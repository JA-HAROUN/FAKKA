package com.oae.fakka.service;

import com.oae.fakka.dto.MemberBalanceResponse;
import com.oae.fakka.entity.User;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.repository.ExpenseParticipantRepository;
import com.oae.fakka.repository.ExpenseRepository;
import com.oae.fakka.repository.GroupAmount;
import com.oae.fakka.repository.GroupMemberRepository;
import com.oae.fakka.repository.GroupRepository;
import com.oae.fakka.repository.SettlementRepository;
import com.oae.fakka.repository.UserAmount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.stream.Collectors;

/**
 * The balance engine (FR-31, BR-4, BR-5). Depends on no controller and holds no state.
 *
 * <h2>The formula</h2>
 * <pre>
 *   balance = everything this member paid as payer in the group
 *           + settlements they have paid out        (PAID only)
 *           - everything this member owes as a participant in the group
 *           - settlements they have received        (PAID only)
 * </pre>
 * Positive means the group owes them, negative means they owe the group.
 * <p>
 * A PAID settlement counts exactly like an expense in the opposite direction: handing somebody
 * 200 credits the payer and debits the recipient, which is what lets a debt come down without
 * any expense being edited or deleted. A PENDING one contributes nothing, because until the
 * money moves the debt is still owed.
 * <p>
 * BR-5 survives the addition: every settlement gives one member exactly what it takes from
 * another, so the group still sums to zero.
 *
 * <h2>Units: two types, one number</h2>
 * Every sum happens in {@code long} piastres, because that is what the columns hold and because
 * BR-5 is an exact equality that only survives integer arithmetic. The {@code BigDecimal}
 * methods exist for callers that want EGP and are a pure presentation wrapper -- 35000 piastres
 * becomes {@code 350.00} with scale 2, exactly, with no rounding mode to choose and no way for
 * the two views to disagree.
 * <p>
 * <strong>The {@code BigDecimal} methods return EGP; the {@code InPiastres} methods return
 * piastres.</strong> The API speaks piastres throughout, so everything inside this application
 * uses the latter.
 *
 * <h2>Every member counts, including the idle ones</h2>
 * Balances are built from the group membership, not from the expense rows, so a member who has
 * neither paid nor consumed anything reads 0 rather than being absent. BR-5 does not hold on a
 * map with holes in it.
 */
@Service
public class BalanceService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseParticipantRepository expenseParticipantRepository;
    private final SettlementRepository settlementRepository;

    public BalanceService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            ExpenseRepository expenseRepository,
            ExpenseParticipantRepository expenseParticipantRepository,
            SettlementRepository settlementRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.expenseRepository = expenseRepository;
        this.expenseParticipantRepository = expenseParticipantRepository;
        this.settlementRepository = settlementRepository;
    }

    /**
     * One member balance in one group, in EGP.
     * <p>
     * A thin wrapper over {@link #calculateUserBalanceInPiastres}: the arithmetic is integer, and
     * this only changes how the answer is expressed.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateUserBalance(Long userId, Long groupId) {
        return toEgp(calculateUserBalanceInPiastres(userId, groupId));
    }

    /**
     * One member balance in one group, in piastres. The canonical form: everything internal uses
     * this, and it needs no group-membership check -- a user who is not in the group has paid
     * nothing and owes nothing, which is 0.
     */
    @Transactional(readOnly = true)
    public long calculateUserBalanceInPiastres(Long userId, Long groupId) {
        long paid = expenseRepository.sumPaidByUserInGroup(groupId, userId);
        long owed = expenseParticipantRepository.sumOwedByUserInGroup(groupId, userId);
        long settledOut = settlementRepository.sumPaidOutByUserInGroup(groupId, userId);
        long settledIn = settlementRepository.sumReceivedByUserInGroup(groupId, userId);
        return (paid + settledOut) - (owed + settledIn);
    }

    /** Every member balance in a group, in EGP, keyed by user id. */
    @Transactional(readOnly = true)
    public SequencedMap<Long, BigDecimal> calculateGroupBalances(Long groupId) {
        SequencedMap<Long, BigDecimal> balances = new LinkedHashMap<>();
        calculateGroupBalancesInPiastres(groupId)
                .forEach((userId, piastres) -> balances.put(userId, toEgp(piastres)));
        return balances;
    }

    /**
     * Every member balance in a group, in piastres, keyed by user id.
     * <p>
     * Five queries regardless of group size: the members, then the four terms of the formula.
     * The result always sums to zero (BR-5), because every expense adds its total to one payer
     * and subtracts shares that sum to the same total, and every paid settlement moves one
     * amount between two members.
     */
    @Transactional(readOnly = true)
    public SequencedMap<Long, Long> calculateGroupBalancesInPiastres(Long groupId) {
        requireGroupExists(groupId);

        List<Long> memberIds = groupMemberRepository.findUserIdsOf(groupId);
        GroupTotals totals = totalsOf(groupId);

        SequencedMap<Long, Long> balances = new LinkedHashMap<>();
        for (Long memberId : memberIds) {
            balances.put(memberId, totals.balanceOf(memberId));
        }
        return balances;
    }

    /**
     * What one member paid, owes and nets in a group, one entry per member (FR-11).
     * <p>
     * Ordered by name, like every other member list, and built from the members so nobody is
     * missing. Loads the users because this one is for display; the id-only paths above do not.
     */
    @Transactional(readOnly = true)
    public List<MemberBalanceResponse> listMemberBalances(Long groupId) {
        requireGroupExists(groupId);

        List<User> members = groupMemberRepository.findMembersOf(groupId);
        GroupTotals totals = totalsOf(groupId);

        return members.stream()
                .map(member -> MemberBalanceResponse.of(
                        member,
                        totals.paidFor(member.getId()),
                        totals.owedBy(member.getId()),
                        totals.settledOutBy(member.getId()),
                        totals.settledInBy(member.getId())))
                .toList();
    }

    /**
     * One member balance in each of several groups, in piastres, for the dashboard (FR-4).
     * <p>
     * Four queries for the whole dashboard rather than four per card: one per term of the
     * formula, each grouped by group id. Groups where the member has no activity are still
     * present, reading 0.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> calculateUserBalanceInGroups(Long userId, Collection<Long> groupIds) {
        if (groupIds.isEmpty()) {
            // Nothing to ask about, and an empty IN list is not portable SQL.
            return Map.of();
        }

        Map<Long, Long> paid = amountsByGroup(expenseRepository.sumPaidByUserPerGroup(userId, groupIds));
        Map<Long, Long> owed =
                amountsByGroup(expenseParticipantRepository.sumOwedByUserPerGroup(userId, groupIds));
        Map<Long, Long> settledOut =
                amountsByGroup(settlementRepository.sumPaidOutByUserPerGroup(userId, groupIds));
        Map<Long, Long> settledIn =
                amountsByGroup(settlementRepository.sumReceivedByUserPerGroup(userId, groupIds));

        Map<Long, Long> balances = new LinkedHashMap<>();
        for (Long groupId : groupIds) {
            balances.put(groupId,
                    (paid.getOrDefault(groupId, 0L) + settledOut.getOrDefault(groupId, 0L))
                            - (owed.getOrDefault(groupId, 0L) + settledIn.getOrDefault(groupId, 0L)));
        }
        return balances;
    }

    /**
     * BR-5 against the database: do the balances in this group cancel out?
     * <p>
     * Deliberately not called on any request path. It is a sanity check for tests and for a
     * future diagnostic route: making it a runtime guard would add three queries to every read
     * in order to re-derive something that is true by construction, and would turn a bug in this
     * engine into a failed user request rather than a failed test.
     */
    @Transactional(readOnly = true)
    public boolean groupBalancesSumToZero(Long groupId) {
        return balancesSumToZero(calculateGroupBalancesInPiastres(groupId));
    }

    /**
     * BR-5 against a balance map, with no database involved, so a test can assert it over
     * hand-written numbers as well as over real ones.
     */
    public static boolean balancesSumToZero(Map<Long, Long> balancesInPiastres) {
        return balancesInPiastres.values().stream().mapToLong(Long::longValue).sum() == 0L;
    }

    /**
     * Piastres as an exact EGP amount. {@code valueOf(unscaled, scale)} rather than a division,
     * so there is no rounding step that could turn 350.005 into a decision.
     */
    private static BigDecimal toEgp(long piastres) {
        return BigDecimal.valueOf(piastres, 2);
    }

    /**
     * The four per-member totals for one group, fetched once.
     * <p>
     * Exists so the breakdown and the id-only balance map are assembled from the same numbers by
     * the same code: two callers each adding up the formula themselves is how the two views would
     * eventually come to disagree.
     */
    private GroupTotals totalsOf(Long groupId) {
        return new GroupTotals(
                amountsByUser(expenseRepository.sumPaidPerPayerInGroup(groupId)),
                amountsByUser(expenseParticipantRepository.sumOwedPerParticipantInGroup(groupId)),
                amountsByUser(settlementRepository.sumPaidOutPerUserInGroup(groupId)),
                amountsByUser(settlementRepository.sumReceivedPerUserInGroup(groupId)));
    }

    /** Absent means zero throughout: a member with no activity has totals, not holes. */
    private record GroupTotals(
            Map<Long, Long> paid,
            Map<Long, Long> owed,
            Map<Long, Long> settledOut,
            Map<Long, Long> settledIn) {

        long paidFor(Long userId) {
            return paid.getOrDefault(userId, 0L);
        }

        long owedBy(Long userId) {
            return owed.getOrDefault(userId, 0L);
        }

        long settledOutBy(Long userId) {
            return settledOut.getOrDefault(userId, 0L);
        }

        long settledInBy(Long userId) {
            return settledIn.getOrDefault(userId, 0L);
        }

        long balanceOf(Long userId) {
            return (paidFor(userId) + settledOutBy(userId)) - (owedBy(userId) + settledInBy(userId));
        }
    }

    private void requireGroupExists(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group", groupId);
        }
    }

    private static Map<Long, Long> amountsByUser(List<UserAmount> rows) {
        return rows.stream().collect(Collectors.toMap(UserAmount::getUserId, UserAmount::getAmount));
    }

    private static Map<Long, Long> amountsByGroup(List<GroupAmount> rows) {
        return rows.stream().collect(Collectors.toMap(
                GroupAmount::getGroupId, GroupAmount::getAmount, (first, second) -> first,
                LinkedHashMap::new));
    }
}
