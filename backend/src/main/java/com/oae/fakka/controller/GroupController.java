package com.oae.fakka.controller;

import com.oae.fakka.dto.CreateGroupRequest;
import com.oae.fakka.dto.ErrorResponse;
import com.oae.fakka.dto.GroupResponse;
import com.oae.fakka.dto.UserSummaryResponse;
import com.oae.fakka.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Groups: create one, list who is in it (FR-6, FR-7, FR-11).
 * <p>
 * Members must be friends of the creator, so the friends tab is the only way people enter a
 * group. The creator is added automatically and is a member row like any other, which keeps
 * membership in one place for the balance engine to read later.
 * <p>
 * <strong>Identity is caller-supplied here, and that is not authentication.</strong> The creator
 * arrives in the request body, exactly the pattern {@link AuthController} warns about, because
 * sign-in issues no credential the server can check. Any caller can therefore create a group as
 * anyone else, and read any group membership. Acceptable only for the trusted demo the spec
 * scopes; when real auth lands, the creator must come from the session and
 * {@code CreateGroupRequest.createdBy} must go.
 */
@RestController
@RequestMapping("/api/groups")
@Validated
@Tag(name = "Groups", description = "Create groups and read their membership")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @Operation(
            summary = "Create a group",
            description = "Creates the group and its members in one transaction. The creator is "
                    + "added automatically, so an empty member list is valid and makes a group of "
                    + "one. Every other member must already be a friend of the creator; duplicate "
                    + "ids are rejected, though the creator may harmlessly appear in the list.")
    @ApiResponse(responseCode = "201", description = "Group created")
    @ApiResponse(responseCode = "400",
            description = "Validation failed, duplicate member ids, or a member who is not a friend",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The creating user does not exist",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createGroup(@Valid @RequestBody CreateGroupRequest request) {
        return groupService.createGroup(request);
    }

    @Operation(
            summary = "List group members",
            description = "Returns each member id, name and profile image, ordered by name. The "
                    + "creator is included. A group always has at least one member, so an unknown "
                    + "group id is a 404 rather than an empty array.")
    @ApiResponse(responseCode = "200", description = "Member list")
    @ApiResponse(responseCode = "400", description = "The group id is not a positive number",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No group with this id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}/members")
    public List<UserSummaryResponse> listMembers(
            @Parameter(description = "Id of the group", example = "1")
            @PathVariable @Positive(message = "must be a positive id") Long id) {
        return groupService.listMembers(id);
    }
}
