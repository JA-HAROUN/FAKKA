package com.oae.fakka.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The output of a receipt OCR parse: a list of items for the purchased-items UI (FR-25, FR-26).
 *
 * <h2>Nothing here has been saved</h2>
 * This is the output of a parse, not a record. The expense exists only once the user reviews and
 * confirms the items through the normal expense-save path. A separate save path for OCR-parsed
 * expenses is the mistake this arrangement exists to make obvious (BR-6).
 *
 * <h2>suggestedTotalPiastres may be zero</h2>
 * The OCR may or may not find a printed total. Zero means "not found"; the form should compute
 * the total from the item amounts instead. It is never negative.
 */
@Schema(name = "ParsedReceiptResponse",
        description = "Unsaved items extracted from a receipt image. Populate the "
                + "purchased-items form and let the user review, correct, and confirm before saving.")
public record ParsedReceiptResponse(

        @Schema(description = "Line items extracted from the receipt, in the order they appeared. "
                + "May be empty if the OCR found text but no parseable items — the form falls "
                + "back to manual item entry.")
        List<OcrReceiptItem> items,

        @Schema(description = "The total printed on the receipt, in piastres, or 0 if no total "
                + "was found. The form should prefer computing the total from item amounts.",
                example = "85000")
        long suggestedTotalPiastres

) {
}
