package com.oae.fakka.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.List;

/**
 * A new group: name, optional image, and the friends to start it with (FR-6, FR-7).
 * <p>
 * The creator is not listed in {@code memberUserIds} -- the server adds them -- but sending
 * their own id is accepted and ignored rather than rejected, because a client that builds the
 * list from a member picker that includes the signed-in user is not making a mistake.
 */
@Schema(name = "CreateGroupRequest", description = "New group details")
public record CreateGroupRequest(

        /*
         * Caller-supplied identity again, with the caveat spelled out on GroupController: until
         * sign-in issues a credential there is nothing server-side to derive the creator from.
         */
        @NotNull(message = "must not be null")
        @Positive(message = "must be a positive id")
        @Schema(description = "Id of the user creating the group", example = "1")
        Long createdBy,

        @NotBlank(message = "must not be blank")
        @Size(max = 100, message = "must be at most 100 characters")
        @Schema(description = "Group name", example = "Dinner")
        String name,

        @Size(max = 2048, message = "must be at most 2048 characters")
        @URL(message = "must be a valid URL")
        @Schema(description = "Optional Cloudinary image URL",
                example = "https://res.cloudinary.com/demo/image/upload/dinner.jpg")
        String imageUrl,

        /*
         * The cap is a sanity bound, not a product rule: without one, a single request inserts
         * an unbounded number of rows. 99 plus the creator keeps a group at 100 people, far
         * above anything the split UI is meant to show.
         */
        @Size(max = 99, message = "must not contain more than 99 ids")
        @Schema(description = "Friends to add as members; may be empty for a group of one",
                example = "[2, 3]")
        List<@NotNull(message = "must not contain a null id")
             @Positive(message = "must contain positive ids") Long> memberUserIds
) {

    /** An absent list and an empty list mean the same thing, so only one of them reaches the service. */
    public CreateGroupRequest {
        name = name == null ? null : name.strip();
        imageUrl = imageUrl == null || imageUrl.isBlank() ? null : imageUrl.strip();
        memberUserIds = memberUserIds == null ? List.of() : memberUserIds;
    }

    /**
     * A repeated id is rejected rather than collapsed: it means the client and the server
     * disagree about who is in the group, and silently deduplicating would return a member
     * count the caller did not ask for. Counted with distinct() rather than a Set copy so that
     * a null element is reported by its own constraint instead of failing here.
     */
    @AssertTrue(message = "must not contain duplicate ids")
    @Schema(hidden = true)
    public boolean isMemberUserIdsDistinct() {
        return memberUserIds.stream().distinct().count() == memberUserIds.size();
    }
}
