package com.oae.fakka.controller;

import com.oae.fakka.dto.ErrorResponse;
import com.oae.fakka.dto.SignInRequest;
import com.oae.fakka.dto.SignUpRequest;
import com.oae.fakka.dto.UserResponse;
import com.oae.fakka.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-up and sign-in.
 *
 * <h2>Auth model tradeoff (FR-1: "No token auth needed")</h2>
 * Sign-in returns a user profile and nothing else — no token, no session cookie. The
 * client is expected to hold the returned {@code id} and send it on later requests.
 * <p>
 * <strong>This is not authentication and must not be mistaken for it.</strong> Any caller
 * can pass an arbitrary {@code userId} and act as that user, because nothing server-side
 * proves the caller ever signed in. It is acceptable only because the spec scopes this
 * build to a trusted demo with no real data.
 * <p>
 * The moment real users exist, sign-in must issue a server-verifiable credential (session
 * cookie or JWT) and every protected endpoint must derive the caller's identity from that
 * credential rather than from a request parameter. Deriving identity from the request body
 * is precisely the mistake this note exists to prevent, so no endpoint added later should
 * accept a caller-supplied {@code userId} as proof of identity.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Account creation and sign-in")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
            summary = "Create an account",
            description = "Validates the payload, rejects an email that is already registered, "
                    + "stores a BCrypt hash of the password, and returns the new profile. "
                    + "Emails are normalised to lower case, so uniqueness is case-insensitive.")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Email already registered",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signUp(@Valid @RequestBody SignUpRequest request) {
        return authService.signUp(request);
    }

    @Operation(
            summary = "Sign in",
            description = "Verifies the password against the stored hash and returns the profile. "
                    + "A wrong password and an unknown email produce the same 401 response, so the "
                    + "endpoint cannot be used to discover which emails are registered.")
    @ApiResponse(responseCode = "200", description = "Credentials accepted")
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Invalid email or password",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/signin")
    public UserResponse signIn(@Valid @RequestBody SignInRequest request) {
        return authService.signIn(request);
    }
}
