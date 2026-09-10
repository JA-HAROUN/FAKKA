package com.oae.fakka.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    /**
     * BCrypt cost factor. 10 is the library default; 12 costs roughly 4x more CPU per
     * hash, which is the point — it slows offline cracking if the table is ever dumped,
     * while still leaving sign-in well under ~250ms on typical hardware.
     */
    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
