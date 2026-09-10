package com.oae.fakka.service;

import com.oae.fakka.dto.CreateExpenseRequest;
import com.oae.fakka.dto.ExpenseResponse;
import com.oae.fakka.dto.PagedResponse;
import com.oae.fakka.entity.Expense;
import com.oae.fakka.entity.ExpenseParticipant;
import com.oae.fakka.exception.NonGroupMemberException;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.repository.ExpenseParticipantRepository;
import com.oae.fakka.repository.ExpenseRepository;
import com.oae.fakka.repository.GroupMemberRepository;
import com.oae.fakka.repository.GroupRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Recording and reading expenses (FR-12 to FR-19, FR-11).
 * <p>
 * The arithmetic lives in {@link ExpenseSplitService}; this owns the parts that need the
 * database: the group exists, everyone named is a member of it, and the expense plus its shares
 * are written together.
 */
@Slf4j
@Service
public class ExpenseService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseParticipantRepository expenseParticipantRepository;
    private final ExpenseSplitService expenseSplitService;

    public ExpenseService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            ExpenseRepository expenseRepository,
            ExpenseParticipantRepository expenseParticipantRepository,
            ExpenseSplitService expenseSplitService) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.expenseRepository = expenseRepository;
        this.expenseParticipantRepository = expenseParticipantRepository;
        this.expenseSplitService = expenseSplitService;
    }

    /**
     * Validates, splits, and stores an expense with one share row per participant.
     * <p>
     * Transactional because an expense without its shares would be a cost nobody owes: the
     * balance engine would read the payment and none of the debt, so every member balance in the
     * group would be wrong and BR-5 would not hold.
     */
    @Transactional
    public ExpenseResponse createExpense(Long groupId, CreateExpenseRequest request) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group", groupId);
        }

        requireEveryoneIsAMember(groupId, request);

        /*
         * Split before writing anything. The transaction would roll a bad split back anyway, but
         * failing first keeps a rejected request from consuming an id and makes the ordering
         * obvious: nothing is stored until the shares are known to balance.
         */
        SequencedMap<Long, Long> shares = expenseSplitService.split(
                request.splitType(),
                request.participantUserIds(),
                request.totalAmount(),
                request.customShares());

        Expense expense = expenseRepository.saveAndFlush(Expense.builder()
                .groupId(groupId)
                .category(request.category())
                .description(request.description())
                .totalAmount(request.totalAmount())
                .imageUrl(request.imageUrl())
                .paidByUserId(request.paidByUserId())
                .build());

        List<ExpenseParticipant> participants = new ArrayList<>(shares.size());
        for (Map.Entry<Long, Long> share : shares.entrySet()) {
            participants.add(ExpenseParticipant.builder()
                    .expenseId(expense.getId())
                    .userId(share.getKey())
                    .shareAmount(share.getValue())
                    .build());
        }
        expenseParticipantRepository.saveAllAndFlush(participants);

        log.info("Recorded expense id={} of {} piastres in group id={} paid by user id={} across {} participants",
                expense.getId(), expense.getTotalAmount(), groupId,
                expense.getPaidByUserId(), participants.size());

        return ExpenseResponse.of(expense, participants);
    }

    /**
     * One page of a group expenses, newest first, each with its shares (FR-11).
     * <p>
     * Two queries per page, never one per expense: the page of expenses, then every share for
     * those expense ids in a single lookup. A list that fetched shares per row would turn a
     * twenty-expense page into twenty-one queries.
     * <p>
     * The sort is fixed rather than taken from the caller. A dashboard reads newest first, and a
     * client-chosen sort would be one more thing to validate and index for no gain here.
     */
    @Transactional(readOnly = true)
    public PagedResponse<ExpenseResponse> listExpenses(Long groupId, int page, int size) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group", groupId);
        }

        Page<Expense> expenses = expenseRepository.findByGroupIdOrderByCreatedAtDescIdDesc(
                groupId, PageRequest.of(page, size));

        Map<Long, List<ExpenseParticipant>> sharesByExpense = sharesOf(expenses.getContent());

        return PagedResponse.of(expenses, expense -> ExpenseResponse.of(
                expense, sharesByExpense.getOrDefault(expense.getId(), List.of())));
    }

    /**
     * Everything the group has spent (FR-11, FR-36).
     * <p>
     * Over every expense, not over a page: a total that changed when the reader turned the page
     * would be worse than no total at all.
     */
    @Transactional(readOnly = true)
    public long totalExpensesInGroup(Long groupId) {
        return expenseRepository.sumTotalInGroup(groupId);
    }

    /** Shares for a page of expenses, grouped by expense, in one query. */
    private Map<Long, List<ExpenseParticipant>> sharesOf(List<Expense> expenses) {
        if (expenses.isEmpty()) {
            // Nothing to look up, and an empty IN list is not portable SQL.
            return Map.of();
        }

        List<Long> expenseIds = expenses.stream().map(Expense::getId).toList();
        return expenseParticipantRepository.findByExpenseIdInOrderByExpenseIdAscIdAsc(expenseIds)
                .stream()
                .collect(Collectors.groupingBy(ExpenseParticipant::getExpenseId));
    }

    /**
     * The payer and every participant must belong to the group (FR-16, FR-17).
     * <p>
     * Checked together in one query so an expense naming several outsiders reports all of them,
     * and so the payer is not a second round trip. The payer is included even when they are not a
     * participant: paying for a group you do not belong to is not something to record.
     */
    private void requireEveryoneIsAMember(Long groupId, CreateExpenseRequest request) {
        SequencedSet<Long> involved = new LinkedHashSet<>();
        involved.add(request.paidByUserId());
        involved.addAll(request.participantUserIds());

        Set<Long> members = groupMemberRepository.findMemberIdsAmong(groupId, involved);
        List<Long> outsiders = involved.stream()
                .filter(userId -> !members.contains(userId))
                .toList();

        if (!outsiders.isEmpty()) {
            throw new NonGroupMemberException(outsiders);
        }
    }
}
