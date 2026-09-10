package com.oae.fakka.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How the shares for an expense are arrived at (FR-18, FR-19).
 * <p>
 * Request-only: once the shares exist they are the record, and the method that produced them
 * cannot be recovered from them anyway. Kept out of the entity for that reason.
 */
@Schema(name = "SplitType", description = "How to divide an expense between its participants")
public enum SplitType {

    /** Divide the total evenly, giving the rounding remainder to the first participants. */
    EQUAL,

    /** Take a share per participant from the request; they must sum to the total (BR-1). */
    CUSTOM
}
