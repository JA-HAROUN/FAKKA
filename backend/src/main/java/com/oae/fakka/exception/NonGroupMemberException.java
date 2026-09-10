package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

import java.util.Collection;

/**
 * Somebody outside the group was named in something that only members can take part in: a
 * payer or participant on an expense (FR-16, FR-17), or a party to a settlement (FR-34).
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
        /*
         * Deliberately says nothing about what was being attempted. Expenses and settlements
         * both raise this, and a message naming one of them would be wrong half the time.
         */
        super(HttpStatus.BAD_REQUEST,
                "Users %s are not members of this group".formatted(userIds));
    }
}
