package com.oae.fakka.controller;

import com.oae.fakka.dto.AddFriendRequest;
import com.oae.fakka.dto.ErrorResponse;
import com.oae.fakka.dto.UserSummaryResponse;
import com.oae.fakka.service.FriendService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The friends tab: add a friend, list friends (FR-8 to FR-10).
 * <p>
 * A friendship is symmetric and mutual on creation. Adding someone puts each user in the other
 * list immediately, with no request to accept, because nothing in the spec describes one. See
 * {@link com.oae.fakka.entity.Friendship} for how that symmetry is stored.
 * <p>
 * <strong>Identity is caller-supplied here, and that is not authentication.</strong> Both
 * endpoints take the acting user as a parameter, exactly the pattern {@link AuthController}
 * warns about, because sign-in issues no credential for the server to check. Any caller can
 * therefore read or edit any user friend list. That is tolerable only for the trusted demo the
 * spec scopes; when real auth arrives, the acting user must come from the session and these
 * parameters must go.
 */
@RestController
@RequestMapping("/api/friends")
@Validated
@Tag(name = "Friends", description = "Add and list friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @Operation(
            summary = "Add a friend",
            description = "Finds a registered user by email or username, exactly one of the two, "
                    + "and makes the two users friends in both directions. Adding yourself is "
                    + "rejected, as is a friendship that already exists. Because display names are "
                    + "not unique, a username matching several accounts is reported as a conflict; "
                    + "use the email address in that case.")
    @ApiResponse(responseCode = "201", description = "Friendship created; returns the new friend")
    @ApiResponse(responseCode = "400", description = "Validation failed, or the user tried to add themselves",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The acting user or the target user does not exist",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Already friends, or the username matched several accounts",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummaryResponse addFriend(@Valid @RequestBody AddFriendRequest request) {
        return friendService.addFriend(request);
    }

    @Operation(
            summary = "List a user friends",
            description = "Returns each friend id, name and profile image, ordered by name. "
                    + "A user with no friends yet returns an empty array; an unknown user is a 404.")
    @ApiResponse(responseCode = "200", description = "Friend list, possibly empty")
    @ApiResponse(responseCode = "400", description = "userId is missing or not a positive number",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No user with this id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public List<UserSummaryResponse> listFriends(
            @Parameter(description = "Id of the user whose friends to list", example = "1")
            @RequestParam @Positive(message = "must be a positive id") Long userId) {
        return friendService.listFriends(userId);
    }
}
