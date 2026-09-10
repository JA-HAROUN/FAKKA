package com.oae.fakka.repository;

import com.oae.fakka.entity.Friendship;
import com.oae.fakka.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Friendships are stored as a mirrored pair of rows; see {@link Friendship} for why.
 * Every query here therefore reads one direction only and needs no OR across columns.
 */
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    /**
     * Checks one direction, which is enough: the pair is only ever written together, so
     * {@code (a, b)} existing implies {@code (b, a)} does. This is the fast path for a clear
     * 409; the unique index is what actually decides under concurrency.
     */
    boolean existsByUserIdAndFriendId(Long userId, Long friendId);

    /**
     * Of {@code candidateIds}, the ones already friends with {@code userId}.
     * <p>
     * One query for the whole candidate list rather than a check per id: group creation has to
     * validate every proposed member (FR-7), and the caller needs to know all the offending ids
     * to report them in a single response. Callers must not pass an empty collection -- there is
     * nothing to ask about, and an empty IN list is not portable SQL.
     */
    @Query("""
            select f.friendId
            from Friendship f
            where f.userId = :userId
              and f.friendId in :candidateIds
            """)
    Set<Long> findFriendIdsAmong(
            @Param("userId") Long userId, @Param("candidateIds") Collection<Long> candidateIds);

    /**
     * The users on the other side of {@code userId}'s friendships, ordered for display.
     * <p>
     * Written as a semi-join subquery rather than an entity join because {@code Friendship}
     * holds plain ids, not associations. Sorting by name gives the friends tab a stable order;
     * id breaks the tie so that two friends sharing a display name never swap places between
     * requests.
     */
    @Query("""
            select u
            from User u
            where u.id in (select f.friendId from Friendship f where f.userId = :userId)
            order by u.name asc, u.id asc
            """)
    List<User> findFriendsOf(@Param("userId") Long userId);
}
