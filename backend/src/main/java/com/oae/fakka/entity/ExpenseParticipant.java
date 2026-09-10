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

/**
 * One person share of one expense (FR-17 to FR-19).
 * <p>
 * The rows for an expense always sum to its {@code totalAmount} (BR-1); that is enforced when
 * they are created, and it is what makes the balance engine a sum rather than a reconstruction of
 * how the split was chosen.
 * <p>
 * A share can be zero -- an equal split of 3 piastres across 5 people gives two of them nothing --
 * but it is never negative. The unique constraint keeps one person from appearing twice in the
 * same expense, which would double-count them.
 */
@Entity
@Table(
        name = "expense_participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_expense_participant",
                columnNames = {"expense_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ExpenseParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_id", nullable = false)
    private Long expenseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** This person portion of the expense, in piastres. */
    @Column(name = "share_amount", nullable = false)
    private long shareAmount;
}
