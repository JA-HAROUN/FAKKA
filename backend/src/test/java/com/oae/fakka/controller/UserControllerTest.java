package com.oae.fakka.controller;

import com.oae.fakka.entity.Friendship;
import com.oae.fakka.entity.User;
import com.oae.fakka.repository.FriendshipRepository;
import com.oae.fakka.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the personal dashboard against the real schema and the real, stubbed
 * balance engine.
 * <p>
 * Where {@code UserControllerWebMvcTest} proves the wire format for balances that do not exist
 * yet, this proves what a client actually gets today: real groups, real member counts, and a
 * zero balance on every card until Phase 5.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class UserControllerTest {

    private static final String DUMMY_HASH = "$2a$10$" + "x".repeat(53);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Test
    void dashboardListsEveryGroupTheUserBelongsToWithItsMemberCount() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com");
        User mohamed = createUser("Mohamed Salah", "mohamed@example.com");
        befriend(ahmed, mohamed);

        createGroup(ahmed, "Dinner", "https://img.example.com/dinner.jpg", List.of(mohamed));
        createGroup(ahmed, "Solo", null, List.of());

        mockMvc.perform(get("/api/users/{userId}/groups", ahmed.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Newest first, so the group created second leads.
                .andExpect(jsonPath("$[0].name").value("Solo"))
                .andExpect(jsonPath("$[0].memberCount").value(1))
                .andExpect(jsonPath("$[0].imageUrl").doesNotExist())
                .andExpect(jsonPath("$[1].name").value("Dinner"))
                .andExpect(jsonPath("$[1].memberCount").value(2))
                .andExpect(jsonPath("$[1].imageUrl").value("https://img.example.com/dinner.jpg"))
                .andExpect(jsonPath("$[1].groupId").isNumber());
    }

    /**
     * The stub returns 0 for everyone, so every card is SETTLED. This test is the tripwire for
     * Phase 5: when the balance engine lands it should fail, and be replaced with real amounts.
     */
    @Test
    void dashboardReportsZeroBalanceAndSettledWhileTheEngineIsStubbed() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com");
        createGroup(ahmed, "Dinner", null, List.of());

        mockMvc.perform(get("/api/users/{userId}/groups", ahmed.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userBalance").value(0))
                .andExpect(jsonPath("$[0].status").value("SETTLED"));
    }

    /** A member who did not create the group must still see it on their own dashboard. */
    @Test
    void dashboardIncludesGroupsTheUserWasAddedTo() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com");
        User mohamed = createUser("Mohamed Salah", "mohamed@example.com");
        befriend(ahmed, mohamed);

        createGroup(ahmed, "Dinner", null, List.of(mohamed));

        mockMvc.perform(get("/api/users/{userId}/groups", mohamed.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Dinner"))
                .andExpect(jsonPath("$[0].memberCount").value(2));
    }

    /** Membership scopes the dashboard: a group someone else made must not appear. */
    @Test
    void dashboardExcludesGroupsTheUserIsNotIn() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com");
        User stranger = createUser("Stranger Danger", "stranger@example.com");

        createGroup(stranger, "Not yours", null, List.of());

        mockMvc.perform(get("/api/users/{userId}/groups", ahmed.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void dashboardIsEmptyForAUserWithNoGroups() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com");

        mockMvc.perform(get("/api/users/{userId}/groups", ahmed.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** An unknown user must not look like a user with an empty dashboard. */
    @Test
    void dashboardReturns404ForUnknownUser() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/groups", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User 999999 was not found"));
    }

    @Test
    void dashboardRejectsNonPositiveUserId() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/groups", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
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

    private void createGroup(User creator, String name, String imageUrl, List<User> members)
            throws Exception {

        String ids = members.stream()
                .map(member -> member.getId().toString())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        String image = imageUrl == null ? "null" : "\"%s\"".formatted(imageUrl);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":%d,"name":"%s","imageUrl":%s,"memberUserIds":[%s]}
                                """.formatted(creator.getId(), name, image, ids)))
                .andExpect(status().isCreated());
    }
}
