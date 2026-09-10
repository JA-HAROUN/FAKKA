package com.oae.fakka.controller;

import com.jayway.jsonpath.JsonPath;
import com.oae.fakka.entity.Friendship;
import com.oae.fakka.entity.User;
import com.oae.fakka.repository.FriendshipRepository;
import com.oae.fakka.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The aggregated group dashboard against the real schema.
 * <p>
 * The composition is unit tested with the four services mocked; this is where the actual queries
 * run -- the paged expense finder and the batched share lookup -- and where the sections are
 * checked against each other, so that a balance shown beside a settlement cannot disagree with it.
 * <p>
 * The CSV export lives in {@code GroupExportTest} rather than here, because it cannot be tested
 * inside a rolled-back transaction: see the note on that class.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class GroupReportingTest {

    private static final String DUMMY_HASH = "$2a$10$" + "x".repeat(53);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    private User ahmed;
    private User mohamed;
    private User zeinab;
    private long groupId;

    @BeforeEach
    void createAGroupOfThree() throws Exception {
        ahmed = createUser("Ahmed Ragy", "ahmed@example.com");
        mohamed = createUser("Mohamed Salah", "mohamed@example.com");
        zeinab = createUser("Zeinab Hassan", "zeinab@example.com");
        befriend(ahmed, mohamed);
        befriend(ahmed, zeinab);
        groupId = createGroup(ahmed, List.of(mohamed, zeinab));
    }

    @Test
    void theDashboardCarriesEverySectionForARealGroup() throws Exception {
        recordExpense(ahmed, 90_000L, "Dinner", List.of(ahmed, mohamed, zeinab));
        long settlementId = createSettlement(mohamed, ahmed, 30_000L);

        mockMvc.perform(get("/api/groups/{groupId}/dashboard", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group.id").value(groupId))
                .andExpect(jsonPath("$.group.name").value("Dinner"))
                .andExpect(jsonPath("$.group.memberCount").value(3))
                .andExpect(jsonPath("$.members.length()").value(3))
                // Ordered by name, and every member present.
                .andExpect(jsonPath("$.members[0].name").value("Ahmed Ragy"))
                .andExpect(jsonPath("$.members[0].net").value(60000))
                .andExpect(jsonPath("$.members[1].net").value(-30000))
                .andExpect(jsonPath("$.totalGroupExpenses").value(90000))
                .andExpect(jsonPath("$.expenses.content.length()").value(1))
                .andExpect(jsonPath("$.expenses.content[0].description").value("Dinner"))
                // The shares come back with the expense, from the batched lookup.
                .andExpect(jsonPath("$.expenses.content[0].participants.length()").value(3))
                .andExpect(jsonPath("$.expenses.content[0].participants[0].shareAmount").value(30000))
                .andExpect(jsonPath("$.suggestedSettlements.length()").value(2))
                // The pending row is listed but has not moved any balance above.
                .andExpect(jsonPath("$.pendingSettlements.length()").value(1))
                .andExpect(jsonPath("$.pendingSettlements[0].id").value(settlementId))
                .andExpect(jsonPath("$.pendingSettlements[0].status").value("PENDING"));
    }

    /** Paying the settlement has to change the balances and the suggestions in the same read. */
    @Test
    void theDashboardReflectsAPaidSettlement() throws Exception {
        recordExpense(ahmed, 90_000L, "Dinner", List.of(ahmed, mohamed, zeinab));
        long settlementId = createSettlement(mohamed, ahmed, 30_000L);
        markPaid(settlementId, mohamed);

        mockMvc.perform(get("/api/groups/{groupId}/dashboard", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[1].name").value("Mohamed Salah"))
                .andExpect(jsonPath("$.members[1].settledOut").value(30000))
                .andExpect(jsonPath("$.members[1].net").value(0))
                .andExpect(jsonPath("$.members[1].status").value("SETTLED"))
                // One debt left, and nothing still pending.
                .andExpect(jsonPath("$.suggestedSettlements.length()").value(1))
                .andExpect(jsonPath("$.pendingSettlements.length()").value(0));
    }

    /** The expense list is a page; the total beside it is not. */
    @Test
    void onlyTheExpenseListIsPaginated() throws Exception {
        recordExpense(ahmed, 10_000L, "First", List.of(ahmed, mohamed));
        recordExpense(ahmed, 20_000L, "Second", List.of(ahmed, mohamed));
        recordExpense(ahmed, 30_000L, "Third", List.of(ahmed, mohamed));

        mockMvc.perform(get("/api/groups/{groupId}/dashboard", groupId)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenses.content.length()").value(2))
                // Newest first.
                .andExpect(jsonPath("$.expenses.content[0].description").value("Third"))
                .andExpect(jsonPath("$.expenses.content[1].description").value("Second"))
                .andExpect(jsonPath("$.expenses.totalElements").value(3))
                .andExpect(jsonPath("$.expenses.totalPages").value(2))
                .andExpect(jsonPath("$.totalGroupExpenses").value(60000));

        mockMvc.perform(get("/api/groups/{groupId}/dashboard", groupId)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenses.content.length()").value(1))
                .andExpect(jsonPath("$.expenses.content[0].description").value("First"))
                .andExpect(jsonPath("$.totalGroupExpenses").value(60000));
    }

    @Test
    void aBrandNewGroupHasAValidEmptyDashboard() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/dashboard", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group.memberCount").value(3))
                .andExpect(jsonPath("$.members.length()").value(3))
                .andExpect(jsonPath("$.members[0].net").value(0))
                .andExpect(jsonPath("$.totalGroupExpenses").value(0))
                .andExpect(jsonPath("$.expenses.content.length()").value(0))
                .andExpect(jsonPath("$.expenses.totalElements").value(0))
                .andExpect(jsonPath("$.suggestedSettlements.length()").value(0))
                .andExpect(jsonPath("$.pendingSettlements.length()").value(0));
    }

    @Test
    void theDashboardOfAnUnknownGroupIsA404() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/dashboard", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Group 999999 was not found"));
    }

    /** Expenses from another group must not leak into this dashboard. */
    @Test
    void theDashboardIsScopedToOneGroup() throws Exception {
        long otherGroupId = createGroup(ahmed, List.of(mohamed));
        recordExpense(ahmed, 90_000L, "Dinner", List.of(ahmed, mohamed, zeinab));

        mockMvc.perform(get("/api/groups/{groupId}/dashboard", otherGroupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGroupExpenses").value(0))
                .andExpect(jsonPath("$.expenses.content.length()").value(0));
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
