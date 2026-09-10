package com.oae.fakka.controller;

import com.oae.fakka.dto.ErrorResponse;
import com.oae.fakka.dto.MemberBalanceResponse;
import com.oae.fakka.service.BalanceService;
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
 * Who stands where in a group (FR-11, FR-31).
 * <p>
 * Read-only and derived: there is nothing to store here, because a balance is a view over the
 * expenses and changes the moment one is added. Amounts are piastres, as everywhere else.
 * <p>
 * <strong>No authentication, so no privacy.</strong> Any caller can read any group financial
 * position, which is more sensitive than the rest of this API. With real auth this must be
 * restricted to members of the group.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/balances")
@Validated
@Tag(name = "Balances", description = "Per-member balances in a group")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @Operation(
            summary = "Break down the balances in a group",
            description = "One entry per member, ordered by name: what they paid as payer, what "
                    + "they owe as a participant, and the net of the two (BR-4). Members with no "
                    + "expenses appear with zeros rather than being left out. The net values "
                    + "always sum to zero across the group (BR-5).")
    @ApiResponse(responseCode = "200", description = "One balance per member")
    @ApiResponse(responseCode = "400", description = "The group id is not a positive number",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No group with this id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public List<MemberBalanceResponse> listBalances(
            @Parameter(description = "Id of the group", example = "1")
            @PathVariable @Positive(message = "must be a positive id") Long groupId) {
        return balanceService.listMemberBalances(groupId);
    }
}
