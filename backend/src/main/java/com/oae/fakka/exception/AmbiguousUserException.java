package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

/**
 * A username lookup matched more than one account, because display names are not unique.
 * <p>
 * 409 with a pointer to the unambiguous identifier, rather than picking a match: adding the
 * wrong person to a friend list is silent and then propagates into groups and balances. The
 * count is safe to state — the caller supplied the name — but no names or emails are returned,
 * so this cannot be used to enumerate accounts.
 */
public class AmbiguousUserException extends ApiException {

    public AmbiguousUserException(int matchCount) {
        super(HttpStatus.CONFLICT,
                "%d users share this name. Add this friend by email address instead."
                        .formatted(matchCount));
    }
}
