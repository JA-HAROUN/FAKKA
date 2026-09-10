package com.oae.fakka.controller;

import com.oae.fakka.dto.ErrorResponse;
import com.oae.fakka.dto.GroupCardResponse;
import com.oae.fakka.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Per-user views. Currently just the personal dashboard (FR-4, FR-5).
 * <p>
 * <strong>Balances are not implemented yet.</strong> Every card reports a {@code userBalance} of
 * 0 and a status of {@code SETTLED} until the balance engine lands, so a client can build against
 * the real shape now but must not read today numbers as a settled group. The field and its sign
 * rule are final; only the value is pending.
 * <p>
 * <strong>Identity is caller-supplied here, and that is not authentication.</strong> The user is
 * a path variable, exactly the pattern {@link AuthController} warns about, so any caller can read
 * any user dashboard. Tolerable only for the trusted demo the spec scopes; with real auth the
 * dashboard should be the signed-in user only, derived from their session.
 */
@RestController
@RequestMapping("/api/users")
@Validated
@Tag(name = "Users", description = "Personal dashboard")
public class UserController {

    private final GroupService groupService;

    public UserController(GroupService groupService) {
        this.groupService = groupService;
    }

    @Operation(
            summary = "List the groups a user belongs to",
            description = "One card per group, newest first: name, image, member count, the user "
                    + "balance in piastres (100 piastres = 1 EGP) and its sign as POSITIVE, "
                    + "NEGATIVE or SETTLED. A user with no groups gets an empty array; an unknown "
                    + "user is a 404. NOTE: the balance engine is not built yet, so every balance "
                    + "is currently 0 and every status SETTLED.")
    @ApiResponse(responseCode = "200", description = "Dashboard cards, possibly empty")
    @ApiResponse(responseCode = "400", description = "The user id is not a positive number",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No user with this id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{userId}/groups")
    public List<GroupCardResponse> listGroups(
            @Parameter(description = "Id of the user whose dashboard to load", example = "1")
            @PathVariable @Positive(message = "must be a positive id") Long userId) {
        return groupService.listGroupsForUser(userId);
    }
}
