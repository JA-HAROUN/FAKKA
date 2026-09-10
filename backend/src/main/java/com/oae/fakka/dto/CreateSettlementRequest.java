package com.oae.fakka.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * A settlement to track (FR-34). The group comes from the path, not the body.
 * <p>
 * Usually copied straight from the suggested list, but an ad hoc payment is just as valid: people
 * settle in instalments, or hand over a round number and leave the rest. So the amount is
 * <strong>not</strong> checked against what is currently owed -- over-paying simply pushes the
 * balance past zero the other way, which the engine handles like any other number.
 * <p>
 * The status is not a field: a new settlement is always PENDING, because a record created and
 * paid in the same breath would skip the only step this entity exists to track.
 */
@Schema(name = "CreateSettlementRequest", description = "A payment to record between two members")
public record CreateSettlementRequest(

        @NotNull(message = "must not be null")
        @Positive(message = "must be a positive id")
        @Schema(description = "Who pays", example = "2")
        Long fromUserId,

        @NotNull(message = "must not be null")
        @Positive(message = "must be a positive id")
        @Schema(description = "Who is paid", example = "1")
        Long toUserId,

        /*
         * The same cap as an expense, deliberately shared rather than re-picked: a settlement
         * lands in the same balance sums, so a bound that kept those clear of overflow would be
         * pointless if money could enter by another door.
         */
        @NotNull(message = "must not be null")
        @Positive(message = "must be greater than zero")
        @Max(value = CreateExpenseRequest.MAX_AMOUNT_IN_PIASTRES,
                message = "must be at most 100000000000 piastres")
        @Schema(description = "How much, in piastres: 100 piastres = 1 EGP", example = "20000")
        Long amount
) {

    /**
     * Paying yourself moves no money and would net to nothing in the balance engine, so it is a
     * mistake rather than a no-op worth storing.
     */
    @AssertTrue(message = "fromUserId and toUserId must be different people")
    @Schema(hidden = true)
    public boolean isBetweenTwoDifferentPeople() {
        return fromUserId == null || !fromUserId.equals(toUserId);
    }
}
