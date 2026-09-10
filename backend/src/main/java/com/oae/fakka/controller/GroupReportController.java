package com.oae.fakka.controller;

import com.oae.fakka.dto.ErrorResponse;
import com.oae.fakka.exception.UnsupportedReportFormatException;
import com.oae.fakka.service.ExpenseReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Locale;

/**
 * Exporting a group expense report (FR-36, FR-37).
 *
 * <h2>Streamed, not built</h2>
 * The response is a {@link StreamingResponseBody}: rows are read and written in chunks, so the
 * size of the file is bounded by the group history rather than by memory. That has one
 * consequence worth stating -- the status code is committed before the body runs, so anything
 * that could fail has to be checked first. The group is validated in this method, while a 404 is
 * still possible; a failure after the first byte can only truncate the download.
 *
 * <h2>Amounts are EGP in the file</h2>
 * Every other endpoint speaks piastres. The CSV does not, because it is read by a person in a
 * spreadsheet rather than parsed by the client. See {@link ExpenseReportService} for the
 * reasoning and the exactness of the conversion.
 *
 * <h2>No authentication, so no privacy</h2>
 * This hands out the complete financial history of a group to anyone who asks. With real auth it
 * must be restricted to members.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/export")
@Validated
@Tag(name = "Reports", description = "Export a group expense report")
public class GroupReportController {

    /** The only format so far. FR-37 prioritises CSV; PDF is a later, separate writer. */
    private static final String CSV_FORMAT = "csv";

    private final ExpenseReportService expenseReportService;

    public GroupReportController(ExpenseReportService expenseReportService) {
        this.expenseReportService = expenseReportService;
    }

    @Operation(
            summary = "Export the group expenses as CSV",
            description = "One row per participant share -- a three-person expense produces three "
                    + "rows -- with a per-member summary appended after a blank line. Amounts are "
                    + "in EGP with two decimals, unlike the rest of this API, because the file is "
                    + "meant to be opened in a spreadsheet. The summary carries settledOut and "
                    + "settledIn alongside totalPaid and totalOwed, so that netBalance can be "
                    + "checked against the other columns. The response is streamed, so a long "
                    + "history does not have to be assembled first.")
    @ApiResponse(responseCode = "200", description = "The CSV report",
            content = @Content(mediaType = "text/csv"))
    @ApiResponse(responseCode = "400", description = "The group id is invalid, or the format is not csv",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No group with this id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public ResponseEntity<StreamingResponseBody> export(
            @Parameter(description = "Id of the group", example = "1")
            @PathVariable @Positive(message = "must be a positive id") Long groupId,

            @Parameter(description = "Only csv is supported", example = "csv")
            @RequestParam(defaultValue = CSV_FORMAT) String format) {

        requireCsv(format);

        /*
         * Both checks happen here, before the body is handed back: once streaming starts the
         * status is already sent, so a 404 discovered later could not be reported.
         */
        expenseReportService.requireReportableGroup(groupId);

        StreamingResponseBody body = outputStream ->
                expenseReportService.writeCsv(groupId, outputStream);

        return ResponseEntity.ok()
                .contentType(new MediaType("text", CSV_FORMAT, java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"group-%d-expenses.csv\"".formatted(groupId))
                /*
                 * A streamed body has no known length, so nothing downstream should try to cache
                 * or range-request it, and a stale report is worse than a slow one.
                 */
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    /**
     * Compared case-insensitively rather than bound to an enum: {@code format=CSV} and
     * {@code format=csv} are the same request as far as anyone typing a URL is concerned, and
     * Spring enum binding is case-sensitive.
     */
    private static void requireCsv(String format) {
        if (!CSV_FORMAT.equals(format.trim().toLowerCase(Locale.ROOT))) {
            throw new UnsupportedReportFormatException(format);
        }
    }
}
