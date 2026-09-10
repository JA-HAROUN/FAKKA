package com.oae.fakka.service;

import com.oae.fakka.dto.CreateGroupRequest;
import com.oae.fakka.dto.GroupResponse;
import com.oae.fakka.dto.UserSummaryResponse;
import com.oae.fakka.entity.Group;
import com.oae.fakka.entity.GroupMember;
import com.oae.fakka.entity.User;
import com.oae.fakka.exception.NonFriendMemberException;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.repository.FriendshipRepository;
import com.oae.fakka.repository.GroupMemberRepository;
import com.oae.fakka.repository.GroupRepository;
import com.oae.fakka.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link GroupService} with all four repositories mocked.
 * <p>
 * Mocking is what makes the interesting rules observable: that the friend check is skipped
 * entirely when nobody else is listed (an empty IN list is not portable SQL), that the creator
 * is excluded from that check, and that nothing is written on a rejection.
 */
@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    private static final User CREATOR = user(1L, "Ahmed Ragy", null);
    private static final User MOHAMED = user(2L, "Mohamed Salah", "https://img.example.com/m.jpg");
    private static final User ZEINAB = user(3L, "Zeinab Hassan", null);

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Captor
    private ArgumentCaptor<List<GroupMember>> membersCaptor;

    @Captor
    private ArgumentCaptor<Collection<Long>> candidateIdsCaptor;

    @InjectMocks
    private GroupService groupService;

    @Test
    void createGroupSavesTheCreatorFirstThenTheRequestedFriends() {
        givenCreatorExists();
        givenFriends(2L, 3L);
        givenGroupIsSavedWithId(10L);

        GroupResponse response = groupService.createGroup(
                new CreateGroupRequest(1L, "Dinner", null, List.of(2L, 3L)));

        verify(groupMemberRepository).saveAllAndFlush(membersCaptor.capture());
        assertThat(membersCaptor.getValue())
                .extracting(GroupMember::getUserId)
                .containsExactly(1L, 2L, 3L);
        assertThat(membersCaptor.getValue())
                .allSatisfy(member -> assertThat(member.getGroupId()).isEqualTo(10L));
        assertThat(response.memberCount()).isEqualTo(3);
    }

    /** The member rows need the generated group id, so the group has to be saved first. */
    @Test
    void createGroupSavesTheGroupBeforeItsMembers() {
        givenCreatorExists();
        givenFriends(2L);
        givenGroupIsSavedWithId(10L);

        groupService.createGroup(new CreateGroupRequest(1L, "Dinner", null, List.of(2L)));

        InOrder inOrder = Mockito.inOrder(groupRepository, groupMemberRepository);
        inOrder.verify(groupRepository).saveAndFlush(any(Group.class));
        inOrder.verify(groupMemberRepository).saveAllAndFlush(any());
    }

    @Test
    void createGroupStoresNameAndImageOnTheGroup() {
        givenCreatorExists();
        givenGroupIsSavedWithId(10L);
        ArgumentCaptor<Group> groupCaptor = ArgumentCaptor.forClass(Group.class);

        groupService.createGroup(new CreateGroupRequest(
                1L, "Dinner", "https://img.example.com/dinner.jpg", List.of()));

        verify(groupRepository).saveAndFlush(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getName()).isEqualTo("Dinner");
        assertThat(groupCaptor.getValue().getImageUrl()).isEqualTo("https://img.example.com/dinner.jpg");
        assertThat(groupCaptor.getValue().getCreatedBy()).isEqualTo(1L);
    }

    /**
     * With nobody else listed there is nothing to validate, and asking anyway would hand the
     * friend lookup an empty IN list. Skipping the query is deliberate, so pin it down.
     */
    @Test
    void createGroupSkipsTheFriendCheckWhenNobodyElseIsListed() {
        givenCreatorExists();
        givenGroupIsSavedWithId(10L);

        GroupResponse response =
                groupService.createGroup(new CreateGroupRequest(1L, "Solo", null, List.of()));

        verifyNoInteractions(friendshipRepository);
        assertThat(response.memberCount()).isEqualTo(1);
    }

    /** The creator is a member by construction, so they must never be checked as a friend. */
    @Test
    void createGroupExcludesTheCreatorFromTheFriendCheck() {
        givenCreatorExists();
        givenFriends(2L);
        givenGroupIsSavedWithId(10L);

        groupService.createGroup(new CreateGroupRequest(1L, "Dinner", null, List.of(1L, 2L)));

        verify(friendshipRepository).findFriendIdsAmong(eq(1L), candidateIdsCaptor.capture());
        assertThat(candidateIdsCaptor.getValue()).containsExactly(2L);
    }

    /** Their own id in the list is redundant, not an error, and must not produce a second row. */
    @Test
    void createGroupDoesNotDuplicateTheCreatorListedAmongMembers() {
        givenCreatorExists();
        givenFriends(2L);
        givenGroupIsSavedWithId(10L);

        GroupResponse response = groupService.createGroup(
                new CreateGroupRequest(1L, "Dinner", null, List.of(1L, 2L)));

        verify(groupMemberRepository).saveAllAndFlush(membersCaptor.capture());
        assertThat(membersCaptor.getValue()).extracting(GroupMember::getUserId).containsExactly(1L, 2L);
        assertThat(response.memberCount()).isEqualTo(2);
    }

    @Test
    void createGroupRejectsEveryNonFriendInOneMessageWithoutWriting() {
        givenCreatorExists();
        // Only user 2 comes back as a friend, so 3 and 4 are both strangers.
        given(friendshipRepository.findFriendIdsAmong(eq(1L), anyCollection())).willReturn(Set.of(2L));

        assertThatThrownBy(() -> groupService.createGroup(
                new CreateGroupRequest(1L, "Dinner", null, List.of(2L, 3L, 4L))))
                .isInstanceOf(NonFriendMemberException.class)
                .hasMessage("Users [3, 4] cannot be added: "
                        + "a group member must be a friend of the creator");

        verifyNoInteractions(groupRepository, groupMemberRepository);
    }

    @Test
    void createGroupReports404ForAnUnknownCreatorBeforeTouchingAnythingElse() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.createGroup(
                new CreateGroupRequest(99L, "Dinner", null, List.of(2L))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User 99 was not found");

        verifyNoInteractions(friendshipRepository, groupRepository, groupMemberRepository);
    }

    @Test
    void listMembersMapsMembersToSummariesKeepingRepositoryOrder() {
        given(groupRepository.existsById(10L)).willReturn(true);
        given(groupMemberRepository.findMembersOf(10L)).willReturn(List.of(CREATOR, MOHAMED, ZEINAB));

        List<UserSummaryResponse> members = groupService.listMembers(10L);

        assertThat(members)
                .extracting(UserSummaryResponse::userId, UserSummaryResponse::name)
                .containsExactly(
                        tuple(1L, "Ahmed Ragy"),
                        tuple(2L, "Mohamed Salah"),
                        tuple(3L, "Zeinab Hassan"));
        assertThat(members.get(1).profileImageUrl()).isEqualTo("https://img.example.com/m.jpg");
    }

    /** Every group has at least its creator, so an empty result can only mean a bad id. */
    @Test
    void listMembersReports404ForAnUnknownGroup() {
        given(groupRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> groupService.listMembers(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Group 99 was not found");

        verify(groupMemberRepository, never()).findMembersOf(anyLong());
    }

    private void givenCreatorExists() {
        given(userRepository.findById(1L)).willReturn(Optional.of(CREATOR));
    }

    private void givenFriends(Long... friendIds) {
        given(friendshipRepository.findFriendIdsAmong(eq(1L), anyCollection()))
                .willReturn(Set.of(friendIds));
    }

    private void givenGroupIsSavedWithId(long id) {
        given(groupRepository.saveAndFlush(any(Group.class))).willAnswer(invocation -> {
            Group group = invocation.getArgument(0);
            group.setId(id);
            return group;
        });
    }

    private static User user(Long id, String name, String profileImageUrl) {
        return User.builder()
                .id(id)
                .name(name)
                .email("user%d@example.com".formatted(id))
                .passwordHash("$2a$10$" + "x".repeat(53))
                .profileImageUrl(profileImageUrl)
                .build();
    }
}
