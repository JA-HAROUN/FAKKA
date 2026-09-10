package com.oae.fakka.controller;

import com.oae.fakka.entity.User;
import com.oae.fakka.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AuthControllerTest {

    private static final String SIGNUP_PAYLOAD = """
            {"name":"Ahmed Ragy","email":"ahmed@example.com","password":"correct horse battery"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void signUpCreatesUserAndNeverReturnsPasswordHash() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Ahmed Ragy"))
                .andExpect(jsonPath("$.email").value("ahmed@example.com"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void signUpStoresBcryptHashRatherThanPlainText() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_PAYLOAD))
                .andExpect(status().isCreated());

        Optional<User> stored = userRepository.findByEmail("ahmed@example.com");
        assertThat(stored).isPresent();
        assertThat(stored.get().getPasswordHash())
                .isNotEqualTo("correct horse battery")
                .startsWith("$2");
    }

    @Test
    void signUpNormalisesEmailToLowerCase() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ahmed","email":"  AHMED@Example.COM ","password":"correct horse battery"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ahmed@example.com"));
    }

    /** Uniqueness must be case-insensitive, or "A@x.com" and "a@x.com" become two accounts. */
    @Test
    void signUpRejectsDuplicateEmailIgnoringCase() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_PAYLOAD))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Someone Else","email":"AHMED@example.com","password":"another password"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.path").value("/api/auth/signup"));
    }

    @Test
    void signUpRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  ","email":"not-an-email","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists());
    }

    /** BCrypt ignores bytes past 72, so an over-long password must be rejected, not truncated. */
    @Test
    void signUpRejectsPasswordLongerThanBcryptLimit() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ahmed","email":"long@example.com","password":"%s"}
                                """.formatted("x".repeat(73))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signUpRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
    }

    @Test
    void signInReturnsProfileForCorrectCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_PAYLOAD))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"AHMED@example.com","password":"correct horse battery"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ahmed@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void signInRejectsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_PAYLOAD))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ahmed@example.com","password":"wrong password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    /**
     * The response for an unknown email must be byte-identical to the wrong-password
     * response, otherwise sign-in becomes an account-enumeration oracle.
     */
    @Test
    void signInRejectsUnknownEmailWithSameMessageAsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@example.com","password":"correct horse battery"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void signInRejectsMissingCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
