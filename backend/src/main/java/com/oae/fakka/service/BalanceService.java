package com.oae.fakka.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The balance engine (FR-31 to FR-33). <strong>Not implemented yet -- every balance is zero.</strong>
 * <p>
 * This exists now so the dashboard contract can be finished without waiting for it: the shape of
 * the answer, its unit, and the seam callers depend on are all fixed here, and Phase 5 changes
 * one method body rather than the endpoint, the DTO, or the tests around them.
 */
@Slf4j
@Service
public class BalanceService {

    /**
     * What {@code userId} is owed (positive) or owes (negative) in group {@code groupId}, in
     * piastres.
     *
     * <h2>TODO(Phase 5, FR-31): implement this. It currently always returns 0.</h2>
     * Until then every group card reports a zero balance and renders as
     * {@code SETTLED}, which is wrong the moment a single expense exists. The endpoint is
     * deliberately shipped this way; the number is the only part that is missing.
     * <p>
     * The implementation must be, per BR-4:
     * <pre>
     *   balance = (total this user paid for the group)
     *           - (total this user owes as a participant share)
     *           +/- (confirmed settlements involving this user)
     * </pre>
     * with these invariants, which are the ones worth testing when it lands:
     * <ul>
     *   <li>All member balances in a group sum to zero, ignoring rounding (BR-5). That only holds
     *       with exact integer arithmetic, which is why the unit is piastres and the type is
     *       {@code long}, never {@code double}.</li>
     *   <li>Only confirmed expenses and confirmed settlements count (BR-6): an AI-suggested
     *       expense awaiting review must not move anyone balance.</li>
     *   <li>A member with no expenses is 0, not absent.</li>
     * </ul>
     * When it is written, the per-card call below should become one grouped query for the whole
     * dashboard rather than one call per group -- see the note in
     * {@code GroupService#listGroupsForUser}.
     *
     * @return 0 always, until Phase 5
     */
    public long calculateUserBalanceInGroup(Long userId, Long groupId) {
        log.trace("Balance engine not implemented; reporting 0 for user id={} in group id={}",
                userId, groupId);
        return 0L;
    }
}
