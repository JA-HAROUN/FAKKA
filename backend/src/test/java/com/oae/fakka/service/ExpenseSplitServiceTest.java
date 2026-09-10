package com.oae.fakka.service;

import com.oae.fakka.dto.SplitType;
import com.oae.fakka.exception.SplitParticipantMismatchException;
import com.oae.fakka.exception.SplitTotalMismatchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The split arithmetic (FR-18, FR-19), tested directly.
 * <p>
 * No mocks and no Spring: the service is a function of its arguments, which is the whole reason
 * it is separate from the controller. That makes it cheap to cover the cases rounding actually
 * breaks on -- a total that does not divide, a total smaller than the group, one participant --
 * and to assert the invariant that matters (shares sum to the total, exactly) across a wide
 * sweep of inputs rather than a couple of examples.
 */
class ExpenseSplitServiceTest {

    private final ExpenseSplitService splitService = new ExpenseSplitService();

    @Test
    void equalSplitDividesEvenlyWhenItDivides() {
        SequencedMap<Long, Long> shares = splitService.splitEqually(List.of(1L, 2L, 3L), 30_000L);

        assertThat(shares).containsExactly(
                Map.entry(1L, 10_000L), Map.entry(2L, 10_000L), Map.entry(3L, 10_000L));
    }

    /**
     * 100 across three is the canonical case: 33.33 each loses a piastre, 34 each invents one.
     * The remainder has to go somewhere, and it goes to the front of the list.
     */
    @Test
    void equalSplitGivesTheRemainderToTheFirstParticipants() {
        SequencedMap<Long, Long> shares = splitService.splitEqually(List.of(1L, 2L, 3L), 100L);

        assertThat(shares).containsExactly(
                Map.entry(1L, 34L), Map.entry(2L, 33L), Map.entry(3L, 33L));
        assertThat(sumOf(shares)).isEqualTo(100L);
    }

    /** A remainder of two piastres goes to the first two participants, one each -- not both to one. */
    @Test
    void equalSplitSpreadsAMultiPiastreRemainderOnePiastreEach() {
        SequencedMap<Long, Long> shares = splitService.splitEqually(List.of(1L, 2L, 3L, 4L), 10L);

        assertThat(shares).containsExactly(
                Map.entry(1L, 3L), Map.entry(2L, 3L), Map.entry(3L, 2L), Map.entry(4L, 2L));
        assertThat(shares.values()).allSatisfy(share -> assertThat(share).isBetween(2L, 3L));
    }

    /** Order is the request order, not sorted by id: the client decides who absorbs the extra. */
    @Test
    void equalSplitFollowsTheGivenParticipantOrder() {
        SequencedMap<Long, Long> shares = splitService.splitEqually(List.of(9L, 3L, 7L), 100L);

        assertThat(shares.keySet()).containsExactly(9L, 3L, 7L);
        assertThat(shares.get(9L)).isEqualTo(34L);
        assertThat(shares.get(3L)).isEqualTo(33L);
        assertThat(shares.get(7L)).isEqualTo(33L);
    }

    /** Fewer piastres than people: some shares are legitimately zero, and nothing is invented. */
    @Test
    void equalSplitOfLessThanOnePiastreEachLeavesSomeSharesAtZero() {
        SequencedMap<Long, Long> shares = splitService.splitEqually(List.of(1L, 2L, 3L, 4L, 5L), 3L);

        assertThat(shares.values()).containsExactly(1L, 1L, 1L, 0L, 0L);
        assertThat(sumOf(shares)).isEqualTo(3L);
    }

    @Test
    void equalSplitWithOneParticipantGivesThemEverything() {
        assertThat(splitService.splitEqually(List.of(7L), 35_000L))
                .containsExactly(Map.entry(7L, 35_000L));
    }

    @Test
    void equalSplitOfASinglePiastreGivesItToTheFirstParticipant() {
        assertThat(splitService.splitEqually(List.of(1L, 2L), 1L))
                .containsExactly(Map.entry(1L, 1L), Map.entry(2L, 0L));
    }

    /**
     * The invariant, swept rather than sampled: whatever the total and however many people share
     * it, the shares add up to the total and no two differ by more than a piastre. Those two
     * properties together are what "as evenly as possible, losing nothing" means.
     */
    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 7, 99, 100, 101, 999, 1_000, 33_333, 35_000, 100_000_000_001L})
    void equalSplitAlwaysSumsToTheTotalAndIsEvenToWithinOnePiastre(long totalAmount) {
        for (int participantCount = 1; participantCount <= 25; participantCount++) {
            List<Long> participants = participantIds(participantCount);

            SequencedMap<Long, Long> shares = splitService.splitEqually(participants, totalAmount);

            assertThat(shares).as("one share per participant").hasSize(participantCount);
            assertThat(sumOf(shares)).as("sum for %d participants", participantCount).isEqualTo(totalAmount);
            long smallest = shares.values().stream().mapToLong(Long::longValue).min().orElseThrow();
            long largest = shares.values().stream().mapToLong(Long::longValue).max().orElseThrow();
            assertThat(largest - smallest).as("spread for %d participants", participantCount)
                    .isLessThanOrEqualTo(1L);
            assertThat(smallest).as("no negative share").isNotNegative();
        }
    }

    @Test
    void customSplitIsAcceptedWhenTheSharesAddUp() {
        SequencedMap<Long, Long> shares = splitService.validateCustomShares(
                List.of(1L, 2L, 3L), 35_000L, Map.of(1L, 20_000L, 2L, 10_000L, 3L, 5_000L));

        assertThat(sumOf(shares)).isEqualTo(35_000L);
        assertThat(shares.get(1L)).isEqualTo(20_000L);
    }

    /** Returned in participant order even though the request map has no order of its own. */
    @Test
    void customSplitIsReturnedInParticipantOrder() {
        Map<Long, Long> unordered = new LinkedHashMap<>();
        unordered.put(3L, 5_000L);
        unordered.put(1L, 20_000L);
        unordered.put(2L, 10_000L);

        SequencedMap<Long, Long> shares =
                splitService.validateCustomShares(List.of(1L, 2L, 3L), 35_000L, unordered);

        assertThat(shares.keySet()).containsExactly(1L, 2L, 3L);
    }

    /** A participant who owes nothing this time is still a participant. */
    @Test
    void customSplitAllowsAZeroShare() {
        SequencedMap<Long, Long> shares = splitService.validateCustomShares(
                List.of(1L, 2L), 35_000L, Map.of(1L, 35_000L, 2L, 0L));

        assertThat(shares.get(2L)).isZero();
        assertThat(sumOf(shares)).isEqualTo(35_000L);
    }

    /** BR-1: the error has to say which way it is out, which is why it quotes both numbers. */
    @ParameterizedTest
    @CsvSource({
            "34000, 35000, Shares total 34000 piastres but the expense is 35000 piastres; they must add up exactly",
            "36000, 35000, Shares total 36000 piastres but the expense is 35000 piastres; they must add up exactly",
            "34999, 35000, Shares total 34999 piastres but the expense is 35000 piastres; they must add up exactly",
    })
    void customSplitIsRejectedWhenTheSharesDoNotAddUp(
            long firstShare, long totalAmount, String expectedMessage) {

        Map<Long, Long> shares = Map.of(1L, firstShare, 2L, 0L);

        assertThatThrownBy(() -> splitService.validateCustomShares(List.of(1L, 2L), totalAmount, shares))
                .isInstanceOf(SplitTotalMismatchException.class)
                .hasMessage(expectedMessage);
    }

    /** One piastre out is still out: BR-1 is an equality, not a tolerance. */
    @Test
    void customSplitIsRejectedWhenOffByASinglePiastre() {
        Map<Long, Long> shares = Map.of(1L, 17_500L, 2L, 17_499L);

        assertThatThrownBy(() -> splitService.validateCustomShares(List.of(1L, 2L), 35_000L, shares))
                .isInstanceOf(SplitTotalMismatchException.class);
    }

    @Test
    void customSplitIsRejectedWhenAParticipantHasNoShare() {
        Map<Long, Long> shares = Map.of(1L, 35_000L);

        assertThatThrownBy(() -> splitService.validateCustomShares(List.of(1L, 2L), 35_000L, shares))
                .isInstanceOf(SplitParticipantMismatchException.class)
                .hasMessageContaining("no share was given for participants [2]");
    }

    @Test
    void customSplitIsRejectedWhenAShareIsGivenForANonParticipant() {
        Map<Long, Long> shares = Map.of(1L, 20_000L, 2L, 15_000L, 9L, 0L);

        assertThatThrownBy(() -> splitService.validateCustomShares(List.of(1L, 2L), 35_000L, shares))
                .isInstanceOf(SplitParticipantMismatchException.class)
                .hasMessageContaining("shares were given for non-participants [9]");
    }

    /** Both problems at once are reported together, so the form can be fixed in one go. */
    @Test
    void customSplitReportsMissingAndUnexpectedSharesTogether() {
        Map<Long, Long> shares = Map.of(1L, 35_000L, 9L, 0L);

        assertThatThrownBy(() -> splitService.validateCustomShares(List.of(1L, 2L), 35_000L, shares))
                .isInstanceOf(SplitParticipantMismatchException.class)
                .hasMessageContaining("no share was given for participants [2]")
                .hasMessageContaining("shares were given for non-participants [9]");
    }

    /** The participant mismatch is reported before the total, because it is the more basic error. */
    @Test
    void customSplitReportsAParticipantMismatchRatherThanATotalMismatch() {
        Map<Long, Long> shares = Map.of(1L, 1L);

        assertThatThrownBy(() -> splitService.validateCustomShares(List.of(1L, 2L), 35_000L, shares))
                .isInstanceOf(SplitParticipantMismatchException.class);
    }

    @Test
    void splitDispatchesOnTheSplitType() {
        assertThat(splitService.split(SplitType.EQUAL, List.of(1L, 2L, 3L), 100L, Map.of()))
                .containsExactly(Map.entry(1L, 34L), Map.entry(2L, 33L), Map.entry(3L, 33L));

        assertThat(splitService.split(
                SplitType.CUSTOM, List.of(1L, 2L), 100L, Map.of(1L, 60L, 2L, 40L)))
                .containsExactly(Map.entry(1L, 60L), Map.entry(2L, 40L));
    }

    /*
     * The cases below are preconditions rather than user errors: the request DTO rejects all of
     * them before a request reaches the service. They are asserted anyway because this service is
     * public and pure, so a future caller may well be internal, and silence would be worse than a
     * loud failure -- an empty list would divide by zero and a duplicate would unbalance the split.
     */

    @Test
    void splittingWithNoParticipantsIsRejectedRatherThanDividingByZero() {
        assertThatThrownBy(() -> splitService.splitEqually(List.of(), 35_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An expense needs at least one participant");
    }

    @Test
    void splittingWithARepeatedParticipantIsRejected() {
        assertThatThrownBy(() -> splitService.splitEqually(List.of(1L, 1L, 2L), 35_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Participants must be distinct");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -35_000})
    void splittingANonPositiveTotalIsRejected(long totalAmount) {
        assertThatThrownBy(() -> splitService.splitEqually(List.of(1L, 2L), totalAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be greater than zero");
    }

    @Test
    void aNegativeCustomShareIsRejected() {
        Map<Long, Long> shares = Map.of(1L, 40_000L, 2L, -5_000L);

        assertThatThrownBy(() -> splitService.validateCustomShares(List.of(1L, 2L), 35_000L, shares))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Custom shares must not be null or negative");
    }

    /** Overflow must fail loudly rather than wrap into a total that happens to match. */
    @Test
    void customSharesThatOverflowFailLoudly() {
        Map<Long, Long> shares = Map.of(1L, Long.MAX_VALUE, 2L, Long.MAX_VALUE);

        assertThatThrownBy(() -> splitService.validateCustomShares(List.of(1L, 2L), 35_000L, shares))
                .isInstanceOf(ArithmeticException.class);
    }

    private static List<Long> participantIds(int count) {
        return java.util.stream.LongStream.rangeClosed(1, count).boxed().toList();
    }

    private static long sumOf(Map<Long, Long> shares) {
        return shares.values().stream().mapToLong(Long::longValue).sum();
    }
}
