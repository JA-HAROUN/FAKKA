package com.oae.fakka.service;

import com.oae.fakka.dto.BalanceStatus;
import com.oae.fakka.dto.MemberBalanceResponse;
import com.oae.fakka.entity.ExpenseCategory;
import com.oae.fakka.entity.User;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.repository.ExpenseRepository;
import com.oae.fakka.repository.ExpenseShareRow;
import com.oae.fakka.repository.GroupMemberRepository;
import com.oae.fakka.repository.GroupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The CSV export (FR-36, FR-37), written to a buffer and read back as text.
 * <p>
 * Writing to an {@link java.io.OutputStream} rather than through the web layer is what makes the
 * format itself testable: these assert the exact bytes a spreadsheet will open, including the
 * things that are easy to get wrong and invisible until somebody opens the file -- a description
 * with a comma in it, a name that starts with an equals sign, and amounts that have to read as
 * EGP rather than as piastres.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseReportServiceTest {

    private static final long GROUP_ID = 10L;
    private static final Instant DINNER_AT = Instant.parse("2026-09-10T19:30:00Z");
    private static final Instant UBER_AT = Instant.parse("2026-09-11T02:15:00Z");

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private BalanceService balanceService;

    @InjectMocks
    private ExpenseReportService expenseReportService;

    @Test
    void anUnknownGroupIsRefusedBeforeAnythingIsWritten() {
        given(groupRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> expenseReportService.requireReportableGroup(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Group 99 was not found");

        verifyNoInteractions(expenseRepository, balanceService);
    }

    /** One row per participant share: a three-way expense is three lines, not one. */
    @Test
    void everyParticipantShareGetsItsOwnRow() throws Exception {
        givenGroupExists();
        givenMembers();
        givenShareRows(
                row(DINNER_AT, ExpenseCategory.FOOD, "Dinner", 1L, 1L, 30_000L, 90_000L),
                row(DINNER_AT, ExpenseCategory.FOOD, "Dinner", 1L, 2L, 30_000L, 90_000L),
                row(DINNER_AT, ExpenseCategory.FOOD, "Dinner", 1L, 3L, 30_000L, 90_000L));
        givenBalances();

        List<String> lines = exportLines();

        assertThat(lines.get(0)).isEqualTo(
                "date,category,description,payer,participant,shareAmount,expenseTotal");
        assertThat(lines.subList(1, 4)).containsExactly(
                "2026-09-10,FOOD,Dinner,Ahmed Ragy,Ahmed Ragy,300.00,900.00",
                "2026-09-10,FOOD,Dinner,Ahmed Ragy,Mohamed Salah,300.00,900.00",
                "2026-09-10,FOOD,Dinner,Ahmed Ragy,Zeinab Hassan,300.00,900.00");
    }

    /** Ids are resolved to names, because a spreadsheet full of numbers tells nobody anything. */
    @Test
    void payerAndParticipantAreWrittenAsNames() throws Exception {
        givenGroupExists();
        givenMembers();
        givenShareRows(row(UBER_AT, ExpenseCategory.TRANSPORTATION, "Uber", 3L, 2L, 5_000L, 5_000L));
        givenBalances();

        assertThat(exportLines().get(1))
                .isEqualTo("2026-09-11,TRANSPORTATION,Uber,Zeinab Hassan,Mohamed Salah,50.00,50.00");
    }

    /**
     * Amounts are EGP in the file even though the API speaks piastres, so the conversion is
     * asserted at both ends of the scale: a single piastre must not disappear, and a round
     * number must not gain a rounding error.
     */
    @ParameterizedTest
    @CsvSource({
            "1, 0.01",
            "99, 0.99",
            "100, 1.00",
            "3333, 33.33",
            "90000, 900.00",
            "100000000000, 1000000000.00",
    })
    void amountsAreWrittenAsExactEgp(long piastres, String expectedEgp) throws Exception {
        givenGroupExists();
        givenMembers();
        givenShareRows(row(DINNER_AT, ExpenseCategory.OTHER, "Thing", 1L, 1L, piastres, piastres));
        givenBalances();

        assertThat(exportLines().get(1)).endsWith("," + expectedEgp + "," + expectedEgp);
    }

    /** A comma in a description must not split the row into two columns. */
    @Test
    void aDescriptionContainingACommaIsQuoted() throws Exception {
        givenGroupExists();
        givenMembers();
        givenShareRows(row(DINNER_AT, ExpenseCategory.FOOD, "Pizza, salad and cola",
                1L, 1L, 30_000L, 30_000L));
        givenBalances();

        assertThat(exportLines().get(1))
                .isEqualTo("2026-09-10,FOOD,\"Pizza, salad and cola\",Ahmed Ragy,Ahmed Ragy,300.00,300.00");
    }

    /** Quotes inside a quoted field are doubled, per RFC 4180. */
    @Test
    void aDescriptionContainingQuotesIsEscaped() throws Exception {
        givenGroupExists();
        givenMembers();
        givenShareRows(row(DINNER_AT, ExpenseCategory.FOOD, "The \"good\" place, apparently",
                1L, 1L, 30_000L, 30_000L));
        givenBalances();

        assertThat(exportLines().get(1))
                .contains("\"The \"\"good\"\" place, apparently\"");
    }

    /** A newline inside a description must stay inside its field rather than end the row. */
    @Test
    void aDescriptionContainingANewlineIsQuoted() throws Exception {
        givenGroupExists();
        givenMembers();
        givenShareRows(row(DINNER_AT, ExpenseCategory.FOOD, "Dinner\nand drinks",
                1L, 1L, 30_000L, 30_000L));
        givenBalances();

        String csv = export();

        assertThat(csv).contains("\"Dinner\nand drinks\"");
        // The header, the one data row, the blank line, the summary header and one summary row.
        assertThat(csv.split("\r\n", -1)).hasSize(6);
    }

    /**
     * A description a spreadsheet would evaluate is neutralised. Without this, exporting a
     * report is enough to run whatever somebody typed into a description field on the machine of
     * whoever opens it.
     */
    @ParameterizedTest
    @ValueSource(strings = {"=SUM(A1:A9)", "+1+1", "-1+1", "@SUM(A1)"})
    void aDescriptionThatLooksLikeAFormulaIsNeutralised(String dangerous) throws Exception {
        givenGroupExists();
        givenMembers();
        givenShareRows(row(DINNER_AT, ExpenseCategory.OTHER, dangerous, 1L, 1L, 100L, 100L));
        givenBalances();

        assertThat(exportLines().get(1))
                .contains(",'" + dangerous.replace(",", "") + "")
                .doesNotContain("," + dangerous.charAt(0));
    }

    /** The summary is a separate section, so a blank line marks where the rows stop. */
    @Test
    void aSummarySectionIsAppendedAfterABlankLine() throws Exception {
        givenGroupExists();
        givenMembers();
        givenShareRows(row(DINNER_AT, ExpenseCategory.FOOD, "Dinner", 1L, 1L, 30_000L, 90_000L));
        given(balanceService.listMemberBalances(GROUP_ID)).willReturn(List.of(
                balance(1L, "Ahmed Ragy", 90_000L, 30_000L, 0L, 0L, 60_000L, BalanceStatus.POSITIVE),
                balance(2L, "Mohamed Salah", 0L, 30_000L, 30_000L, 0L, 0L, BalanceStatus.SETTLED)));

        List<String> lines = exportLines();

        assertThat(lines.get(2)).isEmpty();
        assertThat(lines.get(3)).isEqualTo(
                "member,totalPaid,totalOwed,settledOut,settledIn,netBalance");
        assertThat(lines.subList(4, 6)).containsExactly(
                "Ahmed Ragy,900.00,300.00,0.00,0.00,600.00",
                "Mohamed Salah,0.00,300.00,300.00,0.00,0.00");
    }

    /** The summary numbers have to add up from the columns beside them, or the report lies. */
    @Test
    void theSummaryArithmeticIsCheckableFromItsOwnColumns() throws Exception {
        givenGroupExists();
        givenMembers();
        givenShareRows();
        given(balanceService.listMemberBalances(GROUP_ID)).willReturn(List.of(
                balance(1L, "Ahmed Ragy", 90_000L, 30_000L, 0L, 40_000L, 20_000L,
                        BalanceStatus.POSITIVE)));

        String summaryRow = exportLines().stream()
                .filter(line -> line.startsWith("Ahmed Ragy"))
                .findFirst()
                .orElseThrow();
        String[] columns = summaryRow.split(",");

        double paid = Double.parseDouble(columns[1]);
        double owed = Double.parseDouble(columns[2]);
        double settledOut = Double.parseDouble(columns[3]);
        double settledIn = Double.parseDouble(columns[4]);
        double net = Double.parseDouble(columns[5]);

        assertThat((paid + settledOut) - (owed + settledIn)).isEqualTo(net);
    }

    /** A group with no expenses still exports a valid file: headers and a summary. */
    @Test
    void anEmptyGroupExportsHeadersAndASummary() throws Exception {
        givenGroupExists();
        givenMembers();
        givenShareRows();
        given(balanceService.listMemberBalances(GROUP_ID)).willReturn(List.of(
                balance(1L, "Ahmed Ragy", 0L, 0L, 0L, 0L, 0L, BalanceStatus.SETTLED)));

        List<String> lines = exportLines();

        assertThat(lines.get(0)).startsWith("date,category");
        assertThat(lines.get(1)).isEmpty();
        assertThat(lines.get(2)).startsWith("member,totalPaid");
        assertThat(lines.get(3)).isEqualTo("Ahmed Ragy,0.00,0.00,0.00,0.00,0.00");
    }

    /**
     * The export reads in chunks so that a long history never sits in memory. Two chunks are
     * asserted here because the loop that asks for the second one is the part that would silently
     * truncate a report if it were wrong.
     */
    @Test
    void aHistoryLongerThanOneChunkIsReadInSeveralPasses() throws Exception {
        givenGroupExists();
        givenMembers();
        givenBalances();

        List<ExpenseShareRow> firstChunk = new ArrayList<>();
        for (int index = 0; index < 500; index++) {
            firstChunk.add(row(DINNER_AT, ExpenseCategory.FOOD, "Dinner", 1L, 1L, 100L, 100L));
        }
        given(expenseRepository.findShareRowsInGroup(eq(GROUP_ID), any(Pageable.class)))
                .willReturn(new SliceImpl<>(firstChunk, PageRequest.of(0, 500), true))
                .willReturn(new SliceImpl<>(
                        List.of(row(UBER_AT, ExpenseCategory.TRANSPORTATION, "Uber", 1L, 1L, 100L, 100L)),
                        PageRequest.of(1, 500), false));

        List<String> lines = exportLines();

        /*
         * 1 header + 501 rows + blank line + summary header + 1 member = 505 lines, and one more
         * element because every line ends CRLF, so the split leaves a trailing empty string. The
         * row from the second chunk landing at 501 is what proves the loop asked for it.
         */
        assertThat(lines).hasSize(506);
        assertThat(lines.get(501)).contains("Uber");
        assertThat(lines.getLast()).isEmpty();
    }

    /** Excel needs the byte order mark to read a non-ASCII name correctly. */
    @Test
    void theFileStartsWithAUtf8ByteOrderMark() throws Exception {
        givenGroupExists();
        given(groupMemberRepository.findMembersOf(GROUP_ID))
                .willReturn(List.of(user(1L, "أحمد راجي")));
        givenShareRows(row(DINNER_AT, ExpenseCategory.FOOD, "عشاء", 1L, 1L, 100L, 100L));
        given(balanceService.listMemberBalances(GROUP_ID)).willReturn(List.of(
                balance(1L, "أحمد راجي", 100L, 100L, 0L, 0L, 0L, BalanceStatus.SETTLED)));

        byte[] bytes = exportBytes();

        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("أحمد راجي", "عشاء");
    }

    /** Rows end CRLF, which is what RFC 4180 specifies and what Excel expects. */
    @Test
    void rowsAreTerminatedWithCrLf() throws Exception {
        givenGroupExists();
        givenMembers();
        givenShareRows(row(DINNER_AT, ExpenseCategory.FOOD, "Dinner", 1L, 1L, 100L, 100L));
        givenBalances();

        assertThat(export()).contains("expenseTotal\r\n2026-09-10,FOOD,Dinner");
    }

    /** A participant who has somehow left the group still gets a row rather than vanishing. */
    @Test
    void anUnknownParticipantIdFallsBackToTheId() throws Exception {
        givenGroupExists();
        givenMembers();
        givenShareRows(row(DINNER_AT, ExpenseCategory.FOOD, "Dinner", 1L, 99L, 100L, 100L));
        givenBalances();

        assertThat(exportLines().get(1)).contains(",user 99,");
    }

    private void givenGroupExists() {
        given(groupRepository.existsById(GROUP_ID)).willReturn(true);
    }

    private void givenMembers() {
        given(groupMemberRepository.findMembersOf(GROUP_ID)).willReturn(List.of(
                user(1L, "Ahmed Ragy"), user(2L, "Mohamed Salah"), user(3L, "Zeinab Hassan")));
    }

    private void givenBalances() {
        given(balanceService.listMemberBalances(GROUP_ID)).willReturn(List.of(
                balance(1L, "Ahmed Ragy", 0L, 0L, 0L, 0L, 0L, BalanceStatus.SETTLED)));
    }

    private void givenShareRows(ExpenseShareRow... rows) {
        Slice<ExpenseShareRow> onlyChunk =
                new SliceImpl<>(List.of(rows), PageRequest.of(0, 500), false);
        given(expenseRepository.findShareRowsInGroup(eq(GROUP_ID), any(Pageable.class)))
                .willReturn(onlyChunk);
    }

    private String export() throws IOException {
        return new String(exportBytes(), StandardCharsets.UTF_8);
    }

    private byte[] exportBytes() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        expenseReportService.writeCsv(GROUP_ID, buffer);
        return buffer.toByteArray();
    }

    /** The lines of the report, with the byte order mark stripped so assertions stay readable. */
    private List<String> exportLines() throws IOException {
        String csv = export().replace("﻿", "");
        return List.of(csv.split("\r\n", -1));
    }

    private static User user(Long id, String name) {
        return User.builder()
                .id(id)
                .name(name)
                .email("user%d@example.com".formatted(id))
                .passwordHash("$2a$10$" + "x".repeat(53))
                .build();
    }

    private static MemberBalanceResponse balance(
            Long userId, String name, long paid, long owed,
            long settledOut, long settledIn, long net, BalanceStatus status) {

        return new MemberBalanceResponse(userId, name, null, paid, owed, settledOut, settledIn,
                net, status);
    }

    private static ExpenseShareRow row(
            Instant createdAt,
            ExpenseCategory category,
            String description,
            Long payerUserId,
            Long participantUserId,
            long shareAmount,
            long expenseTotal) {

        return new ExpenseShareRow() {
            @Override
            public Instant getCreatedAt() {
                return createdAt;
            }

            @Override
            public ExpenseCategory getCategory() {
                return category;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public Long getPayerUserId() {
                return payerUserId;
            }

            @Override
            public Long getParticipantUserId() {
                return participantUserId;
            }

            @Override
            public long getShareAmount() {
                return shareAmount;
            }

            @Override
            public long getExpenseTotal() {
                return expenseTotal;
            }
        };
    }
}
