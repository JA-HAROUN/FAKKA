package com.oae.fakka.repository;

import com.oae.fakka.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * The paid half of the balance engine (BR-4): an expense credits whoever paid it with its full
 * total, regardless of how the shares were divided.
 * <p>
 * Every sum is coalesced to 0, so "this user paid for nothing" comes back as a number rather
 * than a null that each caller would have to remember to unbox defensively.
 */
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    /** What one member paid into one group. */
    @Query("""
            select coalesce(sum(e.totalAmount), 0)
            from Expense e
            where e.groupId = :groupId
              and e.paidByUserId = :userId
            """)
    long sumPaidByUserInGroup(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /**
     * What every payer paid into one group, one row each.
     * <p>
     * Grouped rather than one query per member: a breakdown for a ten-person group would
     * otherwise be ten scans of the same expenses. Members who never paid are simply absent, and
     * the service treats absent as zero.
     */
    @Query("""
            select e.paidByUserId as userId, coalesce(sum(e.totalAmount), 0) as amount
            from Expense e
            where e.groupId = :groupId
            group by e.paidByUserId
            """)
    List<UserAmount> sumPaidPerPayerInGroup(@Param("groupId") Long groupId);

    /**
     * What one member paid into each of several groups, for the dashboard.
     * <p>
     * One query for the whole dashboard rather than one per card. Callers must not pass an empty
     * collection -- an empty IN list is not portable SQL.
     */
    @Query("""
            select e.groupId as groupId, coalesce(sum(e.totalAmount), 0) as amount
            from Expense e
            where e.paidByUserId = :userId
              and e.groupId in :groupIds
            group by e.groupId
            """)
    List<GroupAmount> sumPaidByUserPerGroup(
            @Param("userId") Long userId, @Param("groupIds") Collection<Long> groupIds);
}
