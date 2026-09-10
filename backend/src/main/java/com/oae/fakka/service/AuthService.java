package com.oae.fakka.service;

import com.oae.fakka.dto.SignInRequest;
import com.oae.fakka.dto.SignUpRequest;
import com.oae.fakka.dto.UserResponse;
import com.oae.fakka.entity.User;
import com.oae.fakka.exception.EmailAlreadyRegisteredException;
import com.oae.fakka.exception.InvalidCredentialsException;
import com.oae.fakka.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Hash of a random throwaway secret, verified against when no account matches, so an
     * unknown email costs the same time as a wrong password. Without it, sign-in latency
     * alone would tell an attacker which emails are registered.
     */
    private final String timingEqualisationHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;

        byte[] randomSecret = new byte[32];
        new SecureRandom().nextBytes(randomSecret);
        this.timingEqualisationHash =
                passwordEncoder.encode(Base64.getEncoder().encodeToString(randomSecret));
    }

    @Transactional
    public UserResponse signUp(SignUpRequest request) {
        String email = User.normaliseEmail(request.email());

        // Fast path so the common case returns a clear 409 rather than a constraint error.
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .profileImageUrl(blankToNull(request.profileImageUrl()))
                .build();

        try {
            User saved = userRepository.saveAndFlush(user);
            log.info("Registered user id={}", saved.getId());
            return UserResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            /*
             * Two concurrent signups can both pass existsByEmail; the unique index is what
             * actually decides. Flushing inside this method is what lets us translate that
             * loss into a 409 here instead of it surfacing as a generic conflict later.
             */
            throw new EmailAlreadyRegisteredException();
        }
    }

    @Transactional(readOnly = true)
    public UserResponse signIn(SignInRequest request) {
        Optional<User> user = userRepository.findByEmail(User.normaliseEmail(request.email()));

        if (user.isEmpty()) {
            passwordEncoder.matches(request.password(), timingEqualisationHash);
            // No email in the log: failed sign-ins are attacker-controlled input.
            log.debug("Sign-in failed: no matching account");
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), user.get().getPasswordHash())) {
            log.debug("Sign-in failed: wrong password for user id={}", user.get().getId());
            throw new InvalidCredentialsException();
        }

        return UserResponse.from(user.get());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
