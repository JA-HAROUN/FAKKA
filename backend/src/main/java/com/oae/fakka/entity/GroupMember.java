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
 * One user in one group. The creator gets a row like everyone else (FR-7).
 * <p>
 * Unlike {@link Friendship} this is a single row per relationship, because membership is not
 * symmetric: a group is not a member of a user. The unique constraint is what makes a duplicate
 * member impossible even if two requests race, and its index also serves the member lookup,
 * which always starts from {@code group_id}.
 * <p>
 * Ids are plain columns, as elsewhere, so there is no foreign key; the service checks that the
 * group and every user exist before inserting.
 */
@Entity
@Table(
        name = "group_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_group_member",
                columnNames = {"group_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;
}
