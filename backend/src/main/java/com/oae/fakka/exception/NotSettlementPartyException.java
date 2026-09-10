package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

/**
 * Somebody other than the payer or the recipient tried to mark a settlement paid (FR-35).
 * <p>
 * 403 rather than the 400 used elsewhere in this codebase for "the payload names the wrong
 * people": this is genuinely a permission rule, not a malformed request, and saying so keeps the
 * status stable for the day identity stops being self-declared.
 * <p>
 * The message does not name who the two parties are. The caller has just demonstrated they are
 * not one of them, and a group financial position is not theirs to read.
 */
public class NotSettlementPartyException extends ApiException {

    public NotSettlementPartyException() {
        super(HttpStatus.FORBIDDEN,
                "Only the payer or the recipient can mark this settlement paid");
    }
}
