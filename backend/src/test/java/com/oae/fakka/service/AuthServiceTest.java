package com.oae.fakka.service;

import com.oae.fakka.dto.SignInRequest;
import com.oae.fakka.dto.SignUpRequest;
import com.oae.fakka.dto.UserResponse;
import com.oae.fakka.entity.User;
import com.oae.fakka.exception.EmailAlreadyRegisteredException;
import com.oae.fakka.exception.InvalidCredentialsException;
import com.oae.fakka.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AuthService} with the repository and the password encoder mocked.
 * <p>
 * These cover the decisions the service makes rather than the HTTP surface: what is normalised
 * before a query, what is written, and which failure becomes which exception. Mocking the
 * encoder is also the only way to prove the timing-equalisation branch actually verifies a hash,
 * which no end-to-end test can observe.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String TIMING_HASH = "$2a$10$timingEqualisationHashPlaceholder000000000000000000";
    private static final String PASSWORD_HASH = "$2a$10$realPasswordHashPlaceholder0000000000000000000000000";
    private static final String RAW_PASSWORD = "correct horse battery";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        /*
         * The constructor hashes a random throwaway secret for timing equalisation, so the
         * encoder has to answer before the service exists. Every test therefore uses this stub,
         * and the sign-in tests below rely on knowing what it returned.
         */
        given(passwordEncoder.encode(anyString())).willReturn(TIMING_HASH);
        authService = new AuthService(userRepository, passwordEncoder);
    }

    @Test
    void signUpStoresNormalisedEmailAndHashedPassword() {
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(PASSWORD_HASH);
        given(userRepository.existsByEmail("ahmed@example.com")).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        UserResponse response = authService.signUp(new SignUpRequest(
                "  Ahmed Ragy  ", "  AHMED@Example.COM ", RAW_PASSWORD, null));

        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("ahmed@example.com");
        assertThat(userCaptor.getValue().getName()).isEqualTo("Ahmed Ragy");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.email()).isEqualTo("ahmed@example.com");
    }

    /** The raw password must never be what lands in the entity. */
    @Test
    void signUpNeverStoresThePlainPassword() {
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(PASSWORD_HASH);
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(this::returnSavedUser);

        authService.signUp(new SignUpRequest("Ahmed", "ahmed@example.com", RAW_PASSWORD, null));

        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isNotEqualTo(RAW_PASSWORD);
        verify(passwordEncoder).encode(RAW_PASSWORD);
    }

    @Test
    void signUpStoresNullForBlankProfileImage() {
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(PASSWORD_HASH);
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(this::returnSavedUser);

        authService.signUp(new SignUpRequest("Ahmed", "ahmed@example.com", RAW_PASSWORD, "   "));

        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getProfileImageUrl()).isNull();
    }

    /** The fast path exists so the common duplicate never reaches the database. */
    @Test
    void signUpRejectsKnownEmailWithoutWriting() {
        given(userRepository.existsByEmail("ahmed@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signUp(
                new SignUpRequest("Ahmed", "ahmed@example.com", RAW_PASSWORD, null)))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    /**
     * Two concurrent signups can both pass existsByEmail, so the unique index decides. The loser
     * must come back as the same conflict, not as a generic database failure.
     */
    @Test
    void signUpTranslatesUniqueIndexLossIntoConflict() {
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(PASSWORD_HASH);
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(new DataIntegrityViolationException("uk_users_email"));

        assertThatThrownBy(() -> authService.signUp(
                new SignUpRequest("Ahmed", "ahmed@example.com", RAW_PASSWORD, null)))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void signInReturnsProfileWhenPasswordMatches() {
        User stored = storedUser();
        given(userRepository.findByEmail("ahmed@example.com")).willReturn(Optional.of(stored));
        given(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(true);

        UserResponse response = authService.signIn(new SignInRequest("ahmed@example.com", RAW_PASSWORD));

        assertThat(response.id()).isEqualTo(stored.getId());
        assertThat(response.email()).isEqualTo("ahmed@example.com");
    }

    /** Lookups have to use the same canonical form the row was stored under. */
    @Test
    void signInLooksUpTheNormalisedEmail() {
        given(userRepository.findByEmail("ahmed@example.com")).willReturn(Optional.of(storedUser()));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);

        authService.signIn(new SignInRequest("  AHMED@Example.COM ", RAW_PASSWORD));

        verify(userRepository).findByEmail("ahmed@example.com");
    }

    @Test
    void signInRejectsWrongPassword() {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(storedUser()));
        given(passwordEncoder.matches("wrong password", PASSWORD_HASH)).willReturn(false);

        assertThatThrownBy(() -> authService.signIn(
                new SignInRequest("ahmed@example.com", "wrong password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    /**
     * The point of the timing-equalisation hash: an unknown email must still cost one BCrypt
     * verification, or sign-in latency alone tells an attacker which addresses are registered.
     * Only a mocked encoder can show that the call happens.
     */
    @Test
    void signInVerifiesAHashEvenWhenNoAccountMatches() {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.signIn(
                new SignInRequest("nobody@example.com", RAW_PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder).matches(RAW_PASSWORD, TIMING_HASH);
    }

    /** Wrong password and unknown email must be indistinguishable to the caller. */
    @Test
    void signInFailuresAreIndistinguishable() {
        given(userRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());
        given(userRepository.findByEmail("ahmed@example.com")).willReturn(Optional.of(storedUser()));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

        String unknownEmailMessage = messageOfFailedSignIn("nobody@example.com");
        String wrongPasswordMessage = messageOfFailedSignIn("ahmed@example.com");

        assertThat(unknownEmailMessage).isEqualTo(wrongPasswordMessage);
    }

    private String messageOfFailedSignIn(String email) {
        try {
            authService.signIn(new SignInRequest(email, RAW_PASSWORD));
            throw new AssertionError("Expected sign-in to fail for " + email);
        } catch (InvalidCredentialsException exception) {
            return exception.getMessage();
        }
    }

    private User storedUser() {
        return User.builder()
                .id(1L)
                .name("Ahmed Ragy")
                .email("ahmed@example.com")
                .passwordHash(PASSWORD_HASH)
                .build();
    }

    private User returnSavedUser(org.mockito.invocation.InvocationOnMock invocation) {
        User saved = invocation.getArgument(0);
        saved.setId(7L);
        return saved;
    }
}
