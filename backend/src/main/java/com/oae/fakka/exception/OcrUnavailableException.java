package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

/**
 * The receipt OCR service could not be reached, is not configured, or timed out (FR-28, BR-7).
 * <p>
 * 503 rather than 500: nothing is broken about the request, a dependency is simply not answering.
 * The suffix differs from {@link AiUnavailableException} because the instruction for the caller
 * is different: fall back to <em>manual item entry</em>, not manual expense text entry.
 * <p>
 * Everything unexpected inside the OCR parse path also funnels here, for the same reason BR-7
 * exists: an unhandled 500 would tell the client nothing about what to do next.
 */
public class OcrUnavailableException extends ApiException {

    private static final String FALL_BACK = "; enter items manually instead";

    public OcrUnavailableException(String reason) {
        super(HttpStatus.SERVICE_UNAVAILABLE, reason + FALL_BACK);
    }

    public OcrUnavailableException(String reason, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, reason + FALL_BACK, cause);
    }
}
