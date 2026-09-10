package com.oae.fakka.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One shared cost recorded against a group (FR-12).
 *
 * <h2>Money is a long of piastres</h2>
 * {@code totalAmount} is minor units, matching the rule in this package: 35000 is 350.00 EGP.
 * A double would make BR-1 (shares sum to the total) and BR-5 (member balances sum to zero)
 * untestable, because 0.1 + 0.2 is not 0.3 and an unevenly split bill is mostly thirds.
 *
 * <h2>Exactly one payer, by construction</h2>
 * {@code paidByUserId} is a single column, so BR-2 is not a rule that gets checked -- a second
 * payer has nowhere to go. Who owes what lives in {@link ExpenseParticipant}, one row per person.
 * <p>
 * The split method that produced those rows is not stored: the spec entity does not carry it, and
 * the shares are the truth afterwards. An equal split of 100 across 3 people is the same set of
 * rows as a custom 34/33/33, and nothing downstream needs to tell them apart.
 * <p>
 * Ids are plain columns, as everywhere else here, so there is no foreign key; the service checks
 * that the group exists and that payer and participants are all members of it.
 */
@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    /*
     * Stored as a varchar, not the native MySQL enum type Hibernate would pick by default: a
     * portable column that any tool can read, rather than a type whose definition differs
     * between real MySQL and the H2 the dev profile runs in.
     *
     * Note what this does NOT buy. Hibernate still generates a check constraint listing the
     * seven values, and ddl-auto=update rewrites neither a check constraint nor an enum type, so
     * adding a category to FR-13 needs a deliberate migration either way. The difference is that
     * it is a constraint to drop and re-add rather than a column type to alter.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ExpenseCategory category;

    @Column(nullable = false, length = 255)
    private String description;

    /** Total cost in piastres. Always positive: a zero or negative expense has no meaning. */
    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    /** Optional receipt or supporting photo (FR-15). */
    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    /** The single member who paid (BR-2). Not necessarily a participant: paying is not consuming. */
    @Column(name = "paid_by_user_id", nullable = false)
    private Long paidByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
