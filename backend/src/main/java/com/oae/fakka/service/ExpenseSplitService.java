package com.oae.fakka.service;

import com.oae.fakka.dto.SplitType;
import com.oae.fakka.exception.SplitParticipantMismatchException;
import com.oae.fakka.exception.SplitTotalMismatchException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;

/**
 * Turns an expense total and a participant list into one share per participant (FR-18, FR-19).
 *
 * <h2>Why this is its own service</h2>
 * It has no repositories, no entities and no HTTP: every method is a function of its arguments,
 * so the arithmetic can be tested exhaustively without a database or a request. Rounding is the
 * part of an expense app that is easiest to get subtly wrong and hardest to notice, so it is
 * worth keeping somewhere it can be hammered directly.
 *
 * <h2>The one invariant</h2>
 * Every method returns shares that sum to {@code totalAmount} exactly (BR-1), whatever the
 * arithmetic. That is why amounts are piastres in a {@code long}: with a decimal type, a bill of
 * 100 split three ways would need a rounding policy and would still leave 0.01 unaccounted for,
 * and BR-5 (member balances in a group sum to zero) would drift a piastre at a time.
 *
 * <h2>Two kinds of failure</h2>
 * A caller mistake that the API layer already screens out -- no participants, a duplicate, a
 * non-positive total, a negative share -- is a programming error here and raises
 * {@link IllegalArgumentException}. A genuine payload problem that a user can fix -- shares that
 * do not match the participants, or do not add up -- raises an {@link com.oae.fakka.exception.ApiException}
 * subclass and reaches the client as a 400.
 */
@Service
public class ExpenseSplitService {

    /**
     * Dispatches on the split type. Participant order is preserved in the result, which is the
     * order the expense rows are then written in.
     */
    public SequencedMap<Long, Long> split(
            SplitType splitType,
            List<Long> participantUserIds,
            long totalAmount,
            Map<Long, Long> customShares) {

        return switch (splitType) {
            case EQUAL -> splitEqually(participantUserIds, totalAmount);
            case CUSTOM -> validateCustomShares(participantUserIds, totalAmount, customShares);
        };
    }

    /**
     * Divides {@code totalAmount} as evenly as piastres allow (FR-18).
     * <p>
     * Integer division leaves a remainder smaller than the participant count, and it is handed
     * out one piastre each to the participants at the front of the list. So 100 across three
     * people is 34, 33, 33 -- never 33.33 each, which would lose a piastre, and never a
     * "rounded" 34, 34, 34, which would invent one.
     * <p>
     * Front of the list rather than random or lowest-id, because it is reproducible: the same
     * request always produces the same shares, and the client controls the order, so whoever it
     * puts first absorbs the extra piastre.
     */
    public SequencedMap<Long, Long> splitEqually(List<Long> participantUserIds, long totalAmount) {
        requireParticipants(participantUserIds);
        requirePositiveTotal(totalAmount);

        int participantCount = participantUserIds.size();
        long baseShare = totalAmount / participantCount;
        /*
         * The remainder is strictly less than the participant count, which is an int, so this
         * narrowing cannot lose anything. It is also how many participants get the extra piastre.
         */
        int participantsOwedAnExtraPiastre = (int) (totalAmount % participantCount);

        SequencedMap<Long, Long> shares = new LinkedHashMap<>(participantCount);
        for (int position = 0; position < participantCount; position++) {
            long share = position < participantsOwedAnExtraPiastre ? baseShare + 1 : baseShare;
            shares.put(participantUserIds.get(position), share);
        }
        return shares;
    }

    /**
     * Checks a client-supplied split and returns it in participant order (FR-19).
     * <p>
     * Two things must hold: exactly one share per participant, and a sum equal to the total
     * (BR-1). Both are rejections rather than corrections -- the numbers came from a person
     * dividing a real bill, and quietly changing one of them is worse than asking again.
     */
    public SequencedMap<Long, Long> validateCustomShares(
            List<Long> participantUserIds, long totalAmount, Map<Long, Long> customShares) {

        requireParticipants(participantUserIds);
        requirePositiveTotal(totalAmount);
        requireNonNegativeShares(customShares);

        Set<Long> participants = new LinkedHashSet<>(participantUserIds);
        List<Long> withoutShares = participants.stream()
                .filter(userId -> !customShares.containsKey(userId))
                .toList();
        List<Long> notParticipating = customShares.keySet().stream()
                .filter(userId -> !participants.contains(userId))
                .sorted()
                .toList();

        if (!withoutShares.isEmpty() || !notParticipating.isEmpty()) {
            throw new SplitParticipantMismatchException(withoutShares, notParticipating);
        }

        SequencedMap<Long, Long> shares = new LinkedHashMap<>(participantUserIds.size());
        long shareTotal = 0;
        for (Long userId : participantUserIds) {
            long share = customShares.get(userId);
            shares.put(userId, share);
            /*
             * addExact rather than +: the request caps make an overflow unreachable through the
             * API, but a direct caller passing absurd values should fail loudly instead of
             * wrapping into a total that happens to match.
             */
            shareTotal = Math.addExact(shareTotal, share);
        }

        if (shareTotal != totalAmount) {
            throw new SplitTotalMismatchException(shareTotal, totalAmount);
        }
        return shares;
    }

    /** BR-3, restated as a precondition: the API rejects this long before it reaches here. */
    private static void requireParticipants(List<Long> participantUserIds) {
        if (participantUserIds == null || participantUserIds.isEmpty()) {
            throw new IllegalArgumentException("An expense needs at least one participant");
        }
        if (participantUserIds.stream().anyMatch(userId -> userId == null)) {
            throw new IllegalArgumentException("Participant ids must not be null");
        }
        if (participantUserIds.stream().distinct().count() != participantUserIds.size()) {
            // A repeat would silently collapse into one map entry and unbalance the split.
            throw new IllegalArgumentException("Participants must be distinct");
        }
    }

    private static void requirePositiveTotal(long totalAmount) {
        if (totalAmount <= 0) {
            throw new IllegalArgumentException(
                    "An expense total must be greater than zero, was " + totalAmount);
        }
    }

    private static void requireNonNegativeShares(Map<Long, Long> customShares) {
        if (customShares == null || customShares.isEmpty()) {
            throw new IllegalArgumentException("A custom split needs a share per participant");
        }
        boolean anyNegative = customShares.values().stream()
                .anyMatch(share -> share == null || share < 0);
        if (anyNegative) {
            // A negative share would mean the expense pays somebody, which has no meaning.
            throw new IllegalArgumentException("Custom shares must not be null or negative");
        }
    }
}
