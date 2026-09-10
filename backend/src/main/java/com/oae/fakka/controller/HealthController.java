package com.oae.fakka.controller;

import com.oae.fakka.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Service availability")
public class HealthController {

    private final String applicationName;

    public HealthController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @Operation(
            summary = "Health check",
            description = "Returns UP whenever the application is able to serve requests. "
                    + "Does not probe the database or any external service.")
    @ApiResponse(responseCode = "200", description = "Application is serving requests")
    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", applicationName, Instant.now());
    }
}
