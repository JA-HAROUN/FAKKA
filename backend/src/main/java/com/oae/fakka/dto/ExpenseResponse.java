package com.oae.fakka.dto;

import com.oae.fakka.entity.Expense;
import com.oae.fakka.entity.ExpenseCategory;
import com.oae.fakka.entity.ExpenseParticipant;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * A recorded expense and the shares it produced.
 * <p>
 * The shares are returned rather than left for the client to recompute: for an equal split with a
 * remainder, the server decided who absorbs the extra piastres, and a client that guessed would
 * disagree with the stored rows. All amounts are piastres, so 11667 is 116.67 EGP.
 */
@Schema(name = "ExpenseResponse", description = "A recorded expense with its per-participant shares")
public record ExpenseResponse(

        @Schema(description = "Expense id", example = "1")
        Long id,

        @Schema(description = "Group the expense belongs to", example = "1")
        Long groupId,

        @Schema(description = "Category")
        ExpenseCategory category,

        @Schema(description = "What the expense was for", example = "Dinner at Pizza Hut")
        String description,

        @Schema(description = "Total cost in piastres", example = "35000")
        long totalAmount,

        @Schema(description = "Receipt or photo URL, null if unset")
        String imageUrl,

        @Schema(description = "The single member who paid", example = "1")
        Long paidByUserId,

        @Schema(description = "When the expense was recorded", example = "2026-09-10T12:34:56.789Z")
        Instant createdAt,

        @Schema(description = "Shares, in the participant order from the request. Always sums to "
                + "totalAmount (BR-1)")
        List<ExpenseShareResponse> participants
) {

    public static ExpenseResponse of(Expense expense, List<ExpenseParticipant> participants) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getGroupId(),
                expense.getCategory(),
                expense.getDescription(),
                expense.getTotalAmount(),
                expense.getImageUrl(),
                expense.getPaidByUserId(),
                expense.getCreatedAt(),
                participants.stream().map(ExpenseShareResponse::from).toList());
    }
}
