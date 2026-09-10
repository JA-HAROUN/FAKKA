package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

/**
 * The parser answered, but the answer cannot be turned into an expense draft (FR-22, BR-7).
 * <p>
 * 422 rather than the 503 of {@link AiUnavailableException}, because the two mean different
 * things to a client: the model was reachable, so retrying the same text will probably fail the
 * same way, and rephrasing or typing it in by hand is the way forward. Both statuses tell the
 * caller to fall back to manual entry; only this one suggests the text was the problem.
 * <p>
 * Covers a reply that is not JSON, is missing a field, carries an amount that is not a number, or
 * names people who are not in the group.
 */
public class AiResponseNotUsableException extends ApiException {

    public AiResponseNotUsableException(String reason) {
        super(HttpStatus.UNPROCESSABLE_ENTITY,
                reason + "; review the text or enter the expense manually");
    }
}
