package com.oae.fakka.repository;

import com.oae.fakka.entity.GroupMember;
import com.oae.fakka.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    /**
     * The users in a group, ordered for display (FR-11).
     * <p>
     * Same semi-join shape as the friend list, and for the same reason: {@code GroupMember}
     * holds plain ids rather than associations. Name then id gives a stable order, so two
     * members sharing a display name never swap places between requests.
     */
    @Query("""
            select u
            from User u
            where u.id in (select m.userId from GroupMember m where m.groupId = :groupId)
            order by u.name asc, u.id asc
            """)
    List<User> findMembersOf(@Param("groupId") Long groupId);

    /**
     * Of {@code candidateIds}, the ones that are members of {@code groupId}.
     * <p>
     * One query for the payer and every participant together, so an expense naming several
     * outsiders reports them all at once instead of one per attempt. Callers must not pass an
     * empty collection -- there is nothing to ask about, and an empty IN list is not portable SQL.
     */
    @Query("""
            select m.userId
            from GroupMember m
            where m.groupId = :groupId
              and m.userId in :candidateIds
            """)
    Set<Long> findMemberIdsAmong(
            @Param("groupId") Long groupId, @Param("candidateIds") Collection<Long> candidateIds);

    /**
     * How many members each of {@code groupIds} has, for the dashboard cards (FR-4).
     * <p>
     * One grouped query for the whole dashboard instead of a count per card: a user with twenty
     * groups would otherwise cost twenty round trips to render one screen. Callers must not pass
     * an empty collection -- there is nothing to count, and an empty IN list is not portable SQL.
     */
    @Query("""
            select m.groupId as groupId, count(m.id) as memberCount
            from GroupMember m
            where m.groupId in :groupIds
            group by m.groupId
            """)
    List<MemberCount> countMembersOf(@Param("groupIds") Collection<Long> groupIds);

    /** Projection for {@link #countMembersOf}; the aliases in that query bind to these getters. */
    interface MemberCount {

        Long getGroupId();

        long getMemberCount();
    }
}
