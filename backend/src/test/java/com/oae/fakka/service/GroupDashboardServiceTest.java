package com.oae.fakka.service;

import com.oae.fakka.dto.BalanceStatus;
import com.oae.fakka.dto.ExpenseResponse;
import com.oae.fakka.dto.GroupDashboardResponse;
import com.oae.fakka.dto.GroupResponse;
import com.oae.fakka.dto.MemberBalanceResponse;
import com.oae.fakka.dto.PagedResponse;
import com.oae.fakka.dto.SettlementResponse;
import com.oae.fakka.dto.SuggestedSettlementResponse;
import com.oae.fakka.entity.ExpenseCategory;
import com.oae.fakka.entity.SettlementStatus;
import com.oae.fakka.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link GroupDashboardService} with every service it composes mocked.
 * <p>
 * There is no arithmetic to test here, and that is the point: what these assert is that each
 * part of the response comes straight from the service that owns it, that the paging window is
 * passed through untouched, and that an unknown group is refused before the other four reads
 * happen. If the dashboard ever starts computing something itself, one of these breaks.
 */
@ExtendWith(MockitoExtension.class)
class GroupDashboardServiceTest {

    private static final long GROUP_ID = 10L;
    private static final Instant WHEN = Instant.parse("2026-09-10T12:34:56.789Z");

    private static final GroupResponse GROUP =
            new GroupResponse(GROUP_ID, "Dinner", null, 1L, WHEN, 3);
    private static final MemberBalanceResponse AHMED_BALANCE = new MemberBalanceResponse(
            1L, "Ahmed Ragy", null, 90_000L, 30_000L, 0L, 0L, 60_000L, BalanceStatus.POSITIVE);
    private static final ExpenseResponse EXPENSE = new ExpenseResponse(
            100L, GROUP_ID, ExpenseCategory.FOOD, "Dinner", 90_000L, null, 1L, WHEN, List.of());
    private static final SuggestedSettlementResponse SUGGESTION =
            new SuggestedSettlementResponse(2L, 1L, 30_000L);
    private static final SettlementResponse PENDING = new SettlementResponse(
            200L, GROUP_ID, 3L, 1L, 30_000L, SettlementStatus.PENDING, WHEN, null);

    @Mock
    private GroupService groupService;

    @Mock
    private BalanceService balanceService;

    @Mock
    private ExpenseService expenseService;

    @Mock
    private SettlementService settlementService;

    @InjectMocks
    private GroupDashboardService groupDashboardService;

    @Test
    void everyPartOfTheDashboardComesFromTheServiceThatOwnsIt() {
        PagedResponse<ExpenseResponse> expenses =
                new PagedResponse<>(List.of(EXPENSE), 0, 20, 1, 1);
        given(groupService.getGroup(GROUP_ID)).willReturn(GROUP);
        given(balanceService.listMemberBalances(GROUP_ID)).willReturn(List.of(AHMED_BALANCE));
        given(expenseService.totalExpensesInGroup(GROUP_ID)).willReturn(90_000L);
        given(expenseService.listExpenses(GROUP_ID, 0, 20)).willReturn(expenses);
        given(settlementService.suggestSettlements(GROUP_ID)).willReturn(List.of(SUGGESTION));
        given(settlementService.listPendingSettlements(GROUP_ID)).willReturn(List.of(PENDING));

        GroupDashboardResponse dashboard = groupDashboardService.loadDashboard(GROUP_ID, 0, 20);

        assertThat(dashboard.group()).isSameAs(GROUP);
        assertThat(dashboard.members()).containsExactly(AHMED_BALANCE);
        assertThat(dashboard.totalGroupExpenses()).isEqualTo(90_000L);
        assertThat(dashboard.expenses()).isSameAs(expenses);
        assertThat(dashboard.suggestedSettlements()).containsExactly(SUGGESTION);
        assertThat(dashboard.pendingSettlements()).containsExactly(PENDING);
    }

    /** The window is the caller decision, so it has to arrive at the expense service unchanged. */
    @Test
    void thePagingWindowIsPassedThroughUntouched() {
        givenEverythingIsEmpty();

        groupDashboardService.loadDashboard(GROUP_ID, 3, 50);

        verify(expenseService).listExpenses(GROUP_ID, 3, 50);
    }

    /**
     * The group is read first so that an unknown id is a clean 404 rather than a half-assembled
     * dashboard, or worse, a 200 with an empty group in it.
     */
    @Test
    void anUnknownGroupIsRefusedBeforeAnythingElseIsRead() {
        given(groupService.getGroup(99L)).willThrow(new ResourceNotFoundException("Group", 99L));

        assertThatThrownBy(() -> groupDashboardService.loadDashboard(99L, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Group 99 was not found");

        verifyNoInteractions(balanceService, expenseService, settlementService);
    }

    /** A brand new group is a valid dashboard: empty lists and zeros, not an error. */
    @Test
    void aGroupWithNothingInItStillProducesADashboard() {
        givenEverythingIsEmpty();

        GroupDashboardResponse dashboard = groupDashboardService.loadDashboard(GROUP_ID, 0, 20);

        assertThat(dashboard.totalGroupExpenses()).isZero();
        assertThat(dashboard.expenses().content()).isEmpty();
        assertThat(dashboard.suggestedSettlements()).isEmpty();
        assertThat(dashboard.pendingSettlements()).isEmpty();
    }

    /** Suggested and pending are different questions, so both are asked. */
    @Test
    void bothTheSuggestedAndThePendingSettlementsAreLoaded() {
        givenEverythingIsEmpty();

        groupDashboardService.loadDashboard(GROUP_ID, 0, 20);

        verify(settlementService).suggestSettlements(GROUP_ID);
        verify(settlementService).listPendingSettlements(GROUP_ID);
    }

    private void givenEverythingIsEmpty() {
        given(groupService.getGroup(GROUP_ID)).willReturn(GROUP);
        given(balanceService.listMemberBalances(GROUP_ID)).willReturn(List.of());
        given(expenseService.totalExpensesInGroup(GROUP_ID)).willReturn(0L);
        given(expenseService.listExpenses(eq(GROUP_ID), anyInt(), anyInt()))
                .willReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0));
        given(settlementService.suggestSettlements(GROUP_ID)).willReturn(List.of());
        given(settlementService.listPendingSettlements(GROUP_ID)).willReturn(List.of());
    }
}
