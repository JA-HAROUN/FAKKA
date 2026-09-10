package com.oae.fakka.service;

import com.oae.fakka.dto.SuggestedSettlementResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The debt simplification algorithm (FR-32, FR-33), tested on hand-written balance maps.
 * <p>
 * No database and no Spring, because the service takes numbers and returns numbers. That makes
 * it cheap to assert the properties that actually matter -- every balance ends at zero, no more
 * than one payment per member minus one, and the same input always gives the same output -- over
 * cases a real group would take a month to reach.
 */
class DebtSimplificationServiceTest {

    private final DebtSimplificationService simplifier = new DebtSimplificationService();

    @Test
    void twoPeopleSettleWithOnePayment() {
        List<SuggestedSettlementResponse> settlements = simplifier.simplify(Map.of(1L, 20_000L, 2L, -20_000L));

        assertThat(settlements)
                .extracting(SuggestedSettlementResponse::fromUserId, SuggestedSettlementResponse::toUserId,
                        SuggestedSettlementResponse::amount)
                .containsExactly(tuple(2L, 1L, 20_000L));
    }

    /** The worked example from the spec: two debtors, one creditor, largest debt first. */
    @Test
    void twoDebtorsPayTheOneCreditor() {
        Map<Long, Long> balances = new LinkedHashMap<>();
        balances.put(1L, 35_000L);   // John is owed 350
        balances.put(2L, -20_000L);  // Ahmed owes 200
        balances.put(3L, -15_000L);  // Mohamed owes 150

        List<SuggestedSettlementResponse> settlements = simplifier.simplify(balances);

        assertThat(settlements)
                .extracting(SuggestedSettlementResponse::fromUserId, SuggestedSettlementResponse::toUserId,
                        SuggestedSettlementResponse::amount)
                .containsExactly(
                        tuple(2L, 1L, 20_000L),
                        tuple(3L, 1L, 15_000L));
    }

    @Test
    void oneDebtorPaysSeveralCreditorsLargestFirst() {
        Map<Long, Long> balances = new LinkedHashMap<>();
        balances.put(1L, 10_000L);
        balances.put(2L, 5_000L);
        balances.put(3L, -15_000L);

        assertThat(simplifier.simplify(balances))
                .extracting(SuggestedSettlementResponse::fromUserId, SuggestedSettlementResponse::toUserId,
                        SuggestedSettlementResponse::amount)
                .containsExactly(
                        tuple(3L, 1L, 10_000L),
                        tuple(3L, 2L, 5_000L));
    }

    /**
     * The case that distinguishes a heap from a single pass over two sorted lists. After the
     * biggest debtor is partly settled, the next step must pick whoever is now the biggest --
     * user 4 with 300 -- and not carry on with user 3, who is down to 100.
     */
    @Test
    void aPartlySettledDebtorStopsBeingTheLargestAndTheAlgorithmMovesOn() {
        Map<Long, Long> balances = new LinkedHashMap<>();
        balances.put(1L, 50_000L);
        balances.put(2L, 40_000L);
        balances.put(3L, -60_000L);
        balances.put(4L, -30_000L);

        assertThat(simplifier.simplify(balances))
                .extracting(SuggestedSettlementResponse::fromUserId, SuggestedSettlementResponse::toUserId,
                        SuggestedSettlementResponse::amount)
                .containsExactly(
                        tuple(3L, 1L, 50_000L),
                        tuple(4L, 2L, 30_000L),
                        tuple(3L, 2L, 10_000L));
    }

    /** A chain of debts collapses: B never has to pay C for money A owes B. */
    @Test
    void aChainOfDebtsCollapsesIntoDirectPayments() {
        // A paid for B, B paid for C, so on net A is owed and C owes.
        Map<Long, Long> balances = new LinkedHashMap<>();
        balances.put(1L, 10_000L);
        balances.put(2L, 0L);
        balances.put(3L, -10_000L);

        assertThat(simplifier.simplify(balances))
                .extracting(SuggestedSettlementResponse::fromUserId, SuggestedSettlementResponse::toUserId,
                        SuggestedSettlementResponse::amount)
                .containsExactly(tuple(3L, 1L, 10_000L));
    }

    @Test
    void aSettledGroupNeedsNoPayments() {
        assertThat(simplifier.simplify(Map.of(1L, 0L, 2L, 0L, 3L, 0L))).isEmpty();
    }

    @Test
    void anEmptyBalanceMapNeedsNoPayments() {
        assertThat(simplifier.simplify(Map.of())).isEmpty();
    }

    /** A member who is square is not dragged into anybody payment. */
    @Test
    void membersWithNothingOutstandingAreLeftOut() {
        Map<Long, Long> balances = new LinkedHashMap<>();
        balances.put(1L, 10_000L);
        balances.put(2L, 0L);
        balances.put(3L, -10_000L);

        assertThat(simplifier.simplify(balances))
                .allSatisfy(settlement -> {
                    assertThat(settlement.fromUserId()).isNotEqualTo(2L);
                    assertThat(settlement.toUserId()).isNotEqualTo(2L);
                });
    }

    /**
     * Same balances, different map iteration order, same answer. A settlement list that
     * reshuffled between two reads of unchanged data would look like the debts had moved.
     */
    @Test
    void theResultDoesNotDependOnTheOrderTheMapWasBuiltIn() {
        Map<Long, Long> forwards = new LinkedHashMap<>();
        forwards.put(1L, 50_000L);
        forwards.put(2L, 40_000L);
        forwards.put(3L, -60_000L);
        forwards.put(4L, -30_000L);

        Map<Long, Long> backwards = new LinkedHashMap<>();
        backwards.put(4L, -30_000L);
        backwards.put(3L, -60_000L);
        backwards.put(2L, 40_000L);
        backwards.put(1L, 50_000L);

        Map<Long, Long> unordered = new HashMap<>(forwards);

        assertThat(simplifier.simplify(backwards)).isEqualTo(simplifier.simplify(forwards));
        assertThat(simplifier.simplify(unordered)).isEqualTo(simplifier.simplify(forwards));
    }

    /** Equal amounts are ordered by user id, so the tie is broken the same way every time. */
    @Test
    void tiesAreBrokenOnTheLowerUserId() {
        Map<Long, Long> balances = new LinkedHashMap<>();
        balances.put(7L, 10_000L);
        balances.put(3L, 10_000L);
        balances.put(9L, -10_000L);
        balances.put(5L, -10_000L);

        assertThat(simplifier.simplify(balances))
                .extracting(SuggestedSettlementResponse::fromUserId, SuggestedSettlementResponse::toUserId)
                .containsExactly(tuple(5L, 3L), tuple(9L, 7L));
    }

    /** Direction lives in the two ids, so an amount is never negative. */
    @Test
    void amountsAreAlwaysPositive() {
        Map<Long, Long> balances = new LinkedHashMap<>();
        balances.put(1L, -25_000L);
        balances.put(2L, 10_000L);
        balances.put(3L, 15_000L);

        assertThat(simplifier.simplify(balances))
                .isNotEmpty()
                .allSatisfy(settlement -> assertThat(settlement.amount()).isPositive());
    }

    /** Odd amounts from an uneven split settle exactly; there is no residual piastre. */
    @Test
    void anUnevenSplitSettlesToTheLastPiastre() {
        // 100 piastres split three ways: the payer covered 100 and consumed 34.
        Map<Long, Long> balances = new LinkedHashMap<>();
        balances.put(1L, 66L);
        balances.put(2L, -33L);
        balances.put(3L, -33L);

        List<SuggestedSettlementResponse> settlements = simplifier.simplify(balances);

        assertThat(settlements).hasSize(2);
        assertThat(totalTransferred(settlements)).isEqualTo(66L);
        assertThat(applyTo(balances, settlements).values()).allSatisfy(
                balance -> assertThat(balance).isZero());
    }

    /**
     * Documented behaviour for a map that does not balance, which BR-5 says cannot come from a
     * real group: settle what can be settled rather than refuse to answer. The unmatched 50
     * simply stays outstanding.
     */
    @Test
    void aBalanceMapThatDoesNotSumToZeroIsSettledAsFarAsItCanBe() {
        List<SuggestedSettlementResponse> settlements = simplifier.simplify(Map.of(1L, 10_000L, 2L, -5_000L));

        assertThat(settlements)
                .extracting(SuggestedSettlementResponse::fromUserId, SuggestedSettlementResponse::toUserId,
                        SuggestedSettlementResponse::amount)
                .containsExactly(tuple(2L, 1L, 5_000L));
    }

    /**
     * The two properties that define a correct simplification, swept over many shapes: everyone
     * ends at zero, and there is at most one payment per member minus one (FR-33).
     */
    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 8, 12, 25})
    void everyBalanceIsClearedInAtMostOnePaymentPerMemberMinusOne(int memberCount) {
        Random random = new Random(memberCount * 31L);

        for (int round = 0; round < 40; round++) {
            Map<Long, Long> balances = balancesSummingToZero(memberCount, random);

            List<SuggestedSettlementResponse> settlements = simplifier.simplify(balances);

            assertThat(applyTo(balances, settlements).values())
                    .as("every balance cleared for %s", balances)
                    .allSatisfy(balance -> assertThat(balance).isZero());
            assertThat(settlements.size())
                    .as("payment count for %s", balances)
                    .isLessThanOrEqualTo(memberCount - 1);
            assertThat(settlements).allSatisfy(
                    settlement -> assertThat(settlement.fromUserId())
                            .isNotEqualTo(settlement.toUserId()));
        }
    }

    /** Nobody pays themselves, and nobody pays more in total than they owed. */
    @Test
    void nobodyPaysMoreThanTheyOwe() {
        Map<Long, Long> balances = new LinkedHashMap<>();
        balances.put(1L, 90_000L);
        balances.put(2L, -30_000L);
        balances.put(3L, -25_000L);
        balances.put(4L, -35_000L);

        List<SuggestedSettlementResponse> settlements = simplifier.simplify(balances);

        Map<Long, Long> paidOut = new LinkedHashMap<>();
        settlements.forEach(settlement ->
                paidOut.merge(settlement.fromUserId(), settlement.amount(), Long::sum));

        assertThat(paidOut).containsExactlyInAnyOrderEntriesOf(
                Map.of(2L, 30_000L, 3L, 25_000L, 4L, 35_000L));
        assertThat(totalTransferred(settlements)).isEqualTo(90_000L);
    }

    /** Deterministic pseudo-random balances that add up to zero, as BR-5 guarantees. */
    private static Map<Long, Long> balancesSummingToZero(int memberCount, Random random) {
        Map<Long, Long> balances = new LinkedHashMap<>();
        long running = 0;
        for (long userId = 1; userId < memberCount; userId++) {
            long balance = random.nextLong(-50_000, 50_001);
            balances.put(userId, balance);
            running += balance;
        }
        balances.put((long) memberCount, -running);
        return balances;
    }

    /** The balances left after the suggested payments are made. */
    private static Map<Long, Long> applyTo(
            Map<Long, Long> balances, List<SuggestedSettlementResponse> settlements) {

        Map<Long, Long> remaining = new LinkedHashMap<>(balances);
        for (SuggestedSettlementResponse settlement : settlements) {
            remaining.merge(settlement.fromUserId(), settlement.amount(), Long::sum);
            remaining.merge(settlement.toUserId(), -settlement.amount(), Long::sum);
        }
        return remaining;
    }

    private static long totalTransferred(List<SuggestedSettlementResponse> settlements) {
        return settlements.stream().mapToLong(SuggestedSettlementResponse::amount).sum();
    }
}
