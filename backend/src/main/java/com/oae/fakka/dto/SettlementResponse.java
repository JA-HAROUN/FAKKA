package com.oae.fakka.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One suggested payment that moves a group towards settled (FR-32, FR-33).
 * <p>
 * Ids only, and no status: this is a suggestion computed from the current balances, not a stored
 * settlement. Marking one paid (FR-34, FR-35) needs a persisted record, which does not exist
 * yet; until it does, a client that shows these must recompute them after every change rather
 * than hold on to them.
 * <p>
 * The amount is piastres, always positive: the direction is carried by the two ids, so there is
 * no such thing as a negative payment here.
 */
@Schema(name = "Settlement", description = "A suggested payment from one member to another")
public record SettlementResponse(

        @Schema(description = "Who should pay", example = "2")
        Long fromUserId,

        @Schema(description = "Who should be paid", example = "1")
        Long toUserId,

        @Schema(description = "How much to pay, in piastres: 100 piastres = 1 EGP", example = "20000")
        long amount
) {
}
