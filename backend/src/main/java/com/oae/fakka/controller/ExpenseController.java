package com.oae.fakka.controller;

import com.oae.fakka.dto.CreateExpenseRequest;
import com.oae.fakka.dto.ErrorResponse;
import com.oae.fakka.dto.ExpenseResponse;
import com.oae.fakka.dto.ParseExpenseTextRequest;
import com.oae.fakka.dto.ParsedExpenseDraftResponse;
import com.oae.fakka.service.ExpenseService;
import com.oae.fakka.service.NaturalLanguageExpenseService;
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
 * Expense entry: manual, and AI-assisted (FR-12 to FR-23).
 *
 * <h2>One save path, two ways to fill the form</h2>
 * {@code POST /expenses} is the only endpoint here that writes anything.
 * {@code POST /expenses/parse-nl} reads a sentence and returns a draft for somebody to check;
 * confirming that draft means sending it back to {@code POST /expenses} like any other expense.
 * The two live in the same controller precisely because of that relationship -- FR-23 says AI
 * accelerates the existing workflow rather than creating a parallel one, and BR-6 says an AI
 * suggestion must be confirmed through the normal flow. A separate save path for parsed expenses
 * is the mistake this arrangement exists to make obvious.
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
    private final NaturalLanguageExpenseService naturalLanguageExpenseService;

    public ExpenseController(
            ExpenseService expenseService,
            NaturalLanguageExpenseService naturalLanguageExpenseService) {
        this.expenseService = expenseService;
        this.naturalLanguageExpenseService = naturalLanguageExpenseService;
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

    @Operation(
            summary = "Parse a natural-language description into a draft expense",
            description = "Sends the text and the group member names to an LLM and returns a "
                    + "proposed expense for review -- description, total, payer, participants and "
                    + "split type -- with names resolved to group member ids. "
                    + "<strong>Nothing is written.</strong> To record it, send the reviewed "
                    + "(and possibly corrected) values to POST /expenses, the same endpoint the "
                    + "manual form uses (BR-6). A name the model returned that matches nobody, or "
                    + "matches more than one member, is listed in unresolvedNames rather than "
                    + "guessed. If the parser is unavailable, times out, or returns an answer that "
                    + "cannot be turned into a draft, the response is 503 or 422 respectively, "
                    + "never a 500 -- fall back to manual entry (BR-7).")
    @ApiResponse(responseCode = "200", description = "A draft expense, not yet saved")
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No group with this id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422",
            description = "The parser answered, but the answer could not be turned into a usable "
                    + "draft; enter the expense manually",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503",
            description = "The parser is not configured, unreachable, or timed out; enter the "
                    + "expense manually",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/parse-nl")
    public ParsedExpenseDraftResponse parseNaturalLanguageExpense(
            @Parameter(description = "Id of the group the expense would belong to", example = "1")
            @PathVariable @Positive(message = "must be a positive id") Long groupId,
            @Valid @RequestBody ParseExpenseTextRequest request) {
        return naturalLanguageExpenseService.parse(groupId, request);
    }
}
