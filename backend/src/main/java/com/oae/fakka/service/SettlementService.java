package com.oae.fakka.service;

import com.oae.fakka.dto.CreateSettlementRequest;
import com.oae.fakka.dto.MarkSettlementPaidRequest;
import com.oae.fakka.dto.SettlementResponse;
import com.oae.fakka.dto.SuggestedSettlementResponse;
import com.oae.fakka.entity.Settlement;
import com.oae.fakka.entity.SettlementStatus;
import com.oae.fakka.exception.NonGroupMemberException;
import com.oae.fakka.exception.NotSettlementPartyException;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.exception.SettlementAlreadyPaidException;
import com.oae.fakka.repository.GroupMemberRepository;
import com.oae.fakka.repository.GroupRepository;
import com.oae.fakka.repository.SettlementRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;
import java.util.Set;

/**
 * Settlements: what would clear the group, and what has actually been agreed or paid
 * (FR-32 to FR-35).
 * <p>
 * The suggested list stays a computation over {@link BalanceService} and
 * {@link DebtSimplificationService}; the stored ones live here. The two never mix: a suggestion
 * is not written anywhere, and a stored settlement is never invented from a suggestion without
 * the client asking for it.
 */
@Slf4j
@Service
public class SettlementService {

    private final BalanceService balanceService;
    private final DebtSimplificationService debtSimplificationService;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SettlementRepository settlementRepository;

    public SettlementService(
            BalanceService balanceService,
            DebtSimplificationService debtSimplificationService,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            SettlementRepository settlementRepository) {
        this.balanceService = balanceService;
        this.debtSimplificationService = debtSimplificationService;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.settlementRepository = settlementRepository;
    }

    /**
     * The payments that would settle the group as it stands.
     * <p>
     * Recomputed on every call rather than stored: the answer is only true for the current set of
     * expenses and paid settlements, and one cached past the next change would be wrong. The
     * unknown-group 404 comes from the balance lookup.
     */
    @Transactional(readOnly = true)
    public List<SuggestedSettlementResponse> suggestSettlements(Long groupId) {
        return debtSimplificationService.simplify(
                balanceService.calculateGroupBalancesInPiastres(groupId));
    }

    /**
     * The settlements in a group that have been agreed but not yet paid (FR-34).
     * <p>
     * Kept separate from the suggested list on purpose: these are rows somebody created, and
     * they do not affect any balance until they are marked paid. A client showing both is
     * showing a to-do list beside a proposal, which is exactly the difference.
     */
    @Transactional(readOnly = true)
    public List<SettlementResponse> listPendingSettlements(Long groupId) {
        return settlementRepository
                .findByGroupIdAndStatusOrderByCreatedAtAscIdAsc(groupId, SettlementStatus.PENDING)
                .stream()
                .map(SettlementResponse::from)
                .toList();
    }

    /**
     * Records a settlement as PENDING (FR-34).
     * <p>
     * Nothing about the balances changes here, by design: agreeing to pay is not paying, so the
     * debt stays where it is until {@link #markPaid} says the money moved.
     */
    @Transactional
    public SettlementResponse createSettlement(Long groupId, CreateSettlementRequest request) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group", groupId);
        }

        requireBothPartiesAreMembers(groupId, request);

        Settlement settlement = settlementRepository.saveAndFlush(Settlement.builder()
                .groupId(groupId)
                .fromUserId(request.fromUserId())
                .toUserId(request.toUserId())
                .amount(request.amount())
                .status(SettlementStatus.PENDING)
                .build());

        log.info("Recorded pending settlement id={} of {} piastres in group id={} from user id={} to user id={}",
                settlement.getId(), settlement.getAmount(), groupId,
                settlement.getFromUserId(), settlement.getToUserId());

        return SettlementResponse.from(settlement);
    }

    /**
     * Marks a settlement paid, which is the point at which it starts moving balances (FR-35).
     * <p>
     * Status and {@code paidAt} are set together so the pair can never disagree, and the write
     * happens once: a second attempt is a conflict rather than a fresh timestamp on top of the
     * original.
     */
    @Transactional
    public SettlementResponse markPaid(Long settlementId, MarkSettlementPaidRequest request) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement", settlementId));

        /*
         * Checked before the already-paid conflict on purpose: somebody who is not party to this
         * settlement should not learn whether it has been paid, and the 403 says the same thing
         * either way.
         */
        if (!isParty(settlement, request.userId())) {
            throw new NotSettlementPartyException();
        }

        if (settlement.getStatus() == SettlementStatus.PAID) {
            throw new SettlementAlreadyPaidException(settlementId, settlement.getPaidAt());
        }

        settlement.setStatus(SettlementStatus.PAID);
        settlement.setPaidAt(Instant.now());
        Settlement paid = settlementRepository.saveAndFlush(settlement);

        log.info("Settlement id={} marked paid by user id={}", settlementId, request.userId());
        return SettlementResponse.from(paid);
    }

    private static boolean isParty(Settlement settlement, Long userId) {
        return settlement.getFromUserId().equals(userId) || settlement.getToUserId().equals(userId);
    }

    /**
     * Both parties must belong to the group, checked in one query so a settlement naming two
     * outsiders reports both. An unknown user id lands here as well, for the same reason as on
     * the expense side: a user who does not exist is not a member, and keeping the answers
     * identical stops this endpoint from doubling as a probe for which ids are real.
     */
    private void requireBothPartiesAreMembers(Long groupId, CreateSettlementRequest request) {
        SequencedSet<Long> parties = new LinkedHashSet<>();
        parties.add(request.fromUserId());
        parties.add(request.toUserId());

        Set<Long> members = groupMemberRepository.findMemberIdsAmong(groupId, parties);
        List<Long> outsiders = parties.stream()
                .filter(userId -> !members.contains(userId))
                .toList();

        if (!outsiders.isEmpty()) {
            throw new NonGroupMemberException(outsiders);
        }
    }
}
