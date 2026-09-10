package com.oae.fakka.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Adds a registered user to {@code userId}'s friend list, identified by email or username
 * (FR-9). Exactly one identifier must be supplied.
 * <p>
 * <strong>"username" resolves against the account's display name.</strong> There is no separate
 * username column on {@code User} — FR-3 defines a profile as id, name, email and image — so a
 * username lookup matches {@code User.name} case-insensitively. Unlike email, that name is not
 * unique, so a lookup matching several accounts is reported as a conflict rather than resolved
 * by guessing; email is the identifier to use when a name is shared.
 */
@Schema(name = "AddFriendRequest", description = "Who is adding, and which registered user to add")
public record AddFriendRequest(

        /*
         * Caller-supplied identity, which the AuthController javadoc warns against. It is
         * unavoidable while sign-in issues no credential: there is nothing server-side to derive
         * the caller from. When real auth lands this field must be dropped and the owner taken
         * from the session, or any caller can edit any user's friend list.
         */
        @NotNull(message = "must not be null")
        @Positive(message = "must be a positive id")
        @Schema(description = "Id of the user gaining the friend", example = "1")
        Long userId,

        @Email(message = "must be a well-formed email address")
        @Size(max = 254, message = "must be at most 254 characters")
        @Schema(description = "Email of the user to add, matched case-insensitively",
                example = "mohamed@example.com")
        String email,

        @Size(max = 100, message = "must be at most 100 characters")
        @Schema(description = "Display name of the user to add, matched case-insensitively. "
                + "Use email instead when a name may be shared by several accounts.",
                example = "Mohamed Salah")
        String username
) {

    /*
     * Blanks collapse to null before validation runs, for two reasons: a client that sends
     * {"email": "", "username": "Ahmed"} means the username, and an empty string would otherwise
     * pass @Email (which treats "" as valid) and be treated here as a supplied identifier.
     */
    public AddFriendRequest {
        email = blankToNull(email);
        username = blankToNull(username);
    }

    /**
     * Neither identifier means there is nothing to look up; both means the two could point at
     * different accounts, and silently preferring one would add whichever the client did not
     * intend. Rejecting is the only unambiguous answer.
     */
    @AssertTrue(message = "exactly one of email or username must be provided")
    @Schema(hidden = true)
    public boolean isExactlyOneIdentifier() {
        return (email == null) != (username == null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
