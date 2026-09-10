package com.oae.fakka.controller;

import com.jayway.jsonpath.JsonPath;
import com.oae.fakka.entity.Friendship;
import com.oae.fakka.entity.SettlementStatus;
import com.oae.fakka.entity.User;
import com.oae.fakka.repository.ExpenseRepository;
import com.oae.fakka.repository.FriendshipRepository;
import com.oae.fakka.repository.SettlementRepository;
import com.oae.fakka.repository.UserRepository;
import com.oae.fakka.service.BalanceService;
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
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole money path end to end: real expenses, real balances, real suggested settlements.
 * <p>
 * The unit tests prove each piece in isolation. This proves they agree with each other -- that
 * what the split service divided is what the balance engine adds back up, and that the payments
 * the simplifier suggests actually clear the balances the breakdown reports. BR-5 is asserted
 * against the database rather than against a hand-written map.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class GroupFinanceTest {

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
    private SettlementRepository settlementRepository;

    @Autowired
    private BalanceService balanceService;

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

    /** A group with no expenses is settled, and everybody is present at zero rather than absent. */
    @Test
    void aGroupWithNoExpensesIsSettledForEveryMember() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/balances", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].paid").value(0))
                .andExpect(jsonPath("$[0].owed").value(0))
                .andExpect(jsonPath("$[0].net").value(0))
                .andExpect(jsonPath("$[0].status").value("SETTLED"))
                .andExpect(jsonPath("$[2].net").value(0));

        mockMvc.perform(get("/api/groups/{groupId}/settlements/suggested", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** One 900 dinner split three ways: the payer is owed 600, the other two owe 300 each. */
    @Test
    void oneExpenseProducesTheExpectedBreakdown() throws Exception {
        recordExpense(ahmed, 90_000L, List.of(ahmed, mohamed, zeinab));

        mockMvc.perform(get("/api/groups/{groupId}/balances", groupId))
                .andExpect(status().isOk())
                // Ordered by name: Ahmed, Mohamed, Zeinab.
                .andExpect(jsonPath("$[0].name").value("Ahmed Ragy"))
                .andExpect(jsonPath("$[0].paid").value(90000))
                .andExpect(jsonPath("$[0].owed").value(30000))
                .andExpect(jsonPath("$[0].net").value(60000))
                .andExpect(jsonPath("$[0].status").value("POSITIVE"))
                .andExpect(jsonPath("$[1].paid").value(0))
                .andExpect(jsonPath("$[1].owed").value(30000))
                .andExpect(jsonPath("$[1].net").value(-30000))
                .andExpect(jsonPath("$[1].status").value("NEGATIVE"))
                .andExpect(jsonPath("$[2].net").value(-30000));

        assertThat(balanceService.groupBalancesSumToZero(groupId)).as("BR-5").isTrue();
    }

    /** The payer being owed and the others owing must come out as two direct payments. */
    @Test
    void oneExpenseProducesTheExpectedSettlements() throws Exception {
        recordExpense(ahmed, 90_000L, List.of(ahmed, mohamed, zeinab));

        mockMvc.perform(get("/api/groups/{groupId}/settlements/suggested", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fromUserId").value(mohamed.getId()))
                .andExpect(jsonPath("$[0].toUserId").value(ahmed.getId()))
                .andExpect(jsonPath("$[0].amount").value(30000))
                .andExpect(jsonPath("$[1].fromUserId").value(zeinab.getId()))
                .andExpect(jsonPath("$[1].toUserId").value(ahmed.getId()))
                .andExpect(jsonPath("$[1].amount").value(30000));
    }

    /**
     * Two people paying for overlapping dinners is where simplification earns its name: the
     * cross-debts net off, so nobody pays somebody who is about to pay them back.
     */
    @Test
    void crossDebtsBetweenTwoPayersNetOff() throws Exception {
        // Ahmed pays 600 for all three; each owes 200, so Ahmed is up 400.
        recordExpense(ahmed, 60_000L, List.of(ahmed, mohamed, zeinab));
        // Mohamed pays 300 for all three; each owes 100, so that pulls Ahmed back to 300.
        recordExpense(mohamed, 30_000L, List.of(ahmed, mohamed, zeinab));

        Map<Long, Long> balances = balanceService.calculateGroupBalancesInPiastres(groupId);
        assertThat(balances).containsExactlyInAnyOrderEntriesOf(Map.of(
                ahmed.getId(), 30_000L,
                mohamed.getId(), 0L,
                zeinab.getId(), -30_000L));

        // Mohamed is square, so a single payment settles the group.
        mockMvc.perform(get("/api/groups/{groupId}/settlements/suggested", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fromUserId").value(zeinab.getId()))
                .andExpect(jsonPath("$[0].toUserId").value(ahmed.getId()))
                .andExpect(jsonPath("$[0].amount").value(30000));
    }

    /**
     * The rounding case, carried the whole way through: 100 piastres split three ways gives
     * 34/33/33, and the balances still cancel exactly. This is what integer money buys.
     */
    @Test
    void aRoundingRemainderStillBalancesToTheLastPiastre() throws Exception {
        recordExpense(ahmed, 100L, List.of(ahmed, mohamed, zeinab));

        mockMvc.perform(get("/api/groups/{groupId}/balances", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paid").value(100))
                .andExpect(jsonPath("$[0].owed").value(34))
                .andExpect(jsonPath("$[0].net").value(66))
                .andExpect(jsonPath("$[1].net").value(-33))
                .andExpect(jsonPath("$[2].net").value(-33));

        assertThat(balanceService.groupBalancesSumToZero(groupId)).as("BR-5 with a remainder").isTrue();

        mockMvc.perform(get("/api/groups/{groupId}/settlements/suggested", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].amount").value(33))
                .andExpect(jsonPath("$[1].amount").value(33));
    }

    /** A custom split feeds the same engine: what was agreed is what shows up as owed. */
    @Test
    void aCustomSplitIsReflectedInTheBalances() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":%d,"category":"FOOD","description":"Pizza and salad",
                                 "totalAmount":65000,"participantUserIds":[%d,%d],"splitType":"CUSTOM",
                                 "customShares":{"%d":40000,"%d":25000}}
                                """.formatted(zeinab.getId(), ahmed.getId(), mohamed.getId(),
                                ahmed.getId(), mohamed.getId())))
                .andExpect(status().isCreated());

        assertThat(balanceService.calculateGroupBalancesInPiastres(groupId))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        ahmed.getId(), -40_000L,
                        mohamed.getId(), -25_000L,
                        zeinab.getId(), 65_000L));
        assertThat(balanceService.groupBalancesSumToZero(groupId)).isTrue();
    }

    /** The suggested payments must actually clear the reported balances, not merely look plausible. */
    @Test
    void theSuggestedPaymentsClearEveryReportedBalance() throws Exception {
        recordExpense(ahmed, 90_000L, List.of(ahmed, mohamed, zeinab));
        recordExpense(mohamed, 45_000L, List.of(mohamed, zeinab));
        recordExpense(zeinab, 100L, List.of(ahmed, mohamed, zeinab));

        Map<Long, Long> balances = balanceService.calculateGroupBalancesInPiastres(groupId);
        String settlements = mockMvc.perform(
                        get("/api/groups/{groupId}/settlements/suggested", groupId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> payments = JsonPath.parse(settlements).read("$");
        Map<Long, Long> remaining = new java.util.LinkedHashMap<>(balances);
        for (Map<String, Object> payment : payments) {
            long from = ((Number) payment.get("fromUserId")).longValue();
            long to = ((Number) payment.get("toUserId")).longValue();
            long amount = ((Number) payment.get("amount")).longValue();
            remaining.merge(from, amount, Long::sum);
            remaining.merge(to, -amount, Long::sum);
        }

        assertThat(remaining.values()).allSatisfy(balance -> assertThat(balance).isZero());
        assertThat(payments.size()).isLessThanOrEqualTo(2);
    }

    /** The engine also answers for one member at a time, in EGP, with the same number. */
    @Test
    void theSingleUserApiAgreesWithTheGroupBreakdown() throws Exception {
        recordExpense(ahmed, 90_000L, List.of(ahmed, mohamed, zeinab));

        assertThat(balanceService.calculateUserBalanceInPiastres(ahmed.getId(), groupId))
                .isEqualTo(60_000L);
        assertThat(balanceService.calculateUserBalance(ahmed.getId(), groupId))
                .isEqualByComparingTo("600.00");
        assertThat(balanceService.calculateUserBalance(mohamed.getId(), groupId))
                .isEqualByComparingTo("-300.00");
    }

    /** Expenses in one group must not leak into another group balances. */
    @Test
    void balancesAreScopedToOneGroup() throws Exception {
        long otherGroupId = createGroup(ahmed, List.of(mohamed));
        recordExpense(ahmed, 90_000L, List.of(ahmed, mohamed, zeinab));

        assertThat(balanceService.calculateGroupBalancesInPiastres(otherGroupId).values())
                .allSatisfy(balance -> assertThat(balance).isZero());
        assertThat(balanceService.calculateUserBalanceInPiastres(ahmed.getId(), otherGroupId)).isZero();
    }

    @Test
    void balancesAndSettlementsAre404ForAnUnknownGroup() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/balances", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Group 999999 was not found"));

        mockMvc.perform(get("/api/groups/{groupId}/settlements/suggested", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Group 999999 was not found"));
    }

    /*
     * Settlements (FR-34, FR-35). The loop that matters: an expense creates a debt, the
     * suggestion says who should pay, the payment is recorded and then confirmed, and the group
     * comes out settled without any expense being touched.
     */

    /** Recording a settlement is not paying it, so nothing about the balances moves yet. */
    @Test
    void aPendingSettlementLeavesEveryBalanceWhereItWas() throws Exception {
        recordExpense(ahmed, 90_000L, List.of(ahmed, mohamed, zeinab));

        createSettlement(mohamed, ahmed, 30_000L);

        assertThat(balanceService.calculateGroupBalancesInPiastres(groupId))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        ahmed.getId(), 60_000L,
                        mohamed.getId(), -30_000L,
                        zeinab.getId(), -30_000L));

        // The suggestion still asks for the same two payments, because none has been made.
        mockMvc.perform(get("/api/groups/{groupId}/settlements/suggested", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    /** Paying it nets the amount out in both directions at once. */
    @Test
    void payingASettlementMovesBothBalances() throws Exception {
        recordExpense(ahmed, 90_000L, List.of(ahmed, mohamed, zeinab));

        long settlementId = createSettlement(mohamed, ahmed, 30_000L);
        markPaid(settlementId, mohamed);

        assertThat(balanceService.calculateGroupBalancesInPiastres(groupId))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        ahmed.getId(), 30_000L,
                        mohamed.getId(), 0L,
                        zeinab.getId(), -30_000L));
        assertThat(balanceService.groupBalancesSumToZero(groupId)).as("BR-5 after a payment").isTrue();
    }

    /** The full loop: settle every suggestion and the group is square, with no expense altered. */
    @Test
    void payingEverySuggestedSettlementSettlesTheGroup() throws Exception {
        recordExpense(ahmed, 90_000L, List.of(ahmed, mohamed, zeinab));

        for (Map<String, Object> suggestion : suggestedSettlements()) {
            long from = ((Number) suggestion.get("fromUserId")).longValue();
            long to = ((Number) suggestion.get("toUserId")).longValue();
            long amount = ((Number) suggestion.get("amount")).longValue();
            markPaid(createSettlement(from, to, amount), from);
        }

        assertThat(balanceService.calculateGroupBalancesInPiastres(groupId).values())
                .allSatisfy(balance -> assertThat(balance).isZero());
        assertThat(suggestedSettlements()).as("nothing left to settle").isEmpty();
        assertThat(expenseRepository.count()).as("the expense is untouched").isEqualTo(1);

        mockMvc.perform(get("/api/groups/{groupId}/balances", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SETTLED"))
                .andExpect(jsonPath("$[1].status").value("SETTLED"))
                .andExpect(jsonPath("$[2].status").value("SETTLED"));
    }

    /** The breakdown keeps settlements in their own columns, so the net stays explainable. */
    @Test
    void theBreakdownShowsSettlementsSeparatelyFromExpenses() throws Exception {
        recordExpense(ahmed, 90_000L, List.of(ahmed, mohamed, zeinab));
        markPaid(createSettlement(mohamed, ahmed, 30_000L), ahmed);

        mockMvc.perform(get("/api/groups/{groupId}/balances", groupId))
                .andExpect(status().isOk())
                // Ahmed: paid 900, consumed 300, and has been handed 300 back.
                .andExpect(jsonPath("$[0].name").value("Ahmed Ragy"))
                .andExpect(jsonPath("$[0].paid").value(90000))
                .andExpect(jsonPath("$[0].owed").value(30000))
                .andExpect(jsonPath("$[0].settledOut").value(0))
                .andExpect(jsonPath("$[0].settledIn").value(30000))
                .andExpect(jsonPath("$[0].net").value(30000))
                // Mohamed: consumed 300 and has now paid it.
                .andExpect(jsonPath("$[1].owed").value(30000))
                .andExpect(jsonPath("$[1].settledOut").value(30000))
                .andExpect(jsonPath("$[1].net").value(0))
                .andExpect(jsonPath("$[1].status").value("SETTLED"));
    }

    /** Part payment: the debt comes down by what was handed over and no more. */
    @Test
    void aPartPaymentLeavesTheRemainderToSettle() throws Exception {
        recordExpense(ahmed, 90_000L, List.of(ahmed, mohamed, zeinab));

        markPaid(createSettlement(mohamed, ahmed, 10_000L), mohamed);

        assertThat(balanceService.calculateUserBalanceInPiastres(mohamed.getId(), groupId))
                .isEqualTo(-20_000L);
        assertThat(suggestedSettlements())
                .anySatisfy(suggestion -> {
                    assertThat(((Number) suggestion.get("fromUserId")).longValue())
                            .isEqualTo(mohamed.getId());
                    assertThat(((Number) suggestion.get("amount")).longValue()).isEqualTo(20_000L);
                });
    }

    /** The dashboard card reads the same engine, so a payment shows up there too. */
    @Test
    void payingASettlementUpdatesTheDashboardCard() throws Exception {
        recordExpense(ahmed, 90_000L, List.of(ahmed, mohamed, zeinab));

        mockMvc.perform(get("/api/users/{userId}/groups", mohamed.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userBalance").value(-30000))
                .andExpect(jsonPath("$[0].status").value("NEGATIVE"));

        markPaid(createSettlement(mohamed, ahmed, 30_000L), mohamed);

        mockMvc.perform(get("/api/users/{userId}/groups", mohamed.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userBalance").value(0))
                .andExpect(jsonPath("$[0].status").value("SETTLED"));
    }

    /** Over-paying is allowed, and pushes the balance past zero rather than being clamped. */
    @Test
    void overPayingASettlementFlipsTheBalance() throws Exception {
        recordExpense(ahmed, 90_000L, List.of(ahmed, mohamed, zeinab));

        markPaid(createSettlement(mohamed, ahmed, 50_000L), mohamed);

        assertThat(balanceService.calculateUserBalanceInPiastres(mohamed.getId(), groupId))
                .as("paid 500 against a 300 debt")
                .isEqualTo(20_000L);
        assertThat(balanceService.groupBalancesSumToZero(groupId)).isTrue();
    }

    @Test
    void aSettlementBetweenNonMembersIsRejected() throws Exception {
        User outsider = createUser("Stranger Danger", "stranger@example.com");

        mockMvc.perform(post("/api/groups/{groupId}/settlements", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromUserId":%d,"toUserId":%d,"amount":30000}
                                """.formatted(outsider.getId(), ahmed.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Users [%d] are not members of this group".formatted(outsider.getId())));

        assertThat(settlementRepository.count()).isZero();
    }

    /** FR-35: a third party cannot confirm somebody else payment, even inside the same group. */
    @Test
    void aMemberWhoIsNotAPartyCannotMarkASettlementPaid() throws Exception {
        long settlementId = createSettlement(mohamed, ahmed, 30_000L);

        mockMvc.perform(patch("/api/settlements/{settlementId}/pay", settlementId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d}
                                """.formatted(zeinab.getId())))
                .andExpect(status().isForbidden());

        assertThat(settlementRepository.findById(settlementId))
                .get()
                .satisfies(settlement -> {
                    assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PENDING);
                    assertThat(settlement.getPaidAt()).isNull();
                });
    }

    @Test
    void aSettlementCannotBePaidTwice() throws Exception {
        long settlementId = createSettlement(mohamed, ahmed, 30_000L);
        markPaid(settlementId, mohamed);

        mockMvc.perform(patch("/api/settlements/{settlementId}/pay", settlementId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.startsWith("Settlement %d was already marked paid at "
                                .formatted(settlementId))));
    }

    @Test
    void markingAnUnknownSettlementPaidIsA404() throws Exception {
        mockMvc.perform(patch("/api/settlements/{settlementId}/pay", 999999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Settlement 999999 was not found"));
    }

    /** A payment in one group must not settle a debt in another. */
    @Test
    void paidSettlementsAreScopedToTheirGroup() throws Exception {
        long otherGroupId = createGroup(ahmed, List.of(mohamed));
        recordExpense(ahmed, 90_000L, List.of(ahmed, mohamed, zeinab));

        markPaid(createSettlement(mohamed, ahmed, 30_000L), mohamed);

        assertThat(balanceService.calculateGroupBalancesInPiastres(otherGroupId).values())
                .allSatisfy(balance -> assertThat(balance).isZero());
        assertThat(balanceService.calculateUserBalanceInPiastres(ahmed.getId(), otherGroupId)).isZero();
    }

    private User createUser(String name, String email) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .passwordHash(DUMMY_HASH)
                .build());
    }

    private long createSettlement(User from, User to, long amount) throws Exception {
        return createSettlement(from.getId(), to.getId(), amount);
    }

    private long createSettlement(long fromUserId, long toUserId, long amount) throws Exception {
        String response = mockMvc.perform(post("/api/groups/{groupId}/settlements", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromUserId":%d,"toUserId":%d,"amount":%d}
                                """.formatted(fromUserId, toUserId, amount)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        return JsonPath.parse(response).read("$.id", Integer.class).longValue();
    }

    private void markPaid(long settlementId, User claimant) throws Exception {
        markPaid(settlementId, claimant.getId());
    }

    private void markPaid(long settlementId, long claimantUserId) throws Exception {
        mockMvc.perform(patch("/api/settlements/{settlementId}/pay", settlementId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d}
                                """.formatted(claimantUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").exists());
    }

    private List<Map<String, Object>> suggestedSettlements() throws Exception {
        String response = mockMvc.perform(
                        get("/api/groups/{groupId}/settlements/suggested", groupId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.parse(response).read("$");
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

    private void recordExpense(User payer, long totalAmount, List<User> participants) throws Exception {
        String ids = participants.stream()
                .map(participant -> participant.getId().toString())
                .collect(Collectors.joining(","));

        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":%d,"category":"FOOD","description":"Dinner",
                                 "totalAmount":%d,"participantUserIds":[%s]}
                                """.formatted(payer.getId(), totalAmount, ids)))
                .andExpect(status().isCreated());
    }
}
