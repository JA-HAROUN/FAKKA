package com.oae.fakka.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Where a settlement has got to (FR-34).
 * <p>
 * Two states is the minimum the spec asks for, and deliberately all it is: there is no CANCELLED
 * or DISPUTED here, because nothing in the workflow can produce one yet and a status nobody sets
 * is a status nobody handles.
 */
@Schema(name = "SettlementStatus", description = "Whether a settlement has been paid yet")
public enum SettlementStatus {

    /** Agreed but not yet handed over. Does not move anybody balance. */
    PENDING,

    /** Money has changed hands, so the balance engine nets it out. */
    PAID
}
