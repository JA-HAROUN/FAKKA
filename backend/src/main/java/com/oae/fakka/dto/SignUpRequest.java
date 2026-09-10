package com.oae.fakka.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

@Schema(name = "SignUpRequest", description = "New account details")
public record SignUpRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 100, message = "must be at most 100 characters")
        @Schema(description = "Display name", example = "Ahmed Ragy")
        String name,

        @NotBlank(message = "must not be blank")
        @Email(message = "must be a well-formed email address")
        @Size(max = 254, message = "must be at most 254 characters")
        @Schema(description = "Email address, matched case-insensitively", example = "ahmed@example.com")
        String email,

        /*
         * The 72-byte cap is a real BCrypt limit, not an arbitrary policy: BCrypt silently
         * ignores bytes past 72, so a longer password would authenticate on its first 72
         * bytes alone. Rejecting it is clearer than silently truncating.
         */
        @NotBlank(message = "must not be blank")
        @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        @Schema(description = "Plain-text password, never stored or logged", example = "correct horse battery")
        String password,

        @Size(max = 2048, message = "must be at most 2048 characters")
        @URL(message = "must be a valid URL")
        @Schema(description = "Optional Cloudinary image URL", example = "https://res.cloudinary.com/demo/image/upload/avatar.jpg")
        String profileImageUrl
) {

    /*
     * Jackson builds records through this constructor, so stripping here happens before
     * validation runs. That ordering matters: a pasted "  ahmed@example.com " would
     * otherwise fail @Email and return "must be a well-formed email address" for an
     * address that is perfectly valid. Password is left untouched — whitespace is
     * legitimate password content.
     */
    public SignUpRequest {
        name = name == null ? null : name.strip();
        email = email == null ? null : email.strip();
        profileImageUrl = profileImageUrl == null ? null : profileImageUrl.strip();
    }
}
