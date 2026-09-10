package com.oae.fakka.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credentials for sign-in.
 * <p>
 * Intentionally lighter validation than {@link SignUpRequest}: the password rules may
 * change over time, and applying today's rules here would lock out accounts created
 * under older rules. Only presence is required; correctness is decided by the hash check.
 */
@Schema(name = "SignInRequest", description = "Sign-in credentials")
public record SignInRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 254, message = "must be at most 254 characters")
        @Schema(description = "Email address", example = "ahmed@example.com")
        String email,

        @NotBlank(message = "must not be blank")
        @Size(max = 72, message = "must be at most 72 characters")
        @Schema(description = "Plain-text password", example = "correct horse battery")
        String password
) {

    /** Strips the email for the same reason as SignUpRequest; password is left as typed. */
    public SignInRequest {
        email = email == null ? null : email.strip();
    }
}
