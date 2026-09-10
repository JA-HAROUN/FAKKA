package com.oae.fakka.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * The JSON the model is asked to return: {@code {description, totalAmount, paidBy, participants,
 * splitType}} (FR-21).
 * <p>
 * Every field is a boxed type or a list so that a missing one arrives as null and can be reported
 * as an incomplete answer, rather than defaulting to zero and quietly producing a draft for an
 * expense of nothing. {@code totalAmount} is a {@link BigDecimal} because the model is asked for
 * EGP -- "900", or "12.50" -- and the conversion to piastres has to be exact.
 * <p>
 * Unknown fields are ignored: a model that adds a helpful extra key should not fail the parse.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record LlmExpenseJson(
        String description,
        BigDecimal totalAmount,
        String paidBy,
        List<String> participants,
        String splitType
) {
}
