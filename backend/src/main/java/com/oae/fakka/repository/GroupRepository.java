package com.oae.fakka.repository;

import com.oae.fakka.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Nothing beyond the inherited CRUD is needed yet: groups are created by id and read by id.
 * Kept as its own interface so later features (FR-4 group cards) have the obvious home.
 */
public interface GroupRepository extends JpaRepository<Group, Long> {
}
