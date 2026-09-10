package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

/**
 * A user tried to add themselves.
 * <p>
 * 400 rather than 409: nothing about the stored data conflicts, the request itself is
 * nonsensical. Allowing it would also make a user their own group co-member and their own
 * debtor, which the balance engine has no meaning for.
 */
public class SelfFriendshipException extends ApiException {

    public SelfFriendshipException() {
        super(HttpStatus.BAD_REQUEST, "You cannot add yourself as a friend");
    }
}
