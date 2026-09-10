package com.oae.fakka.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The fixed category list from FR-13.
 * <p>
 * An enum rather than free text so the spending breakdown can group by it without cleaning up
 * "food", "Food" and "fud" first. Stored by name, never by ordinal, so inserting a category here
 * later cannot silently reinterpret existing rows.
 */
@Schema(name = "ExpenseCategory", description = "Predefined expense category")
public enum ExpenseCategory {

    FOOD,
    TRANSPORTATION,
    ENTERTAINMENT,
    SHOPPING,
    ACCOMMODATION,
    UTILITIES,

    /** Deliberately last, and deliberately present: without it, users mis-file rather than skip. */
    OTHER
}
