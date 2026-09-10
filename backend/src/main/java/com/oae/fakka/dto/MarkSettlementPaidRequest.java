package com.oae.fakka.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Who is claiming the money moved (FR-35).
 * <p>
 * Only the two parties to a settlement may mark it paid, so the caller has to say which of them
 * they are. In a body rather than the query string: it is the one input to a state change, and
 * query strings end up in access logs and browser history.
 * <p>
 * <strong>This is a self-declared identity, so the restriction is intent, not security.</strong>
 * Nothing stops a caller naming somebody else -- see the note on {@code SettlementController}.
 * It is written as a real check anyway so that the rule is already in place, and already tested,
 * for when sign-in issues a credential the server can verify.
 */
@Schema(name = "MarkSettlementPaidRequest", description = "The party confirming the payment")
public record MarkSettlementPaidRequest(

        @NotNull(message = "must not be null")
        @Positive(message = "must be a positive id")
        @Schema(description = "Id of the payer or the recipient, whichever is confirming",
                example = "2")
        Long userId
) {
}
