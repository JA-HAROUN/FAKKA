package com.oae.fakka.service;

import com.oae.fakka.dto.MemberBalanceResponse;
import com.oae.fakka.entity.User;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.repository.ExpenseRepository;
import com.oae.fakka.repository.ExpenseShareRow;
import com.oae.fakka.repository.GroupMemberRepository;
import com.oae.fakka.repository.GroupRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The group expense report as CSV (FR-36, FR-37).
 *
 * <h2>Streamed, and why that shapes the queries</h2>
 * Rows are written to the response as they are read, so a group with a year of expenses does not
 * have to fit in memory first. Two consequences drive the design:
 * <ul>
 *   <li><strong>Nothing lazy crosses the boundary.</strong> The rows come back as a flat
 *       projection, not as entities, because {@code spring.jpa.open-in-view=false} means the
 *       persistence context is long gone by the time the response body is written. A projection
 *       has nothing left to load.</li>
 *   <li><strong>Each chunk is its own read.</strong> The repository is called once per chunk
 *       while writing, and a Spring Data repository call is transactional in its own right, so
 *       no transaction has to be held open across the whole download.</li>
 * </ul>
 *
 * <h2>Amounts are EGP here, not piastres</h2>
 * The one place in this application that departs from minor units, and deliberately: a CSV is
 * opened by a person in a spreadsheet, where 350.00 is the number they are checking and 35000 is
 * a puzzle. The conversion is exact -- {@code BigDecimal.valueOf(piastres, 2)}, no division and
 * no rounding mode -- and it happens only on the way out, so nothing is ever added up in
 * decimal.
 *
 * <h2>Two hazards a spreadsheet export has to handle</h2>
 * Descriptions and names are free text from users, so every field is escaped per RFC 4180, and
 * anything that a spreadsheet would read as a formula is neutralised first -- a description of
 * {@code =SUM(A1:A9)} belongs in a cell as text, not as a calculation.
 */
@Slf4j
@Service
public class ExpenseReportService {

    /**
     * Rows per read. Big enough that the round trips are not the bottleneck, small enough that
     * one chunk in memory is never a problem.
     */
    private static final int CHUNK_SIZE = 500;

    private static final String EXPENSE_HEADER =
            "date,category,description,payer,participant,shareAmount,expenseTotal";

    /**
     * The summary carries the two settlement columns as well as the four the report was asked
     * for, because without them {@code netBalance} would not equal {@code totalPaid} minus
     * {@code totalOwed} for anybody who has settled up, and a report whose arithmetic cannot be
     * checked is worse than one with an extra column.
     */
    private static final String SUMMARY_HEADER =
            "member,totalPaid,totalOwed,settledOut,settledIn,netBalance";

    /*
     * Dates are rendered in UTC, matching every timestamp this API returns. Worth knowing that a
     * late-night expense in Cairo therefore reports the previous day; rendering in Africa/Cairo
     * instead is a one-line change here if that matters more than consistency with the API.
     */
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    /** The characters a spreadsheet treats as the start of a formula rather than as text. */
    private static final String FORMULA_STARTERS = "=+-@\t\r";

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ExpenseRepository expenseRepository;
    private final BalanceService balanceService;

    public ExpenseReportService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            ExpenseRepository expenseRepository,
            BalanceService balanceService) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.expenseRepository = expenseRepository;
        this.balanceService = balanceService;
    }

    /**
     * Checks the group exists before anything is written.
     * <p>
     * Called by the controller while it can still choose a status code: once the first byte of a
     * streamed 200 has left, a 404 can no longer be sent, so the existence check cannot wait
     * until the writing starts.
     */
    public void requireReportableGroup(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group", groupId);
        }
    }

    /**
     * Writes the whole report: one row per participant share, then a summary per member.
     * <p>
     * Deliberately takes an {@link OutputStream} rather than anything from the web layer, so the
     * format can be tested by writing to a buffer and read back as text.
     */
    public void writeCsv(Long groupId, OutputStream outputStream) throws IOException {
        requireReportableGroup(groupId);

        Map<Long, String> memberNames = memberNamesOf(groupId);

        /*
         * Not closed here: closing would close the response stream, and the container owns that.
         * Flushing per chunk is what actually gets bytes to the client while the rest is read.
         */
        Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

        /*
         * A UTF-8 byte order mark, purely for Excel: without it Excel reads the file as the local
         * codepage and renders any non-ASCII name as mojibake. Most other parsers tolerate it;
         * drop this line if the consumer is a script rather than a person.
         */
        writer.write('﻿');

        long rowsWritten = writeExpenseRows(groupId, memberNames, writer);
        writeSummary(groupId, writer);
        writer.flush();

        log.info("Exported {} expense share rows for group id={}", rowsWritten, groupId);
    }

    /** One row per participant share, oldest first, read a chunk at a time. */
    private long writeExpenseRows(Long groupId, Map<Long, String> memberNames, Writer writer)
            throws IOException {

        writeLine(writer, EXPENSE_HEADER);

        long rowsWritten = 0;
        int pageNumber = 0;
        Slice<ExpenseShareRow> chunk;

        do {
            chunk = expenseRepository.findShareRowsInGroup(
                    groupId, PageRequest.of(pageNumber, CHUNK_SIZE));

            for (ExpenseShareRow row : chunk) {
                writeLine(writer, String.join(",",
                        DATE_FORMAT.format(row.getCreatedAt()),
                        field(row.getCategory().name()),
                        field(row.getDescription()),
                        field(nameOf(memberNames, row.getPayerUserId())),
                        field(nameOf(memberNames, row.getParticipantUserId())),
                        egp(row.getShareAmount()),
                        egp(row.getExpenseTotal())));
                rowsWritten++;
            }

            // Push this chunk to the client before reading the next one.
            writer.flush();
            pageNumber++;
        } while (chunk.hasNext());

        return rowsWritten;
    }

    /**
     * The per-member totals, after a blank line so a reader can see where the rows stop.
     * <p>
     * Taken from {@link BalanceService} rather than summed from the rows above: the balance
     * engine already knows, and a report that added its own numbers up could disagree with the
     * dashboard the same person just looked at.
     */
    private void writeSummary(Long groupId, Writer writer) throws IOException {
        writeLine(writer, "");
        writeLine(writer, SUMMARY_HEADER);

        for (MemberBalanceResponse balance : balanceService.listMemberBalances(groupId)) {
            writeLine(writer, String.join(",",
                    field(balance.name()),
                    egp(balance.paid()),
                    egp(balance.owed()),
                    egp(balance.settledOut()),
                    egp(balance.settledIn()),
                    egp(balance.net())));
        }
    }

    private Map<Long, String> memberNamesOf(Long groupId) {
        /*
         * Loaded once for the whole report rather than joined on every row: a group is at most a
         * hundred people, and the join would repeat those names thousands of times.
         */
        return groupMemberRepository.findMembersOf(groupId).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (first, second) -> first));
    }

    /**
     * The name behind an id, or the id itself if the person is somehow no longer a member. An
     * export that dropped a row would be worse than one with a number in it.
     */
    private static String nameOf(Map<Long, String> memberNames, Long userId) {
        return memberNames.getOrDefault(userId, "user " + userId);
    }

    /** Piastres as an exact EGP amount, which is what a spreadsheet reader expects to see. */
    private static String egp(long piastres) {
        return BigDecimal.valueOf(piastres, 2).toPlainString();
    }

    /**
     * One CSV field: formula-neutralised, then quoted if it contains anything that would break
     * the row apart.
     */
    private static String field(String value) {
        String safe = neutraliseFormula(value == null ? "" : value);

        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }

    /**
     * Stops a spreadsheet from evaluating user text. A leading apostrophe is the conventional
     * fix: Excel and LibreOffice both read the rest as a literal string, and it survives a
     * round trip through a plain CSV parser as a visible character rather than a live formula.
     */
    private static String neutraliseFormula(String value) {
        if (!value.isEmpty() && FORMULA_STARTERS.indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    private static void writeLine(Writer writer, String line) throws IOException {
        // CRLF, because RFC 4180 says so and because Excel is the likeliest reader.
        writer.write(line);
        writer.write("\r\n");
    }

    /** Package-private so the report tests can assert the escaping rules directly. */
    static String escapeField(String value) {
        return field(value);
    }
}
