package com.oae.fakka.controller;

import com.oae.fakka.dto.ErrorResponse;
import com.oae.fakka.dto.SettlementResponse;
import com.oae.fakka.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Who should pay whom to clear a group (FR-32, FR-33).
 * <p>
 * The path says {@code suggested} because that is all these are: a computed proposal, recomputed
 * on every read. Nothing is stored, so nothing can be marked paid yet -- that needs the
 * settlement record from FR-34 and FR-35, and when it arrives it belongs behind a POST here
 * rather than in this GET.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/settlements")
@Validated
@Tag(name = "Settlements", description = "Suggested payments to settle a group")
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
                    + "adding an expense changes it, so it should be re-read rather than cached.")
    @ApiResponse(responseCode = "200", description = "Suggested payments, largest first")
    @ApiResponse(responseCode = "400", description = "The group id is not a positive number",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No group with this id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/suggested")
    public List<SettlementResponse> suggestSettlements(
            @Parameter(description = "Id of the group", example = "1")
            @PathVariable @Positive(message = "must be a positive id") Long groupId) {
        return settlementService.suggestSettlements(groupId);
    }
}
