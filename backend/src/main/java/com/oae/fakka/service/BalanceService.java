package com.oae.fakka.service;

import com.oae.fakka.dto.MemberBalanceResponse;
import com.oae.fakka.entity.User;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.repository.ExpenseParticipantRepository;
import com.oae.fakka.repository.ExpenseRepository;
import com.oae.fakka.repository.GroupAmount;
import com.oae.fakka.repository.GroupMemberRepository;
import com.oae.fakka.repository.GroupRepository;
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
 *           - everything this member owes as a participant in the group
 * </pre>
 * Positive means the group owes them, negative means they owe the group. Settlements are not
 * subtracted yet because none can be recorded: FR-34 and FR-35 need a stored settlement, and
 * when it exists it belongs in exactly this subtraction and nowhere else.
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

    public BalanceService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            ExpenseRepository expenseRepository,
            ExpenseParticipantRepository expenseParticipantRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.expenseRepository = expenseRepository;
        this.expenseParticipantRepository = expenseParticipantRepository;
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
        return paid - owed;
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
     * Three queries regardless of group size: the members, what each paid, what each owes. The
     * result always sums to zero (BR-5), because every expense adds its total to one payer and
     * subtracts shares that sum to the same total.
     */
    @Transactional(readOnly = true)
    public SequencedMap<Long, Long> calculateGroupBalancesInPiastres(Long groupId) {
        requireGroupExists(groupId);

        List<Long> memberIds = groupMemberRepository.findUserIdsOf(groupId);
        Map<Long, Long> paid = amountsByUser(expenseRepository.sumPaidPerPayerInGroup(groupId));
        Map<Long, Long> owed =
                amountsByUser(expenseParticipantRepository.sumOwedPerParticipantInGroup(groupId));

        SequencedMap<Long, Long> balances = new LinkedHashMap<>();
        for (Long memberId : memberIds) {
            balances.put(memberId, paid.getOrDefault(memberId, 0L) - owed.getOrDefault(memberId, 0L));
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
        Map<Long, Long> paid = amountsByUser(expenseRepository.sumPaidPerPayerInGroup(groupId));
        Map<Long, Long> owed =
                amountsByUser(expenseParticipantRepository.sumOwedPerParticipantInGroup(groupId));

        return members.stream()
                .map(member -> MemberBalanceResponse.of(
                        member,
                        paid.getOrDefault(member.getId(), 0L),
                        owed.getOrDefault(member.getId(), 0L)))
                .toList();
    }

    /**
     * One member balance in each of several groups, in piastres, for the dashboard (FR-4).
     * <p>
     * Two queries for the whole dashboard rather than two per card, which is what the earlier
     * per-group call would have become once this stopped being a stub. Groups where the member
     * has no activity are still present, reading 0.
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

        Map<Long, Long> balances = new LinkedHashMap<>();
        for (Long groupId : groupIds) {
            balances.put(groupId, paid.getOrDefault(groupId, 0L) - owed.getOrDefault(groupId, 0L));
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
