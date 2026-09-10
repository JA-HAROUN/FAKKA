package com.oae.fakka.controller;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class FriendControllerTest {

    /** Shape-valid BCrypt-looking hash: these tests never sign in, they only need a non-null value. */
    private static final String DUMMY_HASH = "$2a$10$" + "x".repeat(53);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    /**
     * The central guarantee of the symmetric model: one POST puts each user in the other list,
     * with no second call and no accept step.
     */
    @Test
    void addFriendByEmailIsVisibleFromBothSides() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        User mohamed = createUser("Mohamed Salah", "mohamed@example.com", "https://img.example.com/m.jpg");

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"email":"mohamed@example.com"}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(mohamed.getId()))
                .andExpect(jsonPath("$.name").value("Mohamed Salah"))
                .andExpect(jsonPath("$.profileImageUrl").value("https://img.example.com/m.jpg"));

        mockMvc.perform(get("/api/friends").param("userId", ahmed.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(mohamed.getId()));

        mockMvc.perform(get("/api/friends").param("userId", mohamed.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(ahmed.getId()));
    }

    /** Two rows, not one, is the stored representation the rest of the design assumes. */
    @Test
    void addFriendStoresBothDirections() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        User mohamed = createUser("Mohamed Salah", "mohamed@example.com", null);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"email":"mohamed@example.com"}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isCreated());

        assertThat(friendshipRepository.count()).isEqualTo(2);
        assertThat(friendshipRepository.existsByUserIdAndFriendId(ahmed.getId(), mohamed.getId())).isTrue();
        assertThat(friendshipRepository.existsByUserIdAndFriendId(mohamed.getId(), ahmed.getId())).isTrue();
        assertThat(friendshipRepository.findAll())
                .allSatisfy(friendship -> assertThat(friendship.getCreatedAt()).isNotNull());
    }

    @Test
    void addFriendByUsernameMatchesIgnoringCase() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        User mohamed = createUser("Mohamed Salah", "mohamed@example.com", null);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"username":"  mohamed salah "}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(mohamed.getId()));
    }

    /** Emails are stored normalised, so a differently-cased address must still resolve. */
    @Test
    void addFriendByEmailMatchesIgnoringCase() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        createUser("Mohamed Salah", "mohamed@example.com", null);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"email":" MOHAMED@Example.COM "}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isCreated());
    }

    @Test
    void addFriendRejectsSelfByEmail() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"email":"AHMED@example.com"}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot add yourself as a friend"));

        assertThat(friendshipRepository.count()).isZero();
    }

    /** Same rule, reached through the other identifier. */
    @Test
    void addFriendRejectsSelfByUsername() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"username":"Ahmed Ragy"}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isBadRequest());

        assertThat(friendshipRepository.count()).isZero();
    }

    @Test
    void addFriendRejectsDuplicate() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        createUser("Mohamed Salah", "mohamed@example.com", null);

        String payload = """
                {"userId":%d,"email":"mohamed@example.com"}
                """.formatted(ahmed.getId());

        mockMvc.perform(post("/api/friends").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/friends").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.path").value("/api/friends"));

        assertThat(friendshipRepository.count()).isEqualTo(2);
    }

    /**
     * The duplicate check must see the friendship from either end. A per-direction check would
     * let the second user "add" a friend they already have and write a third row.
     */
    @Test
    void addFriendRejectsDuplicateRequestedFromTheOtherSide() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        User mohamed = createUser("Mohamed Salah", "mohamed@example.com", null);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"email":"mohamed@example.com"}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"email":"ahmed@example.com"}
                                """.formatted(mohamed.getId())))
                .andExpect(status().isConflict());

        assertThat(friendshipRepository.count()).isEqualTo(2);
    }

    @Test
    void addFriendReturns404ForUnknownEmail() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"email":"nobody@example.com"}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No registered user has this email address"));
    }

    @Test
    void addFriendReturns404ForUnknownUsername() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"username":"Nobody At All"}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No registered user has this username"));
    }

    /** A partial name must not resolve: this endpoint adds a specific person, it is not a search box. */
    @Test
    void addFriendDoesNotMatchPartialUsername() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        createUser("Mohamed Salah", "mohamed@example.com", null);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"username":"Mohamed"}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void addFriendReturns404WhenActingUserDoesNotExist() throws Exception {
        createUser("Mohamed Salah", "mohamed@example.com", null);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":999999,"email":"mohamed@example.com"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User 999999 was not found"));
    }

    /**
     * Display names are not unique, so a shared name is ambiguous. Adding one of the matches
     * would silently befriend the wrong person and carry that error into groups and balances.
     */
    @Test
    void addFriendReturns409WhenUsernameMatchesSeveralAccounts() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        createUser("Mohamed Salah", "mohamed1@example.com", null);
        createUser("mohamed salah", "mohamed2@example.com", null);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"username":"Mohamed Salah"}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("2 users share this name. Add this friend by email address instead."));

        assertThat(friendshipRepository.count()).isZero();
    }

    @Test
    void addFriendRejectsBothIdentifiersAtOnce() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        createUser("Mohamed Salah", "mohamed@example.com", null);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"email":"mohamed@example.com","username":"Mohamed Salah"}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        assertThat(friendshipRepository.count()).isZero();
    }

    @Test
    void addFriendRejectsMissingIdentifier() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isBadRequest());
    }

    /** A blank email is not an identifier, so this is the username case, not a malformed address. */
    @Test
    void addFriendTreatsBlankEmailAsAbsent() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        User mohamed = createUser("Mohamed Salah", "mohamed@example.com", null);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"email":"  ","username":"Mohamed Salah"}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(mohamed.getId()));
    }

    @Test
    void addFriendRejectsMissingUserId() throws Exception {
        createUser("Mohamed Salah", "mohamed@example.com", null);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"mohamed@example.com"}
                                """))
                .andExpect(status().isBadRequest());
    }

    /** FR-10 asks for name and image; an email address in a friend list is more than that needs. */
    @Test
    void listFriendsReturnsNamesAndImagesOrderedByName() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        createUser("Zeinab Hassan", "zeinab@example.com", "https://img.example.com/z.jpg");
        createUser("Bilal Omar", "bilal@example.com", null);

        for (String email : new String[] {"zeinab@example.com", "bilal@example.com"}) {
            mockMvc.perform(post("/api/friends")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":%d,"email":"%s"}
                                    """.formatted(ahmed.getId(), email)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/friends").param("userId", ahmed.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Bilal Omar"))
                .andExpect(jsonPath("$[0].profileImageUrl").doesNotExist())
                .andExpect(jsonPath("$[1].name").value("Zeinab Hassan"))
                .andExpect(jsonPath("$[1].profileImageUrl").value("https://img.example.com/z.jpg"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void listFriendsReturnsEmptyArrayForUserWithNoFriends() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);

        mockMvc.perform(get("/api/friends").param("userId", ahmed.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** An unknown user must not look like a user with no friends. */
    @Test
    void listFriendsReturns404ForUnknownUser() throws Exception {
        mockMvc.perform(get("/api/friends").param("userId", "999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User 999999 was not found"));
    }

    @Test
    void listFriendsRejectsMissingUserId() throws Exception {
        mockMvc.perform(get("/api/friends"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void listFriendsRejectsNonPositiveUserId() throws Exception {
        mockMvc.perform(get("/api/friends").param("userId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private User createUser(String name, String email, String profileImageUrl) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .passwordHash(DUMMY_HASH)
                .profileImageUrl(profileImageUrl)
                .build());
    }
}
