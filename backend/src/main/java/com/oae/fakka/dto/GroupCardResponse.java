package com.oae.fakka.dto;

import com.oae.fakka.entity.Group;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One card on the personal dashboard: a group the user belongs to, its size, and where the user
 * stands in it (FR-4, FR-5).
 *
 * <h2>userBalance is in piastres, not EGP</h2>
 * Money crosses this API in integer minor units, matching the storage rule in the entity package:
 * 1 EGP = 100 piastres, so {@code 35000} is 350.00 EGP and {@code -12550} is owing 125.50 EGP.
 * A client must divide by 100 for display. The alternative -- a decimal EGP field -- would put
 * every share of an unevenly split bill through a floating point or a scale decision on the wire,
 * and BR-5 (member balances sum to zero) only survives exact arithmetic.
 * <p>
 * {@code status} is computed here from {@code userBalance} rather than accepted as an argument,
 * so no caller can produce a card whose badge contradicts its number.
 */
@Schema(name = "GroupCardResponse", description = "A group as shown on the personal dashboard")
public record GroupCardResponse(

        @Schema(description = "Group id", example = "1")
        Long groupId,

        @Schema(description = "Group name", example = "Dinner")
        String name,

        @Schema(description = "Group image URL, null if unset")
        String imageUrl,

        @Schema(description = "Number of members, including the creator", example = "5")
        int memberCount,

        @Schema(description = "What the user is owed (positive) or owes (negative) in this group, "
                + "in piastres: 100 piastres = 1 EGP. Always 0 until the balance engine lands.",
                example = "35000")
        long userBalance,

        @Schema(description = "Sign of userBalance, for the card indicator")
        BalanceStatus status
) {

    public static GroupCardResponse of(Group group, int memberCount, long userBalance) {
        return new GroupCardResponse(
                group.getId(),
                group.getName(),
                group.getImageUrl(),
                memberCount,
                userBalance,
                BalanceStatus.of(userBalance));
    }
}
