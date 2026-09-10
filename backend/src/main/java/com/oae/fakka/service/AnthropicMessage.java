package com.oae.fakka.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Just enough of a Messages API response to read the reply text.
 * <p>
 * Unknown fields are ignored on purpose: usage, id, model and anything added later are of no
 * interest here, and a response shape that grows should not start failing to deserialise. The
 * content is a list of blocks -- thinking blocks can precede the text one -- so the reader picks
 * the first text block rather than assuming position zero.
 * <p>
 * Package-private and provider-shaped: this is wire format, not part of the application model,
 * and it disappears with {@link AnthropicLlmClient} if the provider changes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AnthropicMessage(
        List<ContentBlock> content,
        @JsonProperty("stop_reason") String stopReason
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(String type, String text) {
    }
}
