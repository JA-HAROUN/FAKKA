package com.oae.fakka.repository;

import com.oae.fakka.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Only the inherited CRUD is needed to record an expense. The group expense list and the balance
 * engine queries (FR-11, FR-31) belong here when they arrive.
 */
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
}
