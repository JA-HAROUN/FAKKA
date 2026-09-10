package com.oae.fakka.dto;

import com.oae.fakka.entity.Settlement;
import com.oae.fakka.entity.SettlementStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * A stored settlement (FR-34, FR-35).
 * <p>
 * Distinct from {@link SuggestedSettlementResponse}, which is a computed proposal with no id and
 * no status. This one is a record of an agreement: it has an id to mark paid later, and it keeps
 * affecting balances once it is.
 * <p>
 * {@code paidAt} is null exactly while the status is PENDING, so a client can rely on either
 * field alone rather than checking both.
 */
@Schema(name = "Settlement", description = "A tracked payment between two members")
public record SettlementResponse(

        @Schema(description = "Settlement id, for marking it paid", example = "1")
        Long id,

        @Schema(description = "Group the settlement belongs to", example = "1")
        Long groupId,

        @Schema(description = "Who pays", example = "2")
        Long fromUserId,

        @Schema(description = "Who is paid", example = "1")
        Long toUserId,

        @Schema(description = "How much, in piastres: 100 piastres = 1 EGP", example = "20000")
        long amount,

        @Schema(description = "PENDING until the money moves; only PAID affects balances")
        SettlementStatus status,

        @Schema(description = "When the settlement was recorded", example = "2026-09-10T12:34:56.789Z")
        Instant createdAt,

        @Schema(description = "When it was marked paid, null while PENDING",
                example = "2026-09-11T08:00:00.000Z")
        Instant paidAt
) {

    public static SettlementResponse from(Settlement settlement) {
        return new SettlementResponse(
                settlement.getId(),
                settlement.getGroupId(),
                settlement.getFromUserId(),
                settlement.getToUserId(),
                settlement.getAmount(),
                settlement.getStatus(),
                settlement.getCreatedAt(),
                settlement.getPaidAt());
    }
}
