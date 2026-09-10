package com.oae.fakka.repository;

import com.oae.fakka.entity.ExpenseCategory;

import java.time.Instant;

/**
 * One expense joined to one of its shares: exactly one line of the CSV export (FR-36).
 * <p>
 * A projection rather than the two entities, because the export needs a flat row and nothing
 * else -- no lazy associations to resolve while the response is already streaming, and no share
 * rows held in memory beyond the chunk being written.
 * <p>
 * User ids, not names: the report resolves those from the group membership it loads once, which
 * is cheaper than joining {@code User} twice on every row.
 */
public interface ExpenseShareRow {

    Instant getCreatedAt();

    ExpenseCategory getCategory();

    String getDescription();

    Long getPayerUserId();

    Long getParticipantUserId();

    long getShareAmount();

    long getExpenseTotal();
}
