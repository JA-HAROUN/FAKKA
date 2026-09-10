package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

/**
 * The natural-language parser could not be reached, or is not configured (BR-7, FR-23).
 * <p>
 * 503 rather than 500: nothing is broken about the request, a dependency is simply not answering,
 * and the caller has somewhere useful to go. The message says so explicitly, because the client
 * is expected to switch to the manual form on this status rather than show an error and stop.
 * <p>
 * Everything unexpected inside the parse path funnels here too. An unhandled 500 would tell a
 * client nothing about what to do next, and BR-7 requires that an AI failure never leave a user
 * unable to record their expense.
 */
public class AiUnavailableException extends ApiException {

    private static final String FALL_BACK = "; enter the expense manually instead";

    public AiUnavailableException(String reason) {
        super(HttpStatus.SERVICE_UNAVAILABLE, reason + FALL_BACK);
    }

    public AiUnavailableException(String reason, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, reason + FALL_BACK, cause);
    }
}
