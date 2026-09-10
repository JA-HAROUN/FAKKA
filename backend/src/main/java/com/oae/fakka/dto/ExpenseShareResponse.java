package com.oae.fakka.dto;

import com.oae.fakka.entity.ExpenseParticipant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What one participant owes for one expense.
 * <p>
 * Ids only, no names: the caller just posted these ids and already has the names from the group
 * member list, so loading users here would be a second query for data the client holds.
 */
@Schema(name = "ExpenseShare", description = "One participant portion of an expense")
public record ExpenseShareResponse(

        @Schema(description = "The participant user id", example = "2")
        Long userId,

        @Schema(description = "Their share in piastres: 100 piastres = 1 EGP", example = "11667")
        long shareAmount
) {

    public static ExpenseShareResponse from(ExpenseParticipant participant) {
        return new ExpenseShareResponse(participant.getUserId(), participant.getShareAmount());
    }
}
