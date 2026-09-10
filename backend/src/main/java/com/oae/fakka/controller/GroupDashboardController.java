package com.oae.fakka.controller;

import com.oae.fakka.dto.ErrorResponse;
import com.oae.fakka.dto.GroupDashboardResponse;
import com.oae.fakka.service.GroupDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The whole group screen in one request (FR-11).
 * <p>
 * A convenience over the endpoints it composes, not a replacement for them: the same numbers are
 * available separately from {@code /members}, {@code /balances}, {@code /expenses} and
 * {@code /settlements}, and a client that only needs one of those should ask for that one.
 * <p>
 * Read-only, so there is no request body and nothing to validate beyond the paging window.
 * <p>
 * <strong>No authentication, so no privacy.</strong> Any caller can read any group financial
 * position, which is the most sensitive thing this API exposes. With real auth this must be
 * restricted to members of the group.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/dashboard")
@Validated
@Tag(name = "Group dashboard", description = "The aggregated group view")
public class GroupDashboardController {

    /** Twenty expenses is a screenful; the cap stops a caller asking for the whole history. */
    private static final String DEFAULT_PAGE_SIZE = "20";

    private final GroupDashboardService groupDashboardService;

    public GroupDashboardController(GroupDashboardService groupDashboardService) {
        this.groupDashboardService = groupDashboardService;
    }

    @Operation(
            summary = "Load the group dashboard",
            description = "Composes the group details, every member balance, the total spent, one "
                    + "page of expenses with their shares, the suggested settlements and the "
                    + "pending ones. Amounts are piastres: 100 piastres = 1 EGP. Only the expense "
                    + "list is paginated, newest first; totalGroupExpenses covers every expense "
                    + "rather than the page. Pending settlements are shown because they are "
                    + "agreed, but they do not affect the balances until they are marked paid.")
    @ApiResponse(responseCode = "200", description = "The aggregated group view")
    @ApiResponse(responseCode = "400", description = "The group id or the paging window is invalid",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No group with this id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public GroupDashboardResponse loadDashboard(
            @Parameter(description = "Id of the group", example = "1")
            @PathVariable @Positive(message = "must be a positive id") Long groupId,

            @Parameter(description = "Zero-based page of the expense list", example = "0")
            @RequestParam(defaultValue = "0")
            @PositiveOrZero(message = "must not be negative") int page,

            @Parameter(description = "Expenses per page, at most 100", example = "20")
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE)
            @Positive(message = "must be at least 1")
            @Max(value = 100, message = "must be at most 100") int size) {

        return groupDashboardService.loadDashboard(groupId, page, size);
    }
}
