package com.oae.fakka.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One line item extracted from a receipt by the OCR parser (FR-25, FR-26).
 *
 * <h2>Nothing here has been saved</h2>
 * This is one row in the purchased-items interface pre-fill, not a stored record. It exists only
 * until the user reviews it and confirms (or discards) the expense through the normal save path.
 *
 * <h2>Amounts are piastres</h2>
 * Consistent with every other amount in this API: 35000 is 350.00 EGP. The OCR text is
 * assumed to carry EGP amounts; the parser multiplies by 100 and rounds.
 */
@Schema(name = "OcrReceiptItem",
        description = "One line item extracted from a receipt image. Never persisted; "
                + "populate the purchased-items form and let the user review before saving.")
public record OcrReceiptItem(

        @Schema(description = "Item name as read from the receipt", example = "Pizza")
        String name,

        @Schema(description = "Quantity, defaulting to 1 when not printed on the receipt",
                example = "2")
        int quantity,

        @Schema(description = "Unit price in piastres (100 piastres = 1 EGP). "
                + "If the receipt shows a line total, this is that total divided by quantity.",
                example = "35000")
        long unitPricePiastres

) {
}
