package com.oae.fakka.repository;

import com.oae.fakka.entity.Settlement;
import com.oae.fakka.entity.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Settlements, and the two extra terms they add to a balance.
 * <p>
 * <strong>Every sum here counts PAID rows only.</strong> The status is pinned inside the queries
 * rather than passed in, because a caller that could ask for PENDING totals would eventually use
 * them in a balance, and an intention to pay is not a payment. Each query mirrors one on the
 * expense side, so the engine adds the same two shapes of number in both directions.
 */
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    /**
     * Settlements in one group with one status, oldest first.
     * <p>
     * The status is a parameter here, unlike the sums below, because listing is not arithmetic:
     * a caller asking for PENDING rows is reading a to-do list, not a balance.
     */
    List<Settlement> findByGroupIdAndStatusOrderByCreatedAtAscIdAsc(
            Long groupId, SettlementStatus status);

    /** What one member has handed over in one group. Credited, like paying for an expense. */
    @Query("""
            select coalesce(sum(s.amount), 0)
            from Settlement s
            where s.groupId = :groupId
              and s.fromUserId = :userId
              and s.status = com.oae.fakka.entity.SettlementStatus.PAID
            """)
    long sumPaidOutByUserInGroup(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** What one member has received in one group. Debited, like consuming a share. */
    @Query("""
            select coalesce(sum(s.amount), 0)
            from Settlement s
            where s.groupId = :groupId
              and s.toUserId = :userId
              and s.status = com.oae.fakka.entity.SettlementStatus.PAID
            """)
    long sumReceivedByUserInGroup(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** What every member has handed over in one group, one row each. */
    @Query("""
            select s.fromUserId as userId, coalesce(sum(s.amount), 0) as amount
            from Settlement s
            where s.groupId = :groupId
              and s.status = com.oae.fakka.entity.SettlementStatus.PAID
            group by s.fromUserId
            """)
    List<UserAmount> sumPaidOutPerUserInGroup(@Param("groupId") Long groupId);

    /** What every member has received in one group, one row each. */
    @Query("""
            select s.toUserId as userId, coalesce(sum(s.amount), 0) as amount
            from Settlement s
            where s.groupId = :groupId
              and s.status = com.oae.fakka.entity.SettlementStatus.PAID
            group by s.toUserId
            """)
    List<UserAmount> sumReceivedPerUserInGroup(@Param("groupId") Long groupId);

    /**
     * What one member has handed over in each of several groups, for the dashboard.
     * <p>
     * Callers must not pass an empty collection -- an empty IN list is not portable SQL.
     */
    @Query("""
            select s.groupId as groupId, coalesce(sum(s.amount), 0) as amount
            from Settlement s
            where s.fromUserId = :userId
              and s.groupId in :groupIds
              and s.status = com.oae.fakka.entity.SettlementStatus.PAID
            group by s.groupId
            """)
    List<GroupAmount> sumPaidOutByUserPerGroup(
            @Param("userId") Long userId, @Param("groupIds") Collection<Long> groupIds);

    /** What one member has received in each of several groups, for the dashboard. */
    @Query("""
            select s.groupId as groupId, coalesce(sum(s.amount), 0) as amount
            from Settlement s
            where s.toUserId = :userId
              and s.groupId in :groupIds
              and s.status = com.oae.fakka.entity.SettlementStatus.PAID
            group by s.groupId
            """)
    List<GroupAmount> sumReceivedByUserPerGroup(
            @Param("userId") Long userId, @Param("groupIds") Collection<Long> groupIds);
}
