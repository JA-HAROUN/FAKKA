package com.oae.fakka.service;

import com.oae.fakka.dto.CreateExpenseRequest;
import com.oae.fakka.dto.ExpenseResponse;
import com.oae.fakka.dto.SplitType;
import com.oae.fakka.entity.Expense;
import com.oae.fakka.entity.ExpenseCategory;
import com.oae.fakka.entity.ExpenseParticipant;
import com.oae.fakka.exception.NonGroupMemberException;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.exception.SplitTotalMismatchException;
import com.oae.fakka.repository.ExpenseParticipantRepository;
import com.oae.fakka.repository.ExpenseRepository;
import com.oae.fakka.repository.GroupMemberRepository;
import com.oae.fakka.repository.GroupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link ExpenseService} with the repositories and the split calculator mocked.
 * <p>
 * The arithmetic is not retested here -- {@link ExpenseSplitServiceTest} covers it exhaustively.
 * What matters at this level is the orchestration: one membership query for the payer and every
 * participant, a split that happens before anything is written, and share rows that carry
 * exactly what the calculator returned.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    private static final long GROUP_ID = 10L;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseParticipantRepository expenseParticipantRepository;

    @Mock
    private ExpenseSplitService expenseSplitService;

    @Captor
    private ArgumentCaptor<Collection<Long>> involvedCaptor;

    @Captor
    private ArgumentCaptor<List<ExpenseParticipant>> participantsCaptor;

    @InjectMocks
    private ExpenseService expenseService;

    @Test
    void createExpenseReports404ForAnUnknownGroup() {
        given(groupRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> expenseService.createExpense(99L, equalSplitRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Group 99 was not found");

        verifyNoInteractions(groupMemberRepository, expenseSplitService,
                expenseRepository, expenseParticipantRepository);
    }

    /** The payer and the participants are one question, so they are one query. */
    @Test
    void createExpenseChecksThePayerAndEveryParticipantInOneQuery() {
        givenGroupExists();
        givenEveryoneIsAMember(1L, 2L, 3L);
        givenSplitReturns(shares(2L, 17_500L, 3L, 17_500L));
        givenExpenseIsSavedWithId(100L);

        expenseService.createExpense(GROUP_ID, request(SplitType.EQUAL, 1L, List.of(2L, 3L), 35_000L, Map.of()));

        verify(groupMemberRepository, times(1)).findMemberIdsAmong(eq(GROUP_ID), involvedCaptor.capture());
        assertThat(involvedCaptor.getValue()).containsExactly(1L, 2L, 3L);
    }

    /** Paying for a group you do not belong to is not something to record. */
    @Test
    void createExpenseRejectsAPayerWhoIsNotAMember() {
        givenGroupExists();
        given(groupMemberRepository.findMemberIdsAmong(eq(GROUP_ID), anyCollection()))
                .willReturn(Set.of(2L, 3L));

        assertThatThrownBy(() -> expenseService.createExpense(
                GROUP_ID, request(SplitType.EQUAL, 1L, List.of(2L, 3L), 35_000L, Map.of())))
                .isInstanceOf(NonGroupMemberException.class)
                .hasMessage("Users [1] are not members of this group");

        verifyNoInteractions(expenseSplitService, expenseRepository, expenseParticipantRepository);
    }

    @Test
    void createExpenseReportsEveryOutsiderTogether() {
        givenGroupExists();
        given(groupMemberRepository.findMemberIdsAmong(eq(GROUP_ID), anyCollection()))
                .willReturn(Set.of(1L));

        assertThatThrownBy(() -> expenseService.createExpense(
                GROUP_ID, request(SplitType.EQUAL, 1L, List.of(2L, 3L), 35_000L, Map.of())))
                .isInstanceOf(NonGroupMemberException.class)
                .hasMessageContaining("[2, 3]");
    }

    /** The payer need not be a participant: someone can pay for a meal they did not eat. */
    @Test
    void createExpenseAllowsAPayerWhoIsNotAParticipant() {
        givenGroupExists();
        givenEveryoneIsAMember(1L, 2L, 3L);
        givenSplitReturns(shares(2L, 17_500L, 3L, 17_500L));
        givenExpenseIsSavedWithId(100L);

        ExpenseResponse response = expenseService.createExpense(
                GROUP_ID, request(SplitType.EQUAL, 1L, List.of(2L, 3L), 35_000L, Map.of()));

        assertThat(response.paidByUserId()).isEqualTo(1L);
        assertThat(response.participants()).extracting(share -> share.userId()).containsExactly(2L, 3L);
    }

    @Test
    void createExpensePassesTheRequestValuesToTheSplitCalculator() {
        givenGroupExists();
        givenEveryoneIsAMember(1L, 2L);
        givenSplitReturns(shares(1L, 20_000L, 2L, 15_000L));
        givenExpenseIsSavedWithId(100L);
        Map<Long, Long> customShares = Map.of(1L, 20_000L, 2L, 15_000L);

        expenseService.createExpense(
                GROUP_ID, request(SplitType.CUSTOM, 1L, List.of(1L, 2L), 35_000L, customShares));

        verify(expenseSplitService).split(SplitType.CUSTOM, List.of(1L, 2L), 35_000L, customShares);
    }

    @Test
    void createExpenseStoresOneShareRowPerParticipantInSplitOrder() {
        givenGroupExists();
        givenEveryoneIsAMember(1L, 2L, 3L);
        givenSplitReturns(shares(1L, 34L, 2L, 33L, 3L, 33L));
        givenExpenseIsSavedWithId(100L);

        expenseService.createExpense(
                GROUP_ID, request(SplitType.EQUAL, 1L, List.of(1L, 2L, 3L), 100L, Map.of()));

        verify(expenseParticipantRepository).saveAllAndFlush(participantsCaptor.capture());
        assertThat(participantsCaptor.getValue())
                .extracting(ExpenseParticipant::getUserId, ExpenseParticipant::getShareAmount)
                .containsExactly(tuple(1L, 34L), tuple(2L, 33L), tuple(3L, 33L));
        assertThat(participantsCaptor.getValue())
                .allSatisfy(participant -> assertThat(participant.getExpenseId()).isEqualTo(100L));
    }

    @Test
    void createExpenseStoresTheExpenseFields() {
        givenGroupExists();
        givenEveryoneIsAMember(1L, 2L);
        givenSplitReturns(shares(2L, 35_000L));
        givenExpenseIsSavedWithId(100L);
        ArgumentCaptor<Expense> expenseCaptor = ArgumentCaptor.forClass(Expense.class);

        expenseService.createExpense(GROUP_ID, new CreateExpenseRequest(
                1L, ExpenseCategory.FOOD, "  Dinner at Pizza Hut  ", 35_000L,
                "https://img.example.com/receipt.jpg", List.of(2L), SplitType.EQUAL, Map.of()));

        verify(expenseRepository).saveAndFlush(expenseCaptor.capture());
        Expense saved = expenseCaptor.getValue();
        assertThat(saved.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(saved.getCategory()).isEqualTo(ExpenseCategory.FOOD);
        assertThat(saved.getDescription()).isEqualTo("Dinner at Pizza Hut");
        assertThat(saved.getTotalAmount()).isEqualTo(35_000L);
        assertThat(saved.getImageUrl()).isEqualTo("https://img.example.com/receipt.jpg");
        assertThat(saved.getPaidByUserId()).isEqualTo(1L);
    }

    /** The share rows need the generated expense id, so the expense has to be saved first. */
    @Test
    void createExpenseSavesTheExpenseBeforeItsShares() {
        givenGroupExists();
        givenEveryoneIsAMember(1L, 2L);
        givenSplitReturns(shares(2L, 35_000L));
        givenExpenseIsSavedWithId(100L);

        expenseService.createExpense(
                GROUP_ID, request(SplitType.EQUAL, 1L, List.of(2L), 35_000L, Map.of()));

        InOrder inOrder = Mockito.inOrder(expenseRepository, expenseParticipantRepository);
        inOrder.verify(expenseRepository).saveAndFlush(any(Expense.class));
        inOrder.verify(expenseParticipantRepository).saveAllAndFlush(any());
    }

    /**
     * A rejected split must leave no trace. The transaction would roll it back anyway, but
     * splitting first means the write never starts.
     */
    @Test
    void createExpenseWritesNothingWhenTheSplitIsRejected() {
        givenGroupExists();
        givenEveryoneIsAMember(1L, 2L);
        given(expenseSplitService.split(any(), any(), anyLong(), any()))
                .willThrow(new SplitTotalMismatchException(34_000L, 35_000L));

        assertThatThrownBy(() -> expenseService.createExpense(
                GROUP_ID, request(SplitType.CUSTOM, 1L, List.of(1L, 2L), 35_000L, Map.of(1L, 34_000L))))
                .isInstanceOf(SplitTotalMismatchException.class);

        verify(expenseRepository, never()).saveAndFlush(any(Expense.class));
        verify(expenseParticipantRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void createExpenseReturnsTheStoredExpenseWithItsShares() {
        givenGroupExists();
        givenEveryoneIsAMember(1L, 2L, 3L);
        givenSplitReturns(shares(1L, 34L, 2L, 33L, 3L, 33L));
        givenExpenseIsSavedWithId(100L);

        ExpenseResponse response = expenseService.createExpense(
                GROUP_ID, request(SplitType.EQUAL, 1L, List.of(1L, 2L, 3L), 100L, Map.of()));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.groupId()).isEqualTo(GROUP_ID);
        assertThat(response.totalAmount()).isEqualTo(100L);
        assertThat(response.participants())
                .extracting(share -> share.userId(), share -> share.shareAmount())
                .containsExactly(tuple(1L, 34L), tuple(2L, 33L), tuple(3L, 33L));
        assertThat(response.participants().stream().mapToLong(share -> share.shareAmount()).sum())
                .as("BR-1 holds in the response too")
                .isEqualTo(response.totalAmount());
    }

    private void givenGroupExists() {
        given(groupRepository.existsById(GROUP_ID)).willReturn(true);
    }

    private void givenEveryoneIsAMember(Long... userIds) {
        given(groupMemberRepository.findMemberIdsAmong(eq(GROUP_ID), anyCollection()))
                .willReturn(Set.of(userIds));
    }

    private void givenSplitReturns(SequencedMap<Long, Long> shares) {
        given(expenseSplitService.split(any(), any(), anyLong(), any())).willReturn(shares);
    }

    private void givenExpenseIsSavedWithId(long id) {
        given(expenseRepository.saveAndFlush(any(Expense.class))).willAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            expense.setId(id);
            return expense;
        });
    }

    private static CreateExpenseRequest equalSplitRequest() {
        return request(SplitType.EQUAL, 1L, List.of(2L), 35_000L, Map.of());
    }

    private static CreateExpenseRequest request(
            SplitType splitType,
            Long paidByUserId,
            List<Long> participantUserIds,
            long totalAmount,
            Map<Long, Long> customShares) {

        return new CreateExpenseRequest(
                paidByUserId, ExpenseCategory.FOOD, "Dinner", totalAmount, null,
                participantUserIds, splitType, customShares);
    }

    private static SequencedMap<Long, Long> shares(Object... userIdsAndShares) {
        SequencedMap<Long, Long> shares = new LinkedHashMap<>();
        for (int index = 0; index < userIdsAndShares.length; index += 2) {
            shares.put((Long) userIdsAndShares[index], (Long) userIdsAndShares[index + 1]);
        }
        return shares;
    }
}
