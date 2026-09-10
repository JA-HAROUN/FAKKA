package com.oae.fakka.controller;

import com.oae.fakka.dto.CreateSettlementRequest;
import com.oae.fakka.dto.ErrorResponse;
import com.oae.fakka.dto.MarkSettlementPaidRequest;
import com.oae.fakka.dto.SettlementResponse;
import com.oae.fakka.dto.SuggestedSettlementResponse;
import com.oae.fakka.service.SettlementService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Settling up: what would clear the group, what has been agreed, and what has been paid
 * (FR-32 to FR-35).
 *
 * <h2>Suggested and stored are different things</h2>
 * {@code GET .../settlements/suggested} is a computation over the current balances and has no
 * ids: it changes whenever an expense does. {@code POST .../settlements} turns one of those
 * suggestions -- or an ad hoc payment -- into a record with an id and a status. Only a stored
 * settlement marked PAID moves a balance.
 *
 * <h2>Why there is no shared path prefix</h2>
 * Creating and suggesting are scoped to a group, so they hang off {@code /api/groups/{groupId}}.
 * Marking one paid needs only its own id, so it lives at {@code /api/settlements/{id}/pay}: the
 * group adds nothing to a request that identifies the row exactly, and putting it in the path
 * would invite a mismatch between the two.
 *
 * <h2>Identity is caller-supplied, so the payer restriction is intent rather than security</h2>
 * FR-35 says only the two parties may mark a settlement paid, and the check is here and tested.
 * But the caller states who they are, so anyone can satisfy it by naming a party -- exactly the
 * pattern {@link AuthController} warns about. It is written this way so the rule and its tests
 * already exist when sign-in starts issuing a credential the server can verify; until then it
 * stops mistakes, not attackers.
 */
@RestController
@Validated
@Tag(name = "Settlements", description = "Suggested, tracked and paid settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Operation(
            summary = "Suggest the payments that would settle the group",
            description = "Matches the largest debtor against the largest creditor and repeats, "
                    + "which clears every balance in at most one payment per member minus one. "
                    + "Amounts are piastres and always positive; the direction is carried by the "
                    + "two ids. A settled group returns an empty array. The result is a snapshot: "
                    + "adding an expense or paying a settlement changes it, so re-read rather "
                    + "than cache it.")
    @ApiResponse(responseCode = "200", description = "Suggested payments, largest first")
    @ApiResponse(responseCode = "400", description = "The group id is not a positive number",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No group with this id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/groups/{groupId}/settlements/suggested")
    public List<SuggestedSettlementResponse> suggestSettlements(
            @Parameter(description = "Id of the group", example = "1")
            @PathVariable @Positive(message = "must be a positive id") Long groupId) {
        return settlementService.suggestSettlements(groupId);
    }

    @Operation(
            summary = "Record a settlement",
            description = "Stores a payment between two members of the group as PENDING, whether "
                    + "it came from the suggested list or was agreed some other way. Balances do "
                    + "not move yet: agreeing to pay is not paying. The amount is deliberately "
                    + "not checked against what is currently owed, so instalments and round-number "
                    + "payments are both allowed. Both parties must be members of the group.")
    @ApiResponse(responseCode = "201", description = "Settlement recorded as PENDING")
    @ApiResponse(responseCode = "400",
            description = "Validation failed, the two ids are the same, or a party is not a group member",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No group with this id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/api/groups/{groupId}/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementResponse createSettlement(
            @Parameter(description = "Id of the group", example = "1")
            @PathVariable @Positive(message = "must be a positive id") Long groupId,
            @Valid @RequestBody CreateSettlementRequest request) {
        return settlementService.createSettlement(groupId, request);
    }

    @Operation(
            summary = "Mark a settlement paid",
            description = "Sets the status to PAID and stamps paidAt. From this point the balance "
                    + "engine nets the amount out as though it were an expense in the opposite "
                    + "direction: the payer is credited and the recipient debited, so the group "
                    + "moves towards settled without any expense being altered. Only the payer or "
                    + "the recipient may do this, and only once -- a settlement that is already "
                    + "PAID is a conflict, not a repeat success.")
    @ApiResponse(responseCode = "200", description = "Settlement marked paid")
    @ApiResponse(responseCode = "400", description = "The id or the userId is missing or not positive",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "The caller is neither the payer nor the recipient",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No settlement with this id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The settlement was already marked paid",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/api/settlements/{settlementId}/pay")
    public SettlementResponse markPaid(
            @Parameter(description = "Id of the settlement", example = "1")
            @PathVariable @Positive(message = "must be a positive id") Long settlementId,
            @Valid @RequestBody MarkSettlementPaidRequest request) {
        return settlementService.markPaid(settlementId, request);
    }
}
