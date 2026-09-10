package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * A settlement that is already PAID cannot be paid again.
 * <p>
 * A conflict rather than a quiet success, even though a repeat request would change nothing: for
 * money, "that was already done" is information the person clicking needs, and a second 200
 * would look like a second payment went through. The original timestamp is included so a client
 * can show when it actually happened.
 */
public class SettlementAlreadyPaidException extends ApiException {

    public SettlementAlreadyPaidException(Long settlementId, Instant paidAt) {
        super(HttpStatus.CONFLICT,
                "Settlement %d was already marked paid at %s".formatted(settlementId, paidAt));
    }
}
