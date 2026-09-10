package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

import java.util.Collection;

/**
 * Group creation named members who are not friends of the creator (FR-7).
 * <p>
 * 400 rather than 403: there is no authorisation to speak of here, the payload is simply
 * invalid. All offending ids are listed at once so a client can fix the whole selection in one
 * round trip -- and they are ids the caller just sent, so echoing them reveals nothing.
 * <p>
 * A user id that does not exist at all lands here too, rather than in a 404: a non-existent user
 * is not a friend, and keeping the two answers identical stops group creation from doubling as a
 * probe for which user ids exist.
 */
public class NonFriendMemberException extends ApiException {

    public NonFriendMemberException(Collection<Long> userIds) {
        super(HttpStatus.BAD_REQUEST,
                "Users %s cannot be added: a group member must be a friend of the creator"
                        .formatted(userIds));
    }
}
