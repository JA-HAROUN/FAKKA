package com.oae.fakka.repository;

import com.oae.fakka.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {

    /**
     * The groups a user belongs to, newest first, for the personal dashboard (FR-4).
     * <p>
     * Same semi-join shape as the other membership queries, since {@code GroupMember} holds
     * plain ids. Ordered by creation rather than by name because a dashboard is read as a list
     * of what is currently going on; id breaks ties so that two groups created in the same
     * instant keep a stable order between requests.
     */
    @Query("""
            select g
            from Group g
            where g.id in (select m.groupId from GroupMember m where m.userId = :userId)
            order by g.createdAt desc, g.id desc
            """)
    List<Group> findGroupsOf(@Param("userId") Long userId);
}
