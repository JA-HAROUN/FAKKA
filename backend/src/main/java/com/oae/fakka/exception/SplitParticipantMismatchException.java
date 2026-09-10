package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

import java.util.Collection;
import java.util.stream.Stream;

/**
 * A custom split whose shares do not line up with the participant list.
 * <p>
 * Separate from a total mismatch because the fix is different: a share is missing, or one was
 * given for somebody who is not taking part. Both lists are named so the client can correct the
 * whole form at once; they are ids the caller just sent, so echoing them reveals nothing.
 */
public class SplitParticipantMismatchException extends ApiException {

    public SplitParticipantMismatchException(
            Collection<Long> withoutShares, Collection<Long> notParticipating) {
        super(HttpStatus.BAD_REQUEST, describe(withoutShares, notParticipating));
    }

    private static String describe(
            Collection<Long> withoutShares, Collection<Long> notParticipating) {

        String problems = Stream.of(
                        withoutShares.isEmpty() ? null
                                : "no share was given for participants %s".formatted(withoutShares),
                        notParticipating.isEmpty() ? null
                                : "shares were given for non-participants %s".formatted(notParticipating))
                .filter(problem -> problem != null)
                .reduce((first, second) -> first + ", and " + second)
                .orElse("the shares do not match the participants");

        return "A custom split must give exactly one share per participant: " + problems;
    }
}
