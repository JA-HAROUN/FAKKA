package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

/**
 * Sign-in failed. The message is identical whether the email is unknown or the
 * password is wrong, so the response cannot be used to enumerate accounts.
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
