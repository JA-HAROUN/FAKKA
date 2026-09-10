package com.oae.fakka.repository;

import com.oae.fakka.entity.ExpenseParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseParticipantRepository extends JpaRepository<ExpenseParticipant, Long> {

    /** Shares for one expense, in insertion order, which is the participant order from the request. */
    List<ExpenseParticipant> findByExpenseIdOrderByIdAsc(Long expenseId);
}
