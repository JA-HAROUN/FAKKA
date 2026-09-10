package com.oae.fakka.service;

import com.oae.fakka.dto.SettlementResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The suggested-settlement view of a group (FR-32, FR-33).
 * <p>
 * Deliberately thin, and that is the point: reading the balances belongs to
 * {@link BalanceService}, deciding who pays whom belongs to {@link DebtSimplificationService},
 * and neither of those should have to know about the other to be tested. This is the seam that
 * joins them.
 */
@Service
public class SettlementService {

    private final BalanceService balanceService;
    private final DebtSimplificationService debtSimplificationService;

    public SettlementService(
            BalanceService balanceService, DebtSimplificationService debtSimplificationService) {
        this.balanceService = balanceService;
        this.debtSimplificationService = debtSimplificationService;
    }

    /**
     * The payments that would settle the group as it stands.
     * <p>
     * Recomputed on every call rather than stored: the answer is only true for the current set of
     * expenses, and a suggestion cached past the next expense would be wrong. The unknown-group
     * 404 comes from the balance lookup, which is the only thing here that touches the database.
     */
    @Transactional(readOnly = true)
    public List<SettlementResponse> suggestSettlements(Long groupId) {
        return debtSimplificationService.simplify(
                balanceService.calculateGroupBalancesInPiastres(groupId));
    }
}
