package com.oae.fakka.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A group (the spec also calls it a room) that shared expenses are recorded against.
 * <p>
 * Table is {@code expense_groups}, not {@code groups}: {@code GROUPS} became a reserved word in
 * both MySQL 8 and H2 2.x when window-frame syntax arrived, so the unquoted plural would fail on
 * the very databases this project targets. Same reasoning as {@code users} on {@link User},
 * except that pluralising is not enough to escape it here.
 * <p>
 * {@code createdBy} is a plain user id rather than an association, matching
 * {@link Friendship}, so there is no foreign key: {@code GroupService} verifies the creator
 * exists before inserting. Membership lives in {@link GroupMember}, including the creator, so
 * that "who is in this group" has exactly one answer rather than a row here plus a table there.
 */
@Entity
@Table(name = "expense_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** Optional group picture. Same 2048-char cap as a profile image, for the same URLs. */
    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    /** The user who created the group. Always also a {@link GroupMember} of it (FR-7). */
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
