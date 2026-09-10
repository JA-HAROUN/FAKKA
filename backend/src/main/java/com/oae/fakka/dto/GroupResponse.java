package com.oae.fakka.dto;

import com.oae.fakka.entity.Group;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * A group as the dashboard card needs it (FR-4): name, image and member count.
 * <p>
 * The member count is passed in rather than derived here, because a DTO that queried for it
 * would hide a database round trip behind a mapping call. Balance is absent until the expense
 * engine exists; the card renders it separately.
 */
@Schema(name = "GroupResponse", description = "A group and its size")
public record GroupResponse(

        @Schema(description = "Group id", example = "1")
        Long id,

        @Schema(description = "Group name", example = "Dinner")
        String name,

        @Schema(description = "Group image URL, null if unset")
        String imageUrl,

        @Schema(description = "Id of the user who created the group", example = "1")
        Long createdBy,

        @Schema(description = "When the group was created", example = "2026-09-10T12:34:56.789Z")
        Instant createdAt,

        @Schema(description = "Number of members, including the creator", example = "3")
        int memberCount
) {

    public static GroupResponse of(Group group, int memberCount) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getImageUrl(),
                group.getCreatedBy(),
                group.getCreatedAt(),
                memberCount);
    }
}
