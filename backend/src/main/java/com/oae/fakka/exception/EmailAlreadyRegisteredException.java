package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

/**
 * Signup was attempted with an email that already has an account.
 * <p>
 * This deliberately confirms that the email is taken, which is unavoidable for a
 * usable signup form. Sign-in does the opposite and never reveals existence.
 */
public class EmailAlreadyRegisteredException extends ApiException {

    public EmailAlreadyRegisteredException() {
        super(HttpStatus.CONFLICT, "An account with this email already exists");
    }
}
