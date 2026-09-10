package com.oae.fakka.repository;

import com.oae.fakka.entity.GroupMember;
import com.oae.fakka.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
}
