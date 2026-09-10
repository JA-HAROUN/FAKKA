package com.oae.fakka.repository;

/**
 * A per-group total, in piastres. Used by the dashboard, which needs one number per group for
 * one user rather than one number per user in one group.
 */
public interface GroupAmount {

    Long getGroupId();

    long getAmount();
}
