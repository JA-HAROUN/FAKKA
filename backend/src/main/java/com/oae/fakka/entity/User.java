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
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Locale;

/**
 * A registered account.
 * <p>
 * Table is {@code users} because {@code user} is a reserved word in H2 and PostgreSQL.
 * Email uniqueness is enforced by a DB constraint, not just by an application check, so
 * two concurrent signups cannot both succeed.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** Always stored normalised (trimmed + lower-cased) so uniqueness is case-insensitive. */
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    /** BCrypt hash. Excluded from toString so it can never reach a log line. */
    @ToString.Exclude
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(name = "profile_image_url", length = 2048)
    private String profileImageUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * The canonical form of an email for storage and lookup.
     * <p>
     * Emails are case-insensitive in practice, so one canonical form is stored. This lives on
     * the entity, next to the column it protects, because every writer and every lookup has to
     * agree on it: normalise in one place and the unique index stays meaningful, normalise in
     * two and "A@x.com" eventually becomes a second account.
     */
    public static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
