package com.oae.fakka.exception;

import org.springframework.http.HttpStatus;

/**
 * An export was asked for in a format that does not exist yet (FR-37).
 * <p>
 * 400 rather than 406: the format is a parameter the caller chose, not a negotiation over the
 * {@code Accept} header, so the request is simply wrong rather than unservable.
 * <p>
 * The message names what is available instead of only what is not, because the whole point of
 * reaching here is that the caller guessed.
 */
public class UnsupportedReportFormatException extends ApiException {

    public UnsupportedReportFormatException(String requestedFormat) {
        super(HttpStatus.BAD_REQUEST,
                "Export format %s is not supported; the only supported format is csv"
                        .formatted(requestedFormat));
    }
}
