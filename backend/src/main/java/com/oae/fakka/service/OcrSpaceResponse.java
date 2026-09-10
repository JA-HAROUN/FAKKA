package com.oae.fakka.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Just enough of an OCR.space response to read the extracted text.
 * <p>
 * Unknown fields are ignored: OCR.space adds processing metadata (file size, exit code, etc.)
 * that is of no interest here, and a response shape that grows should not fail to deserialise.
 * <p>
 * Package-private and provider-shaped: this is wire format, not part of the application model,
 * and it disappears if the OCR provider changes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OcrSpaceResponse(
        @JsonProperty("ParsedResults") List<ParsedResult> parsedResults,
        @JsonProperty("IsErroredOnProcessing") boolean erroredOnProcessing,
        @JsonProperty("ErrorMessage") Object errorMessage
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ParsedResult(
            @JsonProperty("ParsedText") String parsedText,
            @JsonProperty("ErrorMessage") String errorMessage,
            @JsonProperty("ErrorDetails") String errorDetails
    ) {
    }
}
