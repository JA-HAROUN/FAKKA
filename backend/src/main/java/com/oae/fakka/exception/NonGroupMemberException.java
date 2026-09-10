package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

import java.util.Collection;

/**
 * An expense named a payer or a participant who is not in the group (FR-16, FR-17).
 * <p>
 * 400 rather than 403, for the same reason as {@link NonFriendMemberException}: there is no
 * authorisation here, the payload is simply invalid. Every offending id is listed at once so the
 * form can be fixed in one round trip.
 * <p>
 * A user id that does not exist at all lands here too rather than in a 404: a non-existent user
 * is not a member, and keeping the answers identical stops expense creation from doubling as a
 * probe for which user ids exist.
 */
public class NonGroupMemberException extends ApiException {

    public NonGroupMemberException(Collection<Long> userIds) {
        super(HttpStatus.BAD_REQUEST,
                "Users %s are not members of this group, so they cannot pay for or share an expense"
                        .formatted(userIds));
    }
}
