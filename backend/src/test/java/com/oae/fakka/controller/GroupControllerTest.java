package com.oae.fakka.controller;

import com.oae.fakka.entity.Friendship;
import com.oae.fakka.entity.User;
import com.oae.fakka.repository.FriendshipRepository;
import com.oae.fakka.repository.GroupMemberRepository;
import com.oae.fakka.repository.GroupRepository;
import com.oae.fakka.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class GroupControllerTest {

    /** Shape-valid BCrypt-looking hash: these tests never sign in, they only need a non-null value. */
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

    @Test
    void createGroupAddsCreatorAndTheGivenFriends() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        User mohamed = createUser("Mohamed Salah", "mohamed@example.com", "https://img.example.com/m.jpg");
        User zeinab = createUser("Zeinab Hassan", "zeinab@example.com", null);
        befriend(ahmed, mohamed);
        befriend(ahmed, zeinab);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":%d,"name":"Dinner",
                                 "imageUrl":"https://img.example.com/dinner.jpg",
                                 "memberUserIds":[%d,%d]}
                                """.formatted(ahmed.getId(), mohamed.getId(), zeinab.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Dinner"))
                .andExpect(jsonPath("$.imageUrl").value("https://img.example.com/dinner.jpg"))
                .andExpect(jsonPath("$.createdBy").value(ahmed.getId()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.memberCount").value(3));

        assertThat(groupRepository.count()).isEqualTo(1);
        assertThat(groupMemberRepository.count()).isEqualTo(3);
        assertThat(groupMemberRepository.findAll())
                .allSatisfy(member -> assertThat(member.getJoinedAt()).isNotNull())
                .extracting(member -> member.getUserId())
                .containsExactlyInAnyOrder(ahmed.getId(), mohamed.getId(), zeinab.getId());
    }

    /** FR-7: the creator is a member even when they list nobody. */
    @Test
    void createGroupWithNoMembersStillContainsTheCreator() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":%d,"name":"Solo trip","memberUserIds":[]}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.imageUrl").doesNotExist());

        assertThat(groupMemberRepository.findAll())
                .singleElement()
                .satisfies(member -> assertThat(member.getUserId()).isEqualTo(ahmed.getId()));
    }

    /** An absent list must behave like an empty one, not like a bad request. */
    @Test
    void createGroupWithoutMemberListIsValid() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":%d,"name":"Just me"}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberCount").value(1));
    }

    /**
     * A member picker that includes the signed-in user is not a client bug, and the creator is a
     * member either way, so their own id in the list is accepted without producing a second row.
     */
    @Test
    void createGroupIgnoresCreatorRepeatedInMemberList() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        User mohamed = createUser("Mohamed Salah", "mohamed@example.com", null);
        befriend(ahmed, mohamed);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":%d,"name":"Dinner","memberUserIds":[%d,%d]}
                                """.formatted(ahmed.getId(), ahmed.getId(), mohamed.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberCount").value(2));

        assertThat(groupMemberRepository.count()).isEqualTo(2);
    }

    @Test
    void createGroupRejectsDuplicateMemberIds() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        User mohamed = createUser("Mohamed Salah", "mohamed@example.com", null);
        befriend(ahmed, mohamed);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":%d,"name":"Dinner","memberUserIds":[%d,%d]}
                                """.formatted(ahmed.getId(), mohamed.getId(), mohamed.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "memberUserIdsDistinct: must not contain duplicate ids"));

        assertThat(groupRepository.count()).isZero();
    }

    /** FR-7 scopes membership to friends, so a stranger cannot be added even if they exist. */
    @Test
    void createGroupRejectsMemberWhoIsNotAFriend() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        User mohamed = createUser("Mohamed Salah", "mohamed@example.com", null);
        User stranger = createUser("Stranger Danger", "stranger@example.com", null);
        befriend(ahmed, mohamed);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":%d,"name":"Dinner","memberUserIds":[%d,%d]}
                                """.formatted(ahmed.getId(), mohamed.getId(), stranger.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Users [%d] cannot be added: a group member must be a friend of the creator"
                                .formatted(stranger.getId())));

        assertThat(groupRepository.count()).isZero();
        assertThat(groupMemberRepository.count()).isZero();
    }

    /** Every offending id is reported at once, so the client can fix the whole selection. */
    @Test
    void createGroupReportsAllNonFriendMembersTogether() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        User first = createUser("First Stranger", "first@example.com", null);
        User second = createUser("Second Stranger", "second@example.com", null);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":%d,"name":"Dinner","memberUserIds":[%d,%d]}
                                """.formatted(ahmed.getId(), first.getId(), second.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Users [%d, %d] cannot be added: a group member must be a friend of the creator"
                                .formatted(first.getId(), second.getId())));
    }

    /**
     * A user id that does not exist gets the same answer as one that exists but is not a friend,
     * so group creation cannot be used to discover which ids are real.
     */
    @Test
    void createGroupTreatsUnknownMemberIdAsNotAFriend() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":%d,"name":"Dinner","memberUserIds":[999999]}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Users [999999] cannot be added: a group member must be a friend of the creator"));
    }

    /**
     * Friendship is symmetric, so the member can be the one who originally added the creator.
     * A one-directional check would reject half of all legitimate selections.
     */
    @Test
    void createGroupAcceptsFriendWhoInitiatedTheFriendship() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        User mohamed = createUser("Mohamed Salah", "mohamed@example.com", null);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"email":"ahmed@example.com"}
                                """.formatted(mohamed.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":%d,"name":"Dinner","memberUserIds":[%d]}
                                """.formatted(ahmed.getId(), mohamed.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberCount").value(2));
    }

    @Test
    void createGroupReturns404WhenCreatorDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":999999,"name":"Dinner"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User 999999 was not found"));

        assertThat(groupRepository.count()).isZero();
    }

    @Test
    void createGroupRejectsBlankName() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":%d,"name":"   "}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name: must not be blank"));
    }

    @Test
    void createGroupRejectsMissingName() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":%d,"memberUserIds":[]}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createGroupRejectsMissingCreator() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Dinner"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createGroupRejectsMalformedImageUrl() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":%d,"name":"Dinner","imageUrl":"not a url"}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("imageUrl: must be a valid URL"));
    }

    @Test
    void createGroupRejectsNonPositiveMemberId() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":%d,"name":"Dinner","memberUserIds":[0]}
                                """.formatted(ahmed.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /** FR-11 asks for name and image; a member list should not hand out everyone email address. */
    @Test
    void listMembersReturnsNamesAndImagesOrderedByName() throws Exception {
        User zeinab = createUser("Zeinab Hassan", "zeinab@example.com", "https://img.example.com/z.jpg");
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        User bilal = createUser("Bilal Omar", "bilal@example.com", null);
        befriend(zeinab, ahmed);
        befriend(zeinab, bilal);

        long groupId = createGroup(zeinab, "Trip", List.of(ahmed, bilal));

        mockMvc.perform(get("/api/groups/{id}/members", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Ahmed Ragy"))
                .andExpect(jsonPath("$[0].userId").value(ahmed.getId()))
                .andExpect(jsonPath("$[1].name").value("Bilal Omar"))
                .andExpect(jsonPath("$[2].name").value("Zeinab Hassan"))
                .andExpect(jsonPath("$[2].profileImageUrl").value("https://img.example.com/z.jpg"))
                .andExpect(jsonPath("$[0].profileImageUrl").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void listMembersReturns404ForUnknownGroup() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/members", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Group 999999 was not found"));
    }

    @Test
    void listMembersRejectsNonPositiveGroupId() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/members", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void listMembersRejectsNonNumericGroupId() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/members", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'id' has an invalid value"));
    }

    /** Membership is per group: adding someone to one group must not show them in another. */
    @Test
    void listMembersIsScopedToOneGroup() throws Exception {
        User ahmed = createUser("Ahmed Ragy", "ahmed@example.com", null);
        User mohamed = createUser("Mohamed Salah", "mohamed@example.com", null);
        befriend(ahmed, mohamed);

        long shared = createGroup(ahmed, "Dinner", List.of(mohamed));
        long solo = createGroup(ahmed, "Solo", List.of());

        mockMvc.perform(get("/api/groups/{id}/members", shared))
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/groups/{id}/members", solo))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(ahmed.getId()));
    }

    private User createUser(String name, String email, String profileImageUrl) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .passwordHash(DUMMY_HASH)
                .profileImageUrl(profileImageUrl)
                .build());
    }

    /** Writes the mirrored pair directly, so group tests do not depend on the friends endpoint. */
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
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.parse(response).read("$.id", Integer.class).longValue();
    }
}
