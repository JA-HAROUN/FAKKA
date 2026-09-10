package com.oae.fakka.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A sentence or two describing an expense, to be turned into a draft (FR-20).
 * <p>
 * The cap is not arbitrary: the text goes to a model that charges by the token and answers within
 * a request somebody is waiting on. A thousand characters is far more than "John paid 900 for
 * dinner, split with Ahmed and Mohamed" needs, and anything longer is a paste rather than a
 * description.
 */
@Schema(name = "ParseExpenseTextRequest", description = "Free text describing an expense")
public record ParseExpenseTextRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 1000, message = "must be at most 1000 characters")
        @Schema(description = "What happened, in the words of whoever was there",
                example = "John paid 900 EGP for dinner. John and Ahmed shared pizza, Mohamed had burger.")
        String text
) {

    /** Stripped here so the model never sees padding, and so a whitespace-only body is rejected. */
    public ParseExpenseTextRequest {
        text = text == null ? null : text.strip();
    }
}
