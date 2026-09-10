package com.oae.fakka.dto;

import com.oae.fakka.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A user as every list in the app shows them: name and profile image, plus the id the client
 * sends back when picking people (FR-7, FR-10, FR-11).
 * <p>
 * One type for friend lists and group member lists because it is one projection of the same
 * entity, and a card that renders a friend renders a member identically. Deliberately narrower
 * than {@link UserResponse}: a member list should not hand every group member the email address
 * of everyone else.
 */
@Schema(name = "UserSummary", description = "A user as shown in a friend or member list")
public record UserSummaryResponse(

        @Schema(description = "The user id", example = "2")
        Long userId,

        @Schema(description = "Display name", example = "Mohamed Salah")
        String name,

        @Schema(description = "Profile image URL, null if unset")
        String profileImageUrl
) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getName(), user.getProfileImageUrl());
    }
}
