package com.oae.fakka.repository;

import com.oae.fakka.entity.ExpenseParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * The owed half of the balance engine (BR-4): a participant owes their share of every expense
 * they took part in.
 * <p>
 * Shares carry no group of their own, so every query here reaches the group through the expense.
 * Sums are coalesced to 0 for the same reason as on the paid side.
 */
public interface ExpenseParticipantRepository extends JpaRepository<ExpenseParticipant, Long> {

    /** Shares for one expense, in insertion order, which is the participant order from the request. */
    List<ExpenseParticipant> findByExpenseIdOrderByIdAsc(Long expenseId);

    /** What one member owes across one group. */
    @Query("""
            select coalesce(sum(p.shareAmount), 0)
            from ExpenseParticipant p
            where p.userId = :userId
              and p.expenseId in (select e.id from Expense e where e.groupId = :groupId)
            """)
    long sumOwedByUserInGroup(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** What every participant owes across one group, one row each. */
    @Query("""
            select p.userId as userId, coalesce(sum(p.shareAmount), 0) as amount
            from ExpenseParticipant p
            where p.expenseId in (select e.id from Expense e where e.groupId = :groupId)
            group by p.userId
            """)
    List<UserAmount> sumOwedPerParticipantInGroup(@Param("groupId") Long groupId);

    /**
     * What one member owes in each of several groups, for the dashboard.
     * <p>
     * Joined to the expense rather than filtered by a subquery, because the group id being
     * grouped on lives on the expense. Callers must not pass an empty collection.
     */
    @Query("""
            select e.groupId as groupId, coalesce(sum(p.shareAmount), 0) as amount
            from ExpenseParticipant p
            join Expense e on e.id = p.expenseId
            where p.userId = :userId
              and e.groupId in :groupIds
            group by e.groupId
            """)
    List<GroupAmount> sumOwedByUserPerGroup(
            @Param("userId") Long userId, @Param("groupIds") Collection<Long> groupIds);
}
