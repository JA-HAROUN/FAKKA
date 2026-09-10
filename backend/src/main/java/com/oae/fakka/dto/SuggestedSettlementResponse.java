package com.oae.fakka.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One suggested payment that moves a group towards settled (FR-32, FR-33).
 * <p>
 * Ids only, and no id of its own: this is computed from the current balances and stops being
 * true the moment an expense is added, so there is nothing here to hold on to. Posting one to
 * {@code POST /api/groups/{groupId}/settlements} is what turns a suggestion into a
 * {@link SettlementResponse} that can be tracked and marked paid.
 * <p>
 * The amount is piastres, always positive: the direction is carried by the two ids, so there is
 * no such thing as a negative payment here.
 */
@Schema(name = "SuggestedSettlement", description = "A suggested payment from one member to another")
public record SuggestedSettlementResponse(

        @Schema(description = "Who should pay", example = "2")
        Long fromUserId,

        @Schema(description = "Who should be paid", example = "1")
        Long toUserId,

        @Schema(description = "How much to pay, in piastres: 100 piastres = 1 EGP", example = "20000")
        long amount
) {
}
