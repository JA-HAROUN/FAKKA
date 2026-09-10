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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link SettlementService} with the repositories mocked.
 * <p>
 * Two rules matter most here and neither is arithmetic: a new settlement is always PENDING, so
 * recording one cannot quietly move a balance; and marking one paid is a one-way, one-time
 * change that only its two parties may make. Both are asserted with the write itself, not just
 * the returned object, because it is the row the balance engine reads.
 */
@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    private static final long GROUP_ID = 10L;
    private static final Instant PAID_AT = Instant.parse("2026-09-11T08:00:00Z");

    @Mock
    private BalanceService balanceService;

    @Mock
    private DebtSimplificationService debtSimplificationService;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @Captor
    private ArgumentCaptor<Settlement> settlementCaptor;

    @Captor
    private ArgumentCaptor<Collection<Long>> partiesCaptor;

    @InjectMocks
    private SettlementService settlementService;

    @Test
    void suggestSettlementsSimplifiesTheCurrentBalances() {
        given(balanceService.calculateGroupBalancesInPiastres(GROUP_ID))
                .willReturn(new java.util.LinkedHashMap<>(Map.of(1L, 20_000L, 2L, -20_000L)));
        given(debtSimplificationService.simplify(any()))
                .willReturn(List.of(new SuggestedSettlementResponse(2L, 1L, 20_000L)));

        assertThat(settlementService.suggestSettlements(GROUP_ID))
                .containsExactly(new SuggestedSettlementResponse(2L, 1L, 20_000L));

        // Nothing is stored: a suggestion is a computation, not a record.
        verifyNoInteractions(settlementRepository);
    }

    @Test
    void createSettlementReports404ForAnUnknownGroup() {
        given(groupRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> settlementService.createSettlement(99L, request(2L, 1L, 20_000L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Group 99 was not found");

        verifyNoInteractions(groupMemberRepository, settlementRepository);
    }

    /** Both parties are one question, so they are one query. */
    @Test
    void createSettlementChecksBothPartiesInOneQuery() {
        givenGroupExists();
        givenBothPartiesAreMembers(1L, 2L);
        givenSettlementIsSavedWithId(100L);

        settlementService.createSettlement(GROUP_ID, request(2L, 1L, 20_000L));

        verify(groupMemberRepository, times(1)).findMemberIdsAmong(eq(GROUP_ID), partiesCaptor.capture());
        assertThat(partiesCaptor.getValue()).containsExactly(2L, 1L);
    }

    @Test
    void createSettlementRejectsAPartyOutsideTheGroupWithoutWriting() {
        givenGroupExists();
        given(groupMemberRepository.findMemberIdsAmong(eq(GROUP_ID), anyCollection()))
                .willReturn(Set.of(1L));

        assertThatThrownBy(() -> settlementService.createSettlement(GROUP_ID, request(9L, 1L, 20_000L)))
                .isInstanceOf(NonGroupMemberException.class)
                .hasMessage("Users [9] are not members of this group");

        verifyNoInteractions(settlementRepository);
    }

    @Test
    void createSettlementReportsBothOutsidersTogether() {
        givenGroupExists();
        given(groupMemberRepository.findMemberIdsAmong(eq(GROUP_ID), anyCollection()))
                .willReturn(Set.of());

        assertThatThrownBy(() -> settlementService.createSettlement(GROUP_ID, request(8L, 9L, 20_000L)))
                .isInstanceOf(NonGroupMemberException.class)
                .hasMessageContaining("[8, 9]");
    }

    /**
     * Agreeing to pay is not paying: the row goes in as PENDING with no timestamp, so the balance
     * engine -- which only counts PAID -- sees nothing yet.
     */
    @Test
    void createSettlementStoresItAsPendingWithNoPaidTimestamp() {
        givenGroupExists();
        givenBothPartiesAreMembers(1L, 2L);
        givenSettlementIsSavedWithId(100L);

        SettlementResponse response =
                settlementService.createSettlement(GROUP_ID, request(2L, 1L, 20_000L));

        verify(settlementRepository).saveAndFlush(settlementCaptor.capture());
        Settlement saved = settlementCaptor.getValue();
        assertThat(saved.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(saved.getFromUserId()).isEqualTo(2L);
        assertThat(saved.getToUserId()).isEqualTo(1L);
        assertThat(saved.getAmount()).isEqualTo(20_000L);
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(saved.getPaidAt()).isNull();

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(SettlementStatus.PENDING);
        assertThat(response.paidAt()).isNull();
    }

    /** An ad hoc amount is not checked against what is owed, so instalments are possible. */
    @Test
    void createSettlementDoesNotConsultTheBalancesAtAll() {
        givenGroupExists();
        givenBothPartiesAreMembers(1L, 2L);
        givenSettlementIsSavedWithId(100L);

        settlementService.createSettlement(GROUP_ID, request(2L, 1L, 999_999L));

        verifyNoInteractions(balanceService, debtSimplificationService);
    }

    @Test
    void markPaidReports404ForAnUnknownSettlement() {
        given(settlementRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.markPaid(99L, new MarkSettlementPaidRequest(1L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Settlement 99 was not found");

        verify(settlementRepository, never()).saveAndFlush(any());
    }

    @Test
    void markPaidSetsTheStatusAndTheTimestampTogether() {
        given(settlementRepository.findById(100L)).willReturn(Optional.of(pending()));
        given(settlementRepository.saveAndFlush(any(Settlement.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Instant before = Instant.now();
        SettlementResponse response =
                settlementService.markPaid(100L, new MarkSettlementPaidRequest(2L));

        verify(settlementRepository).saveAndFlush(settlementCaptor.capture());
        assertThat(settlementCaptor.getValue().getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(settlementCaptor.getValue().getPaidAt()).isBetween(before, Instant.now());
        assertThat(response.status()).isEqualTo(SettlementStatus.PAID);
        assertThat(response.paidAt()).isNotNull();
    }

    /** Either party can confirm the money moved, because either of them would know. */
    @Test
    void bothThePayerAndTheRecipientMayMarkItPaid() {
        // Chained rather than varargs: a fresh PENDING row for each of the two attempts.
        given(settlementRepository.findById(100L))
                .willReturn(Optional.of(pending()))
                .willReturn(Optional.of(pending()));
        given(settlementRepository.saveAndFlush(any(Settlement.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        assertThat(settlementService.markPaid(100L, new MarkSettlementPaidRequest(2L)).status())
                .as("the payer")
                .isEqualTo(SettlementStatus.PAID);
        assertThat(settlementService.markPaid(100L, new MarkSettlementPaidRequest(1L)).status())
                .as("the recipient")
                .isEqualTo(SettlementStatus.PAID);
    }

    @Test
    void markPaidIsRejectedForSomebodyWhoIsNotAPartyToIt() {
        given(settlementRepository.findById(100L)).willReturn(Optional.of(pending()));

        assertThatThrownBy(() -> settlementService.markPaid(100L, new MarkSettlementPaidRequest(9L)))
                .isInstanceOf(NotSettlementPartyException.class)
                .hasMessage("Only the payer or the recipient can mark this settlement paid");

        verify(settlementRepository, never()).saveAndFlush(any());
    }

    /**
     * The party check comes first on purpose: somebody with no business here should not learn
     * whether the settlement has already been paid, so both cases give them the same 403.
     */
    @Test
    void anOutsiderIsRefusedRatherThanToldItWasAlreadyPaid() {
        given(settlementRepository.findById(100L)).willReturn(Optional.of(alreadyPaid()));

        assertThatThrownBy(() -> settlementService.markPaid(100L, new MarkSettlementPaidRequest(9L)))
                .isInstanceOf(NotSettlementPartyException.class);
    }

    /** Paying twice is a conflict, not a second success, and does not restamp the original. */
    @Test
    void markPaidIsRejectedWhenItWasAlreadyPaid() {
        given(settlementRepository.findById(100L)).willReturn(Optional.of(alreadyPaid()));

        assertThatThrownBy(() -> settlementService.markPaid(100L, new MarkSettlementPaidRequest(2L)))
                .isInstanceOf(SettlementAlreadyPaidException.class)
                .hasMessage("Settlement 100 was already marked paid at " + PAID_AT);

        verify(settlementRepository, never()).saveAndFlush(any());
    }

    private void givenGroupExists() {
        given(groupRepository.existsById(GROUP_ID)).willReturn(true);
    }

    private void givenBothPartiesAreMembers(Long... userIds) {
        given(groupMemberRepository.findMemberIdsAmong(eq(GROUP_ID), anyCollection()))
                .willReturn(Set.of(userIds));
    }

    private void givenSettlementIsSavedWithId(long id) {
        given(settlementRepository.saveAndFlush(any(Settlement.class))).willAnswer(invocation -> {
            Settlement settlement = invocation.getArgument(0);
            settlement.setId(id);
            return settlement;
        });
    }

    private static CreateSettlementRequest request(Long fromUserId, Long toUserId, long amount) {
        return new CreateSettlementRequest(fromUserId, toUserId, amount);
    }

    /** User 2 owes user 1, agreed but not yet handed over. */
    private static Settlement pending() {
        return Settlement.builder()
                .id(100L)
                .groupId(GROUP_ID)
                .fromUserId(2L)
                .toUserId(1L)
                .amount(20_000L)
                .status(SettlementStatus.PENDING)
                .build();
    }

    private static Settlement alreadyPaid() {
        Settlement settlement = pending();
        settlement.setStatus(SettlementStatus.PAID);
        settlement.setPaidAt(PAID_AT);
        return settlement;
    }
}
