package com.oae.fakka.service;

import com.oae.fakka.dto.BalanceStatus;
import com.oae.fakka.dto.MemberBalanceResponse;
import com.oae.fakka.entity.User;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.repository.ExpenseParticipantRepository;
import com.oae.fakka.repository.ExpenseRepository;
import com.oae.fakka.repository.GroupAmount;
import com.oae.fakka.repository.GroupMemberRepository;
import com.oae.fakka.repository.GroupRepository;
import com.oae.fakka.repository.UserAmount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link BalanceService} with the repositories mocked.
 * <p>
 * Mocking the two sums is what makes BR-4 testable as a formula rather than as a story about
 * expenses: paid and owed go in, the difference comes out. It also pins the parts that are easy
 * to get wrong without a database noticing -- a member with no activity reading 0 rather than
 * being dropped, and the EGP view carrying the same number as the piastre one.
 */
@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    private static final long GROUP_ID = 10L;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseParticipantRepository expenseParticipantRepository;

    @InjectMocks
    private BalanceService balanceService;

    /** BR-4, straight through: what you put in minus what you took out. */
    @ParameterizedTest
    @CsvSource({
            "90000, 55000, 35000",
            "55000, 90000, -35000",
            "50000, 50000, 0",
            "0, 30000, -30000",
            "30000, 0, 30000",
            "0, 0, 0",
    })
    void userBalanceIsPaidMinusOwed(long paid, long owed, long expectedBalance) {
        given(expenseRepository.sumPaidByUserInGroup(GROUP_ID, 1L)).willReturn(paid);
        given(expenseParticipantRepository.sumOwedByUserInGroup(GROUP_ID, 1L)).willReturn(owed);

        assertThat(balanceService.calculateUserBalanceInPiastres(1L, GROUP_ID))
                .isEqualTo(expectedBalance);
    }

    /**
     * The EGP view is the same integer, expressed differently: exact, scale 2, no rounding step
     * anywhere. A client reading 350.00 and a client reading 35000 are looking at one number.
     */
    @ParameterizedTest
    @CsvSource({
            "35000, 350.00",
            "-12550, -125.50",
            "0, 0.00",
            "1, 0.01",
            "-1, -0.01",
            "100000000000, 1000000000.00",
    })
    void theEgpViewIsTheExactSameAmount(long piastres, String expectedEgp) {
        given(expenseRepository.sumPaidByUserInGroup(GROUP_ID, 1L)).willReturn(piastres);
        given(expenseParticipantRepository.sumOwedByUserInGroup(GROUP_ID, 1L)).willReturn(0L);

        BigDecimal balance = balanceService.calculateUserBalance(1L, GROUP_ID);

        assertThat(balance).isEqualTo(new BigDecimal(expectedEgp));
        assertThat(balance.scale()).isEqualTo(2);
        assertThat(balance.movePointRight(2).longValueExact()).isEqualTo(piastres);
    }

    /**
     * Balances come from the membership, so somebody who has neither paid nor consumed anything
     * is present and zero. BR-5 cannot hold on a map with holes in it.
     */
    @Test
    void groupBalancesIncludeMembersWithNoExpensesAtAll() {
        givenGroupExists();
        given(groupMemberRepository.findUserIdsOf(GROUP_ID)).willReturn(List.of(1L, 2L, 3L));
        given(expenseRepository.sumPaidPerPayerInGroup(GROUP_ID))
                .willReturn(List.of(userAmount(1L, 90_000L)));
        given(expenseParticipantRepository.sumOwedPerParticipantInGroup(GROUP_ID))
                .willReturn(List.of(userAmount(1L, 30_000L), userAmount(2L, 30_000L),
                        userAmount(3L, 30_000L)));

        Map<Long, Long> balances = balanceService.calculateGroupBalancesInPiastres(GROUP_ID);

        assertThat(balances).containsExactlyInAnyOrderEntriesOf(
                Map.of(1L, 60_000L, 2L, -30_000L, 3L, -30_000L));
    }

    @Test
    void groupBalancesAreZeroForAGroupWithNoExpenses() {
        givenGroupExists();
        given(groupMemberRepository.findUserIdsOf(GROUP_ID)).willReturn(List.of(1L, 2L));
        given(expenseRepository.sumPaidPerPayerInGroup(GROUP_ID)).willReturn(List.of());
        given(expenseParticipantRepository.sumOwedPerParticipantInGroup(GROUP_ID)).willReturn(List.of());

        assertThat(balanceService.calculateGroupBalancesInPiastres(GROUP_ID))
                .containsExactlyInAnyOrderEntriesOf(Map.of(1L, 0L, 2L, 0L));
    }

    @Test
    void groupBalancesReport404ForAnUnknownGroup() {
        given(groupRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> balanceService.calculateGroupBalancesInPiastres(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Group 99 was not found");

        verifyNoInteractions(expenseRepository, expenseParticipantRepository);
    }

    @Test
    void groupBalancesInEgpCarryTheSameAmounts() {
        givenGroupExists();
        given(groupMemberRepository.findUserIdsOf(GROUP_ID)).willReturn(List.of(1L, 2L));
        given(expenseRepository.sumPaidPerPayerInGroup(GROUP_ID))
                .willReturn(List.of(userAmount(1L, 35_000L)));
        given(expenseParticipantRepository.sumOwedPerParticipantInGroup(GROUP_ID))
                .willReturn(List.of(userAmount(1L, 17_500L), userAmount(2L, 17_500L)));

        assertThat(balanceService.calculateGroupBalances(GROUP_ID))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        1L, new BigDecimal("175.00"),
                        2L, new BigDecimal("-175.00")));
    }

    /** BR-5 over a hand-written map, with no database in the way. */
    @Test
    void balancesSumToZeroChecksBR5Directly() {
        assertThat(BalanceService.balancesSumToZero(Map.of(1L, 60_000L, 2L, -30_000L, 3L, -30_000L)))
                .isTrue();
        assertThat(BalanceService.balancesSumToZero(Map.of())).isTrue();
        assertThat(BalanceService.balancesSumToZero(Map.of(1L, 0L, 2L, 0L))).isTrue();
        assertThat(BalanceService.balancesSumToZero(Map.of(1L, 60_000L, 2L, -30_000L)))
                .as("a group that does not balance")
                .isFalse();
        assertThat(BalanceService.balancesSumToZero(Map.of(1L, 1L, 2L, 0L)))
                .as("one piastre out is still out")
                .isFalse();
    }

    @Test
    void groupBalancesSumToZeroChecksBR5AgainstTheDatabase() {
        givenGroupExists();
        given(groupMemberRepository.findUserIdsOf(GROUP_ID)).willReturn(List.of(1L, 2L, 3L));
        given(expenseRepository.sumPaidPerPayerInGroup(GROUP_ID))
                .willReturn(List.of(userAmount(1L, 100L)));
        given(expenseParticipantRepository.sumOwedPerParticipantInGroup(GROUP_ID))
                .willReturn(List.of(userAmount(1L, 34L), userAmount(2L, 33L), userAmount(3L, 33L)));

        assertThat(balanceService.groupBalancesSumToZero(GROUP_ID)).isTrue();
    }

    @Test
    void memberBalancesBreakDownPaidOwedAndNet() {
        givenGroupExists();
        given(groupMemberRepository.findMembersOf(GROUP_ID)).willReturn(List.of(
                user(1L, "Ahmed Ragy", null),
                user(2L, "Mohamed Salah", "https://img.example.com/m.jpg"),
                user(3L, "Zeinab Hassan", null)));
        given(expenseRepository.sumPaidPerPayerInGroup(GROUP_ID))
                .willReturn(List.of(userAmount(1L, 90_000L)));
        given(expenseParticipantRepository.sumOwedPerParticipantInGroup(GROUP_ID))
                .willReturn(List.of(userAmount(1L, 30_000L), userAmount(2L, 30_000L),
                        userAmount(3L, 30_000L)));

        List<MemberBalanceResponse> balances = balanceService.listMemberBalances(GROUP_ID);

        assertThat(balances)
                .extracting(MemberBalanceResponse::userId, MemberBalanceResponse::name,
                        MemberBalanceResponse::paid, MemberBalanceResponse::owed,
                        MemberBalanceResponse::net, MemberBalanceResponse::status)
                .containsExactly(
                        tuple(1L, "Ahmed Ragy", 90_000L, 30_000L, 60_000L, BalanceStatus.POSITIVE),
                        tuple(2L, "Mohamed Salah", 0L, 30_000L, -30_000L, BalanceStatus.NEGATIVE),
                        tuple(3L, "Zeinab Hassan", 0L, 30_000L, -30_000L, BalanceStatus.NEGATIVE));
        assertThat(balances.get(1).profileImageUrl()).isEqualTo("https://img.example.com/m.jpg");
        assertThat(balances.stream().mapToLong(MemberBalanceResponse::net).sum())
                .as("BR-5 across the breakdown")
                .isZero();
    }

    @Test
    void memberBalancesReport404ForAnUnknownGroup() {
        given(groupRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> balanceService.listMemberBalances(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(expenseRepository, expenseParticipantRepository);
    }

    /** The dashboard asks once for every group, and gets an entry for each, zeros included. */
    @Test
    void balancesAcrossGroupsCoverEveryRequestedGroup() {
        given(expenseRepository.sumPaidByUserPerGroup(anyLong(), anyCollection()))
                .willReturn(List.of(groupAmount(10L, 90_000L), groupAmount(11L, 10_000L)));
        given(expenseParticipantRepository.sumOwedByUserPerGroup(anyLong(), anyCollection()))
                .willReturn(List.of(groupAmount(10L, 30_000L), groupAmount(12L, 5_000L)));

        Map<Long, Long> balances =
                balanceService.calculateUserBalanceInGroups(1L, List.of(10L, 11L, 12L, 13L));

        assertThat(balances).containsExactlyInAnyOrderEntriesOf(Map.of(
                10L, 60_000L,
                11L, 10_000L,
                12L, -5_000L,
                13L, 0L));
    }

    /** No groups means nothing to ask, and an empty IN list is not portable SQL. */
    @Test
    void balancesAcrossGroupsSkipsTheQueriesWhenThereAreNoGroups() {
        assertThat(balanceService.calculateUserBalanceInGroups(1L, List.of())).isEmpty();

        verifyNoInteractions(expenseRepository, expenseParticipantRepository);
    }

    private void givenGroupExists() {
        given(groupRepository.existsById(GROUP_ID)).willReturn(true);
    }

    private static User user(Long id, String name, String profileImageUrl) {
        return User.builder()
                .id(id)
                .name(name)
                .email("user%d@example.com".formatted(id))
                .passwordHash("$2a$10$" + "x".repeat(53))
                .profileImageUrl(profileImageUrl)
                .build();
    }

    /** Stands in for the repository projections, which are interfaces with no value type. */
    private static UserAmount userAmount(Long userId, long amount) {
        return new UserAmount() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public long getAmount() {
                return amount;
            }
        };
    }

    private static GroupAmount groupAmount(Long groupId, long amount) {
        return new GroupAmount() {
            @Override
            public Long getGroupId() {
                return groupId;
            }

            @Override
            public long getAmount() {
                return amount;
            }
        };
    }
}
