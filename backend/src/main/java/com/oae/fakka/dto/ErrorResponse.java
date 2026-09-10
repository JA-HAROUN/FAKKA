package com.oae.fakka.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * The single error shape returned by every failing request.
 * Field order here is the field order clients see, so keep it stable.
 */
@Schema(name = "ErrorResponse", description = "Standard error payload for all failed requests")
public record ErrorResponse(

        @Schema(description = "When the error was produced", example = "2026-09-10T12:34:56.789Z")
        Instant timestamp,

        @Schema(description = "HTTP status code", example = "404")
        int status,

        @Schema(description = "HTTP status reason phrase", example = "Not Found")
        String error,

        @Schema(description = "Human-readable detail, safe to show to the caller", example = "Group 42 was not found")
        String message,

        @Schema(description = "Request path that produced the error", example = "/api/groups/42")
        String path
) {
}
