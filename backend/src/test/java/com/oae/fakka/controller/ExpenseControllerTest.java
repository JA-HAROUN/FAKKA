package com.oae.fakka.controller;

import com.jayway.jsonpath.JsonPath;
import com.oae.fakka.entity.Expense;
import com.oae.fakka.entity.ExpenseCategory;
import com.oae.fakka.entity.ExpenseParticipant;
import com.oae.fakka.entity.Friendship;
import com.oae.fakka.entity.User;
import com.oae.fakka.repository.ExpenseParticipantRepository;
import com.oae.fakka.repository.ExpenseRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end expense creation against the real schema.
 * <p>
 * The split arithmetic is covered exhaustively in {@code ExpenseSplitServiceTest}; what this adds
 * is that the computed shares actually reach the database, still summing to the total (BR-1),
 * with the participants stored in the order the client sent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ExpenseControllerTest {

    private static final String DUMMY_HASH = "$2a$10$" + "x".repeat(53);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseParticipantRepository expenseParticipantRepository;

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
        groupId = createGroup(ahmed, "Dinner", List.of(mohamed, zeinab));
    }

    /** 100 piastres across three: 34, 33, 33 -- and 34 goes to whoever the client listed first. */
    @Test
    void equalSplitStoresSharesThatSumToTheTotal() throws Exception {
        String response = mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":%d,"category":"FOOD","description":"Dinner at Pizza Hut",
                                 "totalAmount":100,"participantUserIds":[%d,%d,%d],"splitType":"EQUAL"}
                                """.formatted(ahmed.getId(), ahmed.getId(), mohamed.getId(), zeinab.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(100))
                .andExpect(jsonPath("$.category").value("FOOD"))
                .andExpect(jsonPath("$.participants.length()").value(3))
                .andExpect(jsonPath("$.participants[0].userId").value(ahmed.getId()))
                .andExpect(jsonPath("$.participants[0].shareAmount").value(34))
                .andExpect(jsonPath("$.participants[1].shareAmount").value(33))
                .andExpect(jsonPath("$.participants[2].shareAmount").value(33))
                .andReturn().getResponse().getContentAsString();

        long expenseId = JsonPath.parse(response).read("$.id", Integer.class).longValue();
        List<ExpenseParticipant> stored = expenseParticipantRepository.findByExpenseIdOrderByIdAsc(expenseId);

        assertThat(stored)
                .extracting(ExpenseParticipant::getUserId, ExpenseParticipant::getShareAmount)
                .containsExactly(
                        tuple(ahmed.getId(), 34L),
                        tuple(mohamed.getId(), 33L),
                        tuple(zeinab.getId(), 33L));
        assertThat(stored.stream().mapToLong(ExpenseParticipant::getShareAmount).sum())
                .as("BR-1 in the database")
                .isEqualTo(100L);
    }

    @Test
    void expenseIsStoredWithItsCategoryPayerAndImage() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":%d,"category":"ACCOMMODATION","description":"  Hotel  ",
                                 "totalAmount":90000,"imageUrl":"https://img.example.com/receipt.jpg",
                                 "participantUserIds":[%d,%d]}
                                """.formatted(mohamed.getId(), mohamed.getId(), zeinab.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Hotel"))
                .andExpect(jsonPath("$.imageUrl").value("https://img.example.com/receipt.jpg"))
                .andExpect(jsonPath("$.createdAt").exists());

        assertThat(expenseRepository.findAll())
                .singleElement()
                .satisfies(expense -> {
                    assertThat(expense.getGroupId()).isEqualTo(groupId);
                    assertThat(expense.getCategory()).isEqualTo(ExpenseCategory.ACCOMMODATION);
                    assertThat(expense.getPaidByUserId()).isEqualTo(mohamed.getId());
                    assertThat(expense.getTotalAmount()).isEqualTo(90_000L);
                    assertThat(expense.getCreatedAt()).isNotNull();
                });
    }

    /** Every category in FR-13 must round-trip through the varchar column. */
    @Test
    void everyCategoryCanBeStored() throws Exception {
        for (ExpenseCategory category : ExpenseCategory.values()) {
            mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"paidByUserId":%d,"category":"%s","description":"Something",
                                     "totalAmount":1000,"participantUserIds":[%d]}
                                    """.formatted(ahmed.getId(), category, ahmed.getId())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.category").value(category.name()));
        }

        assertThat(expenseRepository.findAll())
                .extracting(Expense::getCategory)
                .containsExactlyInAnyOrder(ExpenseCategory.values());
    }

    @Test
    void customSplitIsStoredAsGivenWhenItSumsToTheTotal() throws Exception {
        String response = mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":%d,"category":"FOOD","description":"Pizza and burger",
                                 "totalAmount":65000,"participantUserIds":[%d,%d],"splitType":"CUSTOM",
                                 "customShares":{"%d":40000,"%d":25000}}
                                """.formatted(ahmed.getId(), ahmed.getId(), mohamed.getId(),
                                ahmed.getId(), mohamed.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participants[0].shareAmount").value(40000))
                .andExpect(jsonPath("$.participants[1].shareAmount").value(25000))
                .andReturn().getResponse().getContentAsString();

        long expenseId = JsonPath.parse(response).read("$.id", Integer.class).longValue();
        assertThat(expenseParticipantRepository.findByExpenseIdOrderByIdAsc(expenseId))
                .extracting(ExpenseParticipant::getShareAmount)
                .containsExactly(40_000L, 25_000L);
    }

    /** BR-1: one piastre out and the whole request is refused, with both numbers named. */
    @Test
    void customSplitThatDoesNotSumToTheTotalIsRejectedAndStoresNothing() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":%d,"category":"FOOD","description":"Pizza",
                                 "totalAmount":65000,"participantUserIds":[%d,%d],"splitType":"CUSTOM",
                                 "customShares":{"%d":40000,"%d":24999}}
                                """.formatted(ahmed.getId(), ahmed.getId(), mohamed.getId(),
                                ahmed.getId(), mohamed.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Shares total 64999 piastres but the expense is 65000 piastres; "
                                + "they must add up exactly"))
                .andExpect(jsonPath("$.path").value("/api/groups/%d/expenses".formatted(groupId)));

        assertThat(expenseRepository.count()).isZero();
        assertThat(expenseParticipantRepository.count()).isZero();
    }

    @Test
    void customSplitMissingAParticipantShareIsRejected() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":%d,"category":"FOOD","description":"Pizza",
                                 "totalAmount":65000,"participantUserIds":[%d,%d],"splitType":"CUSTOM",
                                 "customShares":{"%d":65000}}
                                """.formatted(ahmed.getId(), ahmed.getId(), mohamed.getId(), ahmed.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A custom split must give exactly one share "
                        + "per participant: no share was given for participants [%d]"
                        .formatted(mohamed.getId())));

        assertThat(expenseRepository.count()).isZero();
    }

    /** FR-16: the payer has to be in the group, even though they need not be a participant. */
    @Test
    void aPayerOutsideTheGroupIsRejected() throws Exception {
        User outsider = createUser("Stranger Danger", "stranger@example.com");

        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":%d,"category":"FOOD","description":"Dinner",
                                 "totalAmount":30000,"participantUserIds":[%d]}
                                """.formatted(outsider.getId(), ahmed.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Users [%d] are not members of this group, "
                        .formatted(outsider.getId()) + "so they cannot pay for or share an expense"));

        assertThat(expenseRepository.count()).isZero();
    }

    @Test
    void aParticipantOutsideTheGroupIsRejected() throws Exception {
        User outsider = createUser("Stranger Danger", "stranger@example.com");

        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":%d,"category":"FOOD","description":"Dinner",
                                 "totalAmount":30000,"participantUserIds":[%d,%d]}
                                """.formatted(ahmed.getId(), ahmed.getId(), outsider.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Users [%d] are not members of this group, "
                        .formatted(outsider.getId()) + "so they cannot pay for or share an expense"));
    }

    /**
     * A user id that does not exist gets the same answer as one that exists but is not a member,
     * so expense creation cannot be used to discover which ids are real.
     */
    @Test
    void anUnknownParticipantIdIsTreatedAsANonMember() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":%d,"category":"FOOD","description":"Dinner",
                                 "totalAmount":30000,"participantUserIds":[%d,999999]}
                                """.formatted(ahmed.getId(), ahmed.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Users [999999] are not members of this group, "
                        + "so they cannot pay for or share an expense"));
    }

    /** The payer paying for people who ate without them is normal and must be allowed. */
    @Test
    void aPayerWhoIsNotAParticipantIsAllowed() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":%d,"category":"FOOD","description":"Their dinner",
                                 "totalAmount":30000,"participantUserIds":[%d,%d]}
                                """.formatted(ahmed.getId(), mohamed.getId(), zeinab.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paidByUserId").value(ahmed.getId()))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.participants[0].shareAmount").value(15000))
                .andExpect(jsonPath("$.participants[1].shareAmount").value(15000));
    }

    @Test
    void anExpenseInAnUnknownGroupIsA404() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", 999999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":%d,"category":"FOOD","description":"Dinner",
                                 "totalAmount":30000,"participantUserIds":[%d]}
                                """.formatted(ahmed.getId(), ahmed.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Group 999999 was not found"));
    }

    /** BR-3 end to end: nothing is written for an expense nobody shares. */
    @Test
    void anExpenseWithNoParticipantsIsRejected() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":%d,"category":"FOOD","description":"Dinner",
                                 "totalAmount":30000,"participantUserIds":[]}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isBadRequest());

        assertThat(expenseRepository.count()).isZero();
    }

    /** Several expenses in one group are independent, each with its own share rows. */
    @Test
    void severalExpensesInAGroupKeepSeparateShares() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":%d,"category":"FOOD","description":"Pizza",
                                 "totalAmount":30000,"participantUserIds":[%d,%d]}
                                """.formatted(ahmed.getId(), ahmed.getId(), mohamed.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":%d,"category":"TRANSPORTATION","description":"Uber",
                                 "totalAmount":10,"participantUserIds":[%d,%d,%d]}
                                """.formatted(zeinab.getId(), ahmed.getId(), mohamed.getId(), zeinab.getId())))
                .andExpect(status().isCreated());

        assertThat(expenseRepository.count()).isEqualTo(2);
        assertThat(expenseParticipantRepository.count()).isEqualTo(5);
        assertThat(expenseParticipantRepository.findAll().stream()
                .mapToLong(ExpenseParticipant::getShareAmount).sum())
                .as("both totals accounted for, nothing lost to rounding")
                .isEqualTo(30_010L);
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

    private long createGroup(User creator, String name, List<User> members) throws Exception {
        String ids = members.stream()
                .map(member -> member.getId().toString())
                .collect(Collectors.joining(","));

        String response = mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":%d,"name":"%s","memberUserIds":[%s]}
                                """.formatted(creator.getId(), name, ids)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.parse(response).read("$.id", Integer.class).longValue();
    }
}
