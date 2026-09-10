package com.oae.fakka.dto;

import com.oae.fakka.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Public view of a user. Has no password field at all, so no serialisation setting,
 * annotation, or future refactor can leak the hash through this type.
 */
@Schema(name = "UserResponse", description = "Public user profile")
public record UserResponse(

        @Schema(description = "User id", example = "1")
        Long id,

        @Schema(description = "Display name", example = "Ahmed Ragy")
        String name,

        @Schema(description = "Email address", example = "ahmed@example.com")
        String email,

        @Schema(description = "Profile image URL, null if unset")
        String profileImageUrl,

        @Schema(description = "When the account was created", example = "2026-09-10T12:34:56.789Z")
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getCreatedAt());
    }
}
