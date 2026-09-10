package com.oae.fakka.repository;

import com.oae.fakka.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Callers must pass an already-normalised email; see AuthService#normaliseEmail. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
