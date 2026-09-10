package com.oae.fakka.controller;

import com.jayway.jsonpath.JsonPath;
import com.oae.fakka.entity.Friendship;
import com.oae.fakka.entity.User;
import com.oae.fakka.repository.ExpenseParticipantRepository;
import com.oae.fakka.repository.ExpenseRepository;
import com.oae.fakka.repository.FriendshipRepository;
import com.oae.fakka.repository.GroupMemberRepository;
import com.oae.fakka.repository.GroupRepository;
import com.oae.fakka.repository.SettlementRepository;
import com.oae.fakka.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The CSV export over HTTP, against real data (FR-36, FR-37).
 *
 * <h2>Why this one commits instead of rolling back</h2>
 * Every other integration test here is {@code @Transactional} and rolls back. This one cannot be:
 * a {@link org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody} is written
 * on a different thread from the request, so it can only see data that has been committed. Under
 * a rolled-back test transaction the export would correctly find nothing and report a 404 --
 * which is exactly what happened the first time these were written the usual way.
 * <p>
 * That is also the point of testing it like this. The design claim in
 * {@code ExpenseReportService} is that a report can be streamed after the request transaction is
 * gone, because each chunk is its own repository call. Only a committing test proves it.
 * <p>
 * The cost is that cleanup is manual, and it matters: other tests create the same email
 * addresses, and a leftover row here would fail them with a duplicate-email conflict.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class GroupExportTest {

    private static final String DUMMY_HASH = "$2a$10$" + "x".repeat(53);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseParticipantRepository expenseParticipantRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    private User ahmed;
    private User mohamed;
    private User zeinab;
    private long groupId;

    @BeforeEach
    void createAGroupOfThree() throws Exception {
        deleteEverything();
        ahmed = createUser("Ahmed Ragy", "export-ahmed@example.com");
        mohamed = createUser("Mohamed Salah", "export-mohamed@example.com");
        zeinab = createUser("Zeinab Hassan", "export-zeinab@example.com");
        befriend(ahmed, mohamed);
        befriend(ahmed, zeinab);
        groupId = createGroup(ahmed, List.of(mohamed, zeinab));
    }

    /** Always runs, including after a failure, so a broken test cannot poison the others. */
    @AfterEach
    void tearDown() {
        deleteEverything();
    }

    /** A three-person expense is three rows, and the summary follows after a blank line. */
    @Test
    void theExportWritesOneRowPerShareAndThenASummary() throws Exception {
        recordExpense(ahmed, 90_000L, "Dinner at Pizza Hut", List.of(ahmed, mohamed, zeinab));

        String[] lines = exportCsv().split("\r\n", -1);

        assertThat(lines[0])
                .isEqualTo("date,category,description,payer,participant,shareAmount,expenseTotal");
        assertThat(lines[1]).endsWith("FOOD,Dinner at Pizza Hut,Ahmed Ragy,Ahmed Ragy,300.00,900.00");
        assertThat(lines[2]).endsWith("FOOD,Dinner at Pizza Hut,Ahmed Ragy,Mohamed Salah,300.00,900.00");
        assertThat(lines[3]).endsWith("FOOD,Dinner at Pizza Hut,Ahmed Ragy,Zeinab Hassan,300.00,900.00");
        assertThat(lines[4]).isEmpty();
        assertThat(lines[5]).isEqualTo("member,totalPaid,totalOwed,settledOut,settledIn,netBalance");
        assertThat(lines[6]).isEqualTo("Ahmed Ragy,900.00,300.00,0.00,0.00,600.00");
        assertThat(lines[7]).isEqualTo("Mohamed Salah,0.00,300.00,0.00,0.00,-300.00");
        assertThat(lines[8]).isEqualTo("Zeinab Hassan,0.00,300.00,0.00,0.00,-300.00");
    }

    @Test
    void theExportIsAnAttachedCsvFileNamedForTheGroup() throws Exception {
        recordExpense(ahmed, 90_000L, "Dinner", List.of(ahmed, mohamed));

        MvcResult started = mockMvc.perform(get("/api/groups/{groupId}/export", groupId))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"group-%d-expenses.csv\"".formatted(groupId)))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    /** Several expenses export oldest first, because a report reads as a history. */
    @Test
    void theExportListsExpensesOldestFirst() throws Exception {
        recordExpense(ahmed, 10_000L, "First", List.of(ahmed));
        recordExpense(mohamed, 20_000L, "Second", List.of(mohamed));

        String[] lines = exportCsv().split("\r\n", -1);

        assertThat(lines[1]).contains("First");
        assertThat(lines[2]).contains("Second");
    }

    /** A comma in a real description must survive the round trip as one field. */
    @Test
    void theExportQuotesADescriptionWithACommaInIt() throws Exception {
        recordExpense(ahmed, 30_000L, "Pizza, salad and cola", List.of(ahmed));

        assertThat(exportCsv()).contains("\"Pizza, salad and cola\"");
    }

    /** The summary is the balance engine numbers, so it must agree with the balances endpoint. */
    @Test
    void theExportSummaryAgreesWithTheBalancesEndpoint() throws Exception {
        recordExpense(ahmed, 90_000L, "Dinner", List.of(ahmed, mohamed, zeinab));
        markPaid(createSettlement(mohamed, ahmed, 30_000L), mohamed);

        String balances = mockMvc.perform(get("/api/groups/{groupId}/balances", groupId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Mohamed is the second member by name, and has settled his share.
        long netFromApi = ((Number) JsonPath.parse(balances).read("$[1].net")).longValue();

        assertThat(netFromApi).isZero();
        assertThat(exportCsv()).contains("Mohamed Salah,0.00,300.00,300.00,0.00,0.00");
    }

    /** Empty group, valid file: the headers and a zeroed summary are still worth exporting. */
    @Test
    void anEmptyGroupExportsHeadersAndZeros() throws Exception {
        String[] lines = exportCsv().split("\r\n", -1);

        assertThat(lines[0]).startsWith("date,category");
        assertThat(lines[1]).isEmpty();
        assertThat(lines[2]).startsWith("member,totalPaid");
        assertThat(lines[3]).isEqualTo("Ahmed Ragy,0.00,0.00,0.00,0.00,0.00");
    }

    /**
     * More rows than one chunk holds, so the report has to come back for the rest. This is the
     * case that would silently truncate if the chunking loop were wrong, and it exercises it
     * against the real query rather than a stubbed slice.
     */
    @Test
    void aReportLongerThanOneChunkIsCompleteAnyway() throws Exception {
        // 260 expenses of two shares each is 520 rows, past the 500-row chunk.
        for (int index = 0; index < 260; index++) {
            recordExpense(ahmed, 200L, "Expense " + index, List.of(ahmed, mohamed));
        }

        String[] lines = exportCsv().split("\r\n", -1);

        // 1 header + 520 rows + blank + summary header + 3 members + trailing empty element.
        assertThat(lines).hasSize(527);
        assertThat(lines[520]).contains("Expense 259");
        assertThat(lines[521]).isEmpty();
        assertThat(lines[523]).isEqualTo("Ahmed Ragy,520.00,260.00,0.00,0.00,260.00");
    }

    @Test
    void exportingAnUnknownGroupIsA404() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/export", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Group 999999 was not found"));
    }

    @Test
    void exportingInAnUnsupportedFormatIsA400() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/export", groupId).param("format", "pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Export format pdf is not supported; the only supported format is csv"));
    }

    /** The report covers one group only, whatever else is in the database. */
    @Test
    void theExportIsScopedToOneGroup() throws Exception {
        long otherGroupId = createGroup(ahmed, List.of(mohamed));
        recordExpense(ahmed, 90_000L, "Dinner", List.of(ahmed, mohamed, zeinab));

        MvcResult started = mockMvc.perform(get("/api/groups/{groupId}/export", otherGroupId))
                .andExpect(request().asyncStarted())
                .andReturn();
        String csv = mockMvc.perform(asyncDispatch(started))
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).doesNotContain("Dinner");
        assertThat(csv).contains("Ahmed Ragy,0.00,0.00,0.00,0.00,0.00");
    }

    /** The byte order mark is stripped here so the line assertions stay readable. */
    private String exportCsv() throws Exception {
        MvcResult started = mockMvc.perform(get("/api/groups/{groupId}/export", groupId))
                .andExpect(request().asyncStarted())
                .andReturn();

        return mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .replace("﻿", "");
    }

    /** Children first, though nothing here has a foreign key to complain about. */
    private void deleteEverything() {
        settlementRepository.deleteAll();
        expenseParticipantRepository.deleteAll();
        expenseRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        friendshipRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createUser(String name, String email) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .passwordHash(DUMMY_HASH)
                .build());
    }

    private void befriend(User first, User second) {
        friendshipRepository.saveAllAndFlush(List.of(
                Friendship.builder().userId(first.getId()).friendId(second.getId()).build(),
                Friendship.builder().userId(second.getId()).friendId(first.getId()).build()));
    }

    private long createGroup(User creator, List<User> members) throws Exception {
        String ids = members.stream()
                .map(member -> member.getId().toString())
                .collect(Collectors.joining(","));

        String response = mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":%d,"name":"Dinner","memberUserIds":[%s]}
                                """.formatted(creator.getId(), ids)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.parse(response).read("$.id", Integer.class).longValue();
    }

    private void recordExpense(User payer, long totalAmount, String description, List<User> participants)
            throws Exception {

        String ids = participants.stream()
                .map(participant -> participant.getId().toString())
                .collect(Collectors.joining(","));

        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":%d,"category":"FOOD","description":"%s",
                                 "totalAmount":%d,"participantUserIds":[%s]}
                                """.formatted(payer.getId(), description, totalAmount, ids)))
                .andExpect(status().isCreated());
    }

    private long createSettlement(User from, User to, long amount) throws Exception {
        String response = mockMvc.perform(post("/api/groups/{groupId}/settlements", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromUserId":%d,"toUserId":%d,"amount":%d}
                                """.formatted(from.getId(), to.getId(), amount)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.parse(response).read("$.id", Integer.class).longValue();
    }

    private void markPaid(long settlementId, User claimant) throws Exception {
        mockMvc.perform(patch("/api/settlements/{settlementId}/pay", settlementId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d}
                                """.formatted(claimant.getId())))
                .andExpect(status().isOk());
    }
}
