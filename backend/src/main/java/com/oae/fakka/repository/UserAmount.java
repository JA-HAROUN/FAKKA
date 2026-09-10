package com.oae.fakka.repository;

/**
 * A per-user total, in piastres. Shared by the paid and owed halves of the balance engine
 * because both queries answer the same shape of question about the same group.
 */
public interface UserAmount {

    Long getUserId();

    long getAmount();
}
