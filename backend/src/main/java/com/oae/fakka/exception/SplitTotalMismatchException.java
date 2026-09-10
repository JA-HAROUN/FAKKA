package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

/**
 * A custom split whose shares do not add up to the expense total (BR-1).
 * <p>
 * Both numbers are in the message because the client needs to know which way it is out to fix
 * the form, and a bean-validation message could not have quoted them. The whole request is
 * rejected rather than adjusted: silently absorbing the difference somewhere would invent a
 * number nobody agreed to, and BR-5 (balances sum to zero) depends on this holding exactly.
 */
public class SplitTotalMismatchException extends ApiException {

    public SplitTotalMismatchException(long shareTotal, long expenseTotal) {
        super(HttpStatus.BAD_REQUEST,
                "Shares total %d piastres but the expense is %d piastres; they must add up exactly"
                        .formatted(shareTotal, expenseTotal));
    }
}
