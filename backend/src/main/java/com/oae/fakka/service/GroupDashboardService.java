package com.oae.fakka.service;

import com.oae.fakka.dto.GroupDashboardResponse;
import com.oae.fakka.dto.GroupResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The group screen, assembled from the services that already answer each part of it (FR-11).
 *
 * <h2>It composes and nothing else</h2>
 * There is no repository here and no arithmetic. Every field of the response is what one of
 * {@link GroupService}, {@link BalanceService}, {@link ExpenseService} or
 * {@link SettlementService} already returns for its own endpoint, so the dashboard cannot drift
 * away from the endpoints it summarises: fix a balance once and both places change.
 * <p>
 * The cost of that is a handful of repeated {@code existsById} checks, because each service
 * validates the group for itself. They are primary-key lookups, and the alternative -- a
 * "trust me, it exists" flag threaded through four services -- would make every one of them
 * harder to reason about alone.
 *
 * <h2>Read-only, and one transaction</h2>
 * Annotated at the method so the whole composition reads one consistent snapshot. Without it,
 * each service would open its own transaction and an expense recorded halfway through could
 * appear in the totals but not in the list.
 */
@Service
public class GroupDashboardService {

    private final GroupService groupService;
    private final BalanceService balanceService;
    private final ExpenseService expenseService;
    private final SettlementService settlementService;

    public GroupDashboardService(
            GroupService groupService,
            BalanceService balanceService,
            ExpenseService expenseService,
            SettlementService settlementService) {
        this.groupService = groupService;
        this.balanceService = balanceService;
        this.expenseService = expenseService;
        this.settlementService = settlementService;
    }

    /**
     * Everything the group screen needs, for one page of its expense list.
     * <p>
     * The group is fetched first so that an unknown id is a clean 404 before any of the other
     * reads run.
     */
    @Transactional(readOnly = true)
    public GroupDashboardResponse loadDashboard(Long groupId, int page, int size) {
        GroupResponse group = groupService.getGroup(groupId);

        return new GroupDashboardResponse(
                group,
                balanceService.listMemberBalances(groupId),
                expenseService.totalExpensesInGroup(groupId),
                expenseService.listExpenses(groupId, page, size),
                settlementService.suggestSettlements(groupId),
                settlementService.listPendingSettlements(groupId));
    }
}
