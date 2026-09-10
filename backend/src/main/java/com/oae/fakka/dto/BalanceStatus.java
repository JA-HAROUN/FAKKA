package com.oae.fakka.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Which way a balance leans, for the coloured indicator on a group card (FR-5).
 * <p>
 * Derived from the balance rather than stored, and derived in exactly one place, so the badge
 * can never disagree with the number printed beside it.
 */
@Schema(name = "BalanceStatus", description = "Sign of a balance: green when owed, red when owing")
public enum BalanceStatus {

    /** The user is owed money. Shown green. */
    POSITIVE,

    /** The user owes money. Shown red. */
    NEGATIVE,

    /** Nothing outstanding either way. Shown neutral. */
    SETTLED;

    /**
     * The rule, in one place: above zero is owed, below zero is owing, zero is settled.
     * <p>
     * Takes minor units, so there is no epsilon comparison to get wrong -- exact zero really is
     * exact. See {@link GroupCardResponse} for why balances travel as piastres.
     */
    public static BalanceStatus of(long balanceInPiastres) {
        if (balanceInPiastres > 0) {
            return POSITIVE;
        }
        if (balanceInPiastres < 0) {
            return NEGATIVE;
        }
        return SETTLED;
    }
}
