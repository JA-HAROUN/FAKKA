package com.oae.fakka.controller;

import com.oae.fakka.dto.SignInRequest;
import com.oae.fakka.dto.SignUpRequest;
import com.oae.fakka.dto.UserResponse;
import com.oae.fakka.exception.EmailAlreadyRegisteredException;
import com.oae.fakka.exception.InvalidCredentialsException;
import com.oae.fakka.service.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link AuthController} with {@link AuthService} mocked.
 * <p>
 * No database and no service logic here: the questions are whether a bad payload is refused
 * before the service is ever called, what the controller hands the service once it accepts, and
 * whether {@code GlobalExceptionHandler} turns each thrown exception into the right status. The
 * end-to-end behaviour is covered separately by {@code AuthControllerTest}.
 */
@WebMvcTest(AuthController.class)
@ActiveProfiles("dev")
class AuthControllerWebMvcTest {

    private static final UserResponse PROFILE = new UserResponse(
            7L, "Ahmed Ragy", "ahmed@example.com", null, Instant.parse("2026-09-10T12:34:56.789Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void signUpReturns201WithTheServiceResponse() throws Exception {
        given(authService.signUp(any(SignUpRequest.class))).willReturn(PROFILE);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ahmed Ragy","email":"ahmed@example.com",
                                 "password":"correct horse battery"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Ahmed Ragy"))
                .andExpect(jsonPath("$.email").value("ahmed@example.com"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-10T12:34:56.789Z"));
    }

    /** Whitespace is stripped by the record constructor, so the service never sees a padded email. */
    @Test
    void signUpPassesTheStrippedPayloadToTheService() throws Exception {
        given(authService.signUp(any(SignUpRequest.class))).willReturn(PROFILE);
        ArgumentCaptor<SignUpRequest> captor = ArgumentCaptor.forClass(SignUpRequest.class);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Ahmed Ragy  ","email":"  ahmed@example.com ",
                                 "password":"correct horse battery","profileImageUrl":" https://img.example.com/a.jpg "}
                                """))
                .andExpect(status().isCreated());

        verify(authService).signUp(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Ahmed Ragy");
        assertThat(captor.getValue().email()).isEqualTo("ahmed@example.com");
        assertThat(captor.getValue().profileImageUrl()).isEqualTo("https://img.example.com/a.jpg");
        // The password is deliberately left exactly as typed; whitespace is legitimate content.
        assertThat(captor.getValue().password()).isEqualTo("correct horse battery");
    }

    @Test
    void signUpRejectsInvalidPayloadWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  ","email":"not-an-email","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/auth/signup"));

        verifyNoInteractions(authService);
    }

    /** BCrypt ignores bytes past 72, so the cap has to be enforced at the edge. */
    @Test
    void signUpRejectsOverLongPasswordWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ahmed","email":"ahmed@example.com","password":"%s"}
                                """.formatted("x".repeat(73))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void signUpMapsAlreadyRegisteredEmailTo409() throws Exception {
        given(authService.signUp(any(SignUpRequest.class)))
                .willThrow(new EmailAlreadyRegisteredException());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ahmed","email":"ahmed@example.com","password":"correct horse battery"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("An account with this email already exists"))
                .andExpect(jsonPath("$.path").value("/api/auth/signup"));
    }

    @Test
    void signUpRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));

        verifyNoInteractions(authService);
    }

    @Test
    void signInReturns200WithTheServiceResponse() throws Exception {
        given(authService.signIn(any(SignInRequest.class))).willReturn(PROFILE);

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ahmed@example.com","password":"correct horse battery"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void signInMapsInvalidCredentialsTo401() throws Exception {
        given(authService.signIn(any(SignInRequest.class))).willThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ahmed@example.com","password":"wrong password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void signInRejectsBlankCredentialsWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":""}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    /**
     * An exception the handler does not recognise is a bug, so the caller must get a generic 500
     * with none of the internal detail that would help an attacker or confuse a client.
     */
    @Test
    void unexpectedServiceFailureBecomesAGeneric500() throws Exception {
        given(authService.signIn(any(SignInRequest.class)))
                .willThrow(new IllegalStateException("connection pool exhausted at jdbc:mysql://prod"));

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ahmed@example.com","password":"correct horse battery"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                        .value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.message").value(not(containsString("jdbc"))));
    }

    /** Wrong verb on a real path must be a 405 in the shared error shape, not an HTML page. */
    @Test
    void wrongHttpMethodReturns405() throws Exception {
        mockMvc.perform(get("/api/auth/signup"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").value("Method GET is not supported for this endpoint"));

        verifyNoInteractions(authService);
    }
}
