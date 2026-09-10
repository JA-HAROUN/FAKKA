package com.oae.fakka.controller;

import com.oae.fakka.dto.CreateExpenseRequest;
import com.oae.fakka.dto.ErrorResponse;
import com.oae.fakka.dto.ExpenseResponse;
import com.oae.fakka.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual expense entry (FR-12 to FR-19).
 * <p>
 * Its own controller rather than more methods on {@link GroupController}: an expense is a
 * resource in its own right, and the AI-assisted and OCR entry paths (FR-20, FR-24) are meant to
 * feed this same endpoint rather than a parallel one, so it is worth keeping alone.
 * <p>
 * Amounts are piastres throughout, matching the dashboard: 35000 is 350.00 EGP. There is no
 * decimal anywhere in the API, which is what lets BR-1 be an exact equality.
 * <p>
 * <strong>Identity is caller-supplied here, and that is not authentication.</strong> Nothing
 * proves the caller is even in the group -- only that the payer and the participants are, which
 * is a data rule, not a permission. Any caller can record an expense in anyone name. Tolerable
 * only for the trusted demo the spec scopes; with real auth the caller must be a member of the
 * group, and that check belongs here.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
@Validated
@Tag(name = "Expenses", description = "Record shared expenses in a group")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @Operation(
            summary = "Record an expense",
            description = "Stores the expense and one share per participant, in the participant "
                    + "order given. An EQUAL split divides the total evenly and gives the rounding "
                    + "remainder, a piastre each, to the participants at the front of the list, so "
                    + "the shares always sum to the total exactly. A CUSTOM split takes the shares "
                    + "from the request and is rejected outright unless it covers exactly the "
                    + "participants and sums to totalAmount (BR-1). The payer and every "
                    + "participant must be members of the group.")
    @ApiResponse(responseCode = "201", description = "Expense recorded, with the resulting shares")
    @ApiResponse(responseCode = "400",
            description = "Validation failed, a payer or participant is not a group member, or a "
                    + "custom split does not match the participants or the total",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No group with this id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse createExpense(
            @Parameter(description = "Id of the group the expense belongs to", example = "1")
            @PathVariable @Positive(message = "must be a positive id") Long groupId,
            @Valid @RequestBody CreateExpenseRequest request) {
        return expenseService.createExpense(groupId, request);
    }
}
