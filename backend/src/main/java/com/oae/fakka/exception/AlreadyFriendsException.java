package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

/**
 * The two users are already friends.
 * <p>
 * Confirming this reveals nothing the caller does not already have: they can list their own
 * friends, and the request named the other user explicitly.
 */
public class AlreadyFriendsException extends ApiException {

    public AlreadyFriendsException() {
        super(HttpStatus.CONFLICT, "You are already friends with this user");
    }
}
