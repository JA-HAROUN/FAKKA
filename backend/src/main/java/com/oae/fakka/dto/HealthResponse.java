package com.oae.fakka.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "HealthResponse", description = "Liveness signal for the backend")
public record HealthResponse(

        @Schema(description = "Always \"UP\" when the application can serve requests", example = "UP")
        String status,

        @Schema(description = "Application name", example = "fakka")
        String application,

        @Schema(description = "Server time when the check ran", example = "2026-09-10T12:34:56.789Z")
        Instant timestamp
) {
}
