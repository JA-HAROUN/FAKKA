package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

/**
 * The uploaded file is missing, empty, or is not an image (FR-24, FR-28).
 * <p>
 * 400 Bad Request: the problem is in the request itself, not in a downstream service.
 * Distinct from {@link AiUnavailableException} (503, OCR service unreachable) and
 * {@link AiResponseNotUsableException} (422, OCR reached but text unusable) -- all three
 * tell the client to fall back to manual item entry, but for different reasons.
 */
public class OcrImageInvalidException extends ApiException {

    public OcrImageInvalidException(String reason) {
        super(HttpStatus.BAD_REQUEST, reason + "; enter items manually instead");
    }
}
