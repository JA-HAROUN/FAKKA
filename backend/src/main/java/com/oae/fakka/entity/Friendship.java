package com.oae.fakka.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One direction of a friendship: {@code userId} has {@code friendId} in their friend list.
 *
 * <h2>Why two rows per friendship rather than one read both ways</h2>
 * Friendship here is symmetric (FR-9/FR-10 describe no request/accept step), so it could be
 * stored either as a single row queried with {@code userId = ? OR friendId = ?}, or as the
 * mirrored pair this class uses. The pair wins on the two things that actually matter:
 * <ul>
 *   <li><strong>Listing is a plain indexed lookup.</strong> "Who are user 7's friends" is
 *       {@code where userId = 7}. The single-row form needs an OR across two columns plus a
 *       CASE to work out which side is the other person — the query no index fully serves and
 *       the shape every later feature (group member pickers, balance filters) would repeat.</li>
 *   <li><strong>The database enforces uniqueness.</strong> A unique index on
 *       {@code (user_id, friend_id)} makes a duplicate impossible, so two concurrent adds
 *       cannot both succeed. The single-row form can only get that guarantee by also enforcing
 *       a canonical column order (always store {@code min(id), max(id)}), an invariant that
 *       lives in application code and silently breaks the moment one writer forgets it.</li>
 * </ul>
 * The cost is that the two rows must be written together, which is why
 * {@code FriendService#addFriend} is transactional: a half-created friendship — visible to one
 * side only — is the one failure mode this design has to rule out.
 * <p>
 * {@code userId} and {@code friendId} are plain ids rather than {@code @ManyToOne} associations,
 * per the agreed entity shape. There is therefore no foreign key: the service verifies that both
 * users exist before inserting.
 */
@Entity
@Table(
        name = "friendships",
        /*
         * This constraint is load-bearing, not belt-and-braces: it is what makes duplicate
         * rejection correct under concurrency. Its index also starts with user_id, so it serves
         * the friend-list lookup too and no separate index is needed.
         */
        uniqueConstraints = @UniqueConstraint(
                name = "uk_friendship_pair",
                columnNames = {"user_id", "friend_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user whose friend list this row belongs to. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The other user. Never equal to {@link #userId} — self-friending is rejected up front. */
    @Column(name = "friend_id", nullable = false)
    private Long friendId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
