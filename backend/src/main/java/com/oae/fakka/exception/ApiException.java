package com.oae.fakka.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for failures that map to a deliberate HTTP status.
 * <p>
 * Throwing a subclass is how a service reports an expected failure: the message
 * is returned to the caller verbatim, so it must never carry internals or secrets.
 * Anything not extending this becomes a 500 with a generic message.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    protected ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
