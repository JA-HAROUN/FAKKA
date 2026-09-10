package com.oae.fakka.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Everything the group screen shows, in one response (FR-11).
 * <p>
 * Composed rather than computed: every field here is what one of the existing services already
 * returns for its own endpoint, so a client can render the whole screen from one request without
 * this view inventing a second version of any number.
 * <p>
 * Amounts are piastres throughout, as everywhere else in the API: 35000 is 350.00 EGP.
 *
 * <h2>Why the expense list is paginated and nothing else is</h2>
 * Members, balances and settlements are bounded by the size of the group -- a hundred people at
 * the very most. Expenses are not: a group used for a year has thousands, and a dashboard that
 * embedded all of them would grow without limit. So the list is a page, and the totals beside it
 * are computed over every expense rather than over the page.
 */
@Schema(name = "GroupDashboard", description = "The whole group screen in one response")
public record GroupDashboardResponse(

        @Schema(description = "Name, image, creator, creation date and member count")
        GroupResponse group,

        @Schema(description = "Every member with what they paid, owe, settled and net, ordered by name")
        List<MemberBalanceResponse> members,

        @Schema(description = "Everything the group has spent, in piastres, across every expense "
                + "and not just this page", example = "180000")
        long totalGroupExpenses,

        @Schema(description = "One page of expenses, newest first, each with its shares")
        PagedResponse<ExpenseResponse> expenses,

        @Schema(description = "Computed payments that would settle the group as it stands (FR-33)")
        List<SuggestedSettlementResponse> suggestedSettlements,

        @Schema(description = "Settlements already recorded but not yet paid (FR-34). These do "
                + "not affect the balances above until they are marked paid.")
        List<SettlementResponse> pendingSettlements
) {
}
