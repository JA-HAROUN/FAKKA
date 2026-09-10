package com.oae.fakka.repository;

import com.oae.fakka.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Callers must pass an already-normalised email; see User#normaliseEmail. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Exact name match, ignoring case — the "username search" half of FR-9.
     * <p>
     * Returns a list because {@code name} is a display name with no unique constraint, so
     * several accounts can share one. Callers must decide what to do with more than one match
     * rather than take the first. Exact rather than partial: this backs "add this person", not
     * a type-ahead, and a substring match would let "Ahmed" resolve to "Ahmed Ragy".
     */
    List<User> findByNameIgnoreCase(String name);
}
