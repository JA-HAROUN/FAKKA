package com.oae.fakka.dto;

import com.oae.fakka.entity.ExpenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.List;
import java.util.Map;

/**
 * A new expense for a group (FR-12 to FR-19). The group comes from the path, not the body.
 *
 * <h2>Where the business rules are enforced</h2>
 * <ul>
 *   <li><strong>BR-2, exactly one payer:</strong> {@code paidByUserId} is a single value, so two
 *       payers cannot be expressed. The rule is structural, not checked.</li>
 *   <li><strong>BR-3, at least one participant:</strong> {@code @NotEmpty} on
 *       {@code participantUserIds}, so an expense nobody shares is rejected at the edge before
 *       any service or query runs.</li>
 *   <li><strong>BR-1, shares sum to the total:</strong> not here. It is arithmetic over two
 *       fields and the error has to quote both numbers, which a bean-validation message cannot
 *       do, so {@code ExpenseSplitService} owns it.</li>
 * </ul>
 * This DTO validates shape: presence, sign, distinctness, and that {@code splitType} and
 * {@code customShares} agree. Everything needing the database or the totals is left to the
 * service.
 */
@Schema(name = "CreateExpenseRequest", description = "New expense details")
public record CreateExpenseRequest(

        /*
         * The payer need not be a participant: someone can pay for a meal they did not eat. Both
         * must be members of the group, which the service checks in one query.
         */
        @NotNull(message = "must not be null")
        @Positive(message = "must be a positive id")
        @Schema(description = "The single member who paid (BR-2)", example = "1")
        Long paidByUserId,

        @NotNull(message = "must not be null")
        @Schema(description = "One of the predefined categories (FR-13)", example = "FOOD")
        ExpenseCategory category,

        @NotBlank(message = "must not be blank")
        @Size(max = 255, message = "must be at most 255 characters")
        @Schema(description = "What the expense was for", example = "Dinner at Pizza Hut")
        String description,

        /*
         * The cap is what keeps the arithmetic safe rather than merely plausible: with at most
         * 100 participants and no amount above 10^11, no sum in ExpenseSplitService can approach
         * overflow, so that service can add shares without defending against a wrapped long.
         */
        @NotNull(message = "must not be null")
        @Positive(message = "must be greater than zero")
        @Max(value = MAX_AMOUNT_IN_PIASTRES, message = "must be at most 100000000000 piastres")
        @Schema(description = "Total cost in piastres: 100 piastres = 1 EGP", example = "35000")
        Long totalAmount,

        @Size(max = 2048, message = "must be at most 2048 characters")
        @URL(message = "must be a valid URL")
        @Schema(description = "Optional receipt or photo URL (FR-15)",
                example = "https://res.cloudinary.com/demo/image/upload/receipt.jpg")
        String imageUrl,

        @NotEmpty(message = "must contain at least one participant")
        @Size(max = 100, message = "must not contain more than 100 participants")
        @Schema(description = "Who shares the cost (BR-3). Order matters for an equal split: "
                + "the rounding remainder goes to the first participants.",
                example = "[1, 2, 3]")
        List<@NotNull(message = "must not contain a null id")
             @Positive(message = "must contain positive ids") Long> participantUserIds,

        @Schema(description = "How to divide the total. Defaults to EQUAL when omitted.",
                example = "EQUAL")
        SplitType splitType,

        @Schema(description = "For a CUSTOM split only: participant id to share in piastres. "
                + "Must cover exactly the participants and sum to totalAmount (BR-1).",
                example = "{\"1\": 20000, \"2\": 15000}")
        Map<@NotNull(message = "must not contain a null id")
            @Positive(message = "must use positive ids as keys") Long,
            @NotNull(message = "must not contain a null share")
            @PositiveOrZero(message = "must not contain a negative share")
            @Max(value = MAX_AMOUNT_IN_PIASTRES, message = "must not contain a share above 100000000000")
            Long> customShares
) {

    /** 10^11 piastres, one billion EGP: far above any real bill, far below overflow. */
    public static final long MAX_AMOUNT_IN_PIASTRES = 100_000_000_000L;

    /**
     * Absent and empty mean the same thing for both collections, so only one form reaches the
     * service. {@code splitType} defaults to EQUAL because that is the ordinary case (FR-18); a
     * client that meant CUSTOM and forgot the field still cannot slip through, because sending
     * shares with an EQUAL split is rejected below.
     */
    public CreateExpenseRequest {
        description = description == null ? null : description.strip();
        imageUrl = imageUrl == null || imageUrl.isBlank() ? null : imageUrl.strip();
        participantUserIds = participantUserIds == null ? List.of() : participantUserIds;
        customShares = customShares == null ? Map.of() : customShares;
        splitType = splitType == null ? SplitType.EQUAL : splitType;
    }

    /**
     * A repeated participant would take two shares of the same expense and be counted twice by
     * the balance engine. Counted with distinct() rather than a Set copy so a null id is reported
     * by its own constraint instead of failing here.
     */
    @AssertTrue(message = "participantUserIds must not contain duplicate ids")
    @Schema(hidden = true)
    public boolean isParticipantsDistinct() {
        return participantUserIds.stream().distinct().count() == participantUserIds.size();
    }

    /** Nothing to divide by: a CUSTOM split with no shares has no meaning. */
    @AssertTrue(message = "customShares must be provided when splitType is CUSTOM")
    @Schema(hidden = true)
    public boolean isCustomSharesPresentWhenRequired() {
        return splitType != SplitType.CUSTOM || !customShares.isEmpty();
    }

    /**
     * Rejected rather than ignored: a client sending shares alongside EQUAL believes those
     * numbers will be used, and silently computing different ones is how a bill gets split in a
     * way nobody agreed to.
     */
    @AssertTrue(message = "customShares must not be provided when splitType is EQUAL")
    @Schema(hidden = true)
    public boolean isCustomSharesAbsentWhenUnused() {
        return splitType != SplitType.EQUAL || customShares.isEmpty();
    }
}
