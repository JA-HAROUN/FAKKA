package com.oae.fakka.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
 * A payment from one member to another that clears part of what they owe (FR-34, FR-35).
 *
 * <h2>Why this is stored while a suggestion is not</h2>
 * The suggested list is derived from the current balances and stops being true the moment an
 * expense is added. A settlement is a fact about the world -- somebody handed somebody cash --
 * so it has to survive, and it is what lets a balance come down without deleting an expense.
 *
 * <h2>Only PAID moves a balance</h2>
 * A PENDING row records an intention and is deliberately invisible to the balance engine: until
 * the money moves, the debt is still owed. Marking it PAID makes the engine treat it as an
 * expense in the opposite direction -- the payer is credited, the recipient is debited -- so the
 * two sides cancel and BR-5 still holds.
 * <p>
 * There is no unique constraint on the pair: two members can settle twice, in instalments or on
 * two different nights out, and both rows are real.
 * <p>
 * Ids are plain columns, as everywhere here, so there is no foreign key; the service checks that
 * the group exists and that both parties are members of it.
 */
@Entity
@Table(
        name = "settlements",
        /*
         * Every balance read filters by group, and unlike the membership tables there is no
         * unique constraint here to leave an index behind, so this is the only one.
         */
        indexes = @Index(name = "idx_settlement_group", columnList = "group_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    /** Who pays. Credited when this is marked PAID. */
    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    /** Who is paid. Debited when this is marked PAID. Never equal to {@link #fromUserId}. */
    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    /** How much moves, in piastres. Always positive; the direction is the two ids. */
    @Column(nullable = false)
    private long amount;

    /*
     * Stored as a varchar rather than the native enum type Hibernate would choose on MySQL, for
     * the same reason as the expense category: a portable column instead of a type that differs
     * between MySQL and the H2 used in development. Hibernate still writes a check constraint
     * naming both values, so a third status would need a migration -- of the constraint, not of
     * the column.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SettlementStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * When the money actually moved. Null exactly while {@link #status} is PENDING, which is why
     * the two are set together and never separately.
     */
    @Column(name = "paid_at")
    private Instant paidAt;
}
