package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

/**
 * A requested resource does not exist, or the caller may not know that it does.
 * Prefer this over a 403 when revealing existence would itself leak information.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public ResourceNotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, "%s %s was not found".formatted(resource, id));
    }
}
