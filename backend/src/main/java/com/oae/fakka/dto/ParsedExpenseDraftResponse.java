package com.oae.fakka.dto;

import com.oae.fakka.entity.ExpenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A proposed expense for a person to check before it becomes one (FR-21, FR-22).
 *
 * <h2>Nothing here has been saved</h2>
 * This is the output of a parse, not a record. The expense exists only once the reviewed values
 * are sent to {@code POST /api/groups/{groupId}/expenses}, which is the same endpoint the manual
 * form uses -- BR-6 requires that an AI suggestion go through the ordinary flow rather than a
 * private save path of its own.
 *
 * <h2>Shaped to fill the form, not to be trusted</h2>
 * The fields line up with {@code CreateExpenseRequest} so a client can populate the form and let
 * the user correct it. Two things are deliberately missing, and both are questions for the person
 * reviewing:
 * <ul>
 *   <li><strong>Category.</strong> The model is not asked for one, so the form keeps its own
 *       picker. {@code suggestedCategory} is always null today; the field exists so that adding a
 *       suggestion later does not change the response shape.</li>
 *   <li><strong>Custom shares.</strong> A {@code splitType} of CUSTOM means the text described an
 *       uneven split -- somebody had the burger, somebody shared the pizza -- but the parse does
 *       not produce per-person amounts. The form has to collect them, which is exactly the review
 *       step FR-22 asks for.</li>
 * </ul>
 * Amounts are piastres, as everywhere else in this API: 90000 is 900.00 EGP.
 */
@Schema(name = "ParsedExpenseDraft",
        description = "An unsaved expense proposal for review. Confirming it means POSTing to the "
                + "normal expense endpoint.")
public record ParsedExpenseDraftResponse(

        @Schema(description = "What the expense was for, as the model read it",
                example = "Dinner")
        String description,

        @Schema(description = "Total in piastres, converted from the amount in the text: "
                + "100 piastres = 1 EGP", example = "90000")
        long totalAmount,

        @Schema(description = "Always null for now. Reserved so that suggesting a category later "
                + "does not break this shape; the review form supplies one.")
        ExpenseCategory suggestedCategory,

        @Schema(description = "The member the text says paid, resolved to a group member. Never "
                + "null: a draft without a payer is not usable, and the parse fails instead.")
        UserSummaryResponse paidBy,

        @Schema(description = "The members sharing the cost, resolved to group members. Never "
                + "empty, per BR-3.")
        List<UserSummaryResponse> participants,

        @Schema(description = "EQUAL, or CUSTOM when the text implies uneven shares. CUSTOM means "
                + "the form must collect the per-person amounts before saving.")
        SplitType splitType,

        @Schema(description = "Names the model produced that match nobody in this group, or match "
                + "several members equally. Nothing is guessed: the form should ask who was meant.",
                example = "[\"Youssef\"]")
        List<String> unresolvedNames
) {
}
