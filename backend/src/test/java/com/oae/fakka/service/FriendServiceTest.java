package com.oae.fakka.service;

import com.oae.fakka.dto.AddFriendRequest;
import com.oae.fakka.dto.UserSummaryResponse;
import com.oae.fakka.entity.Friendship;
import com.oae.fakka.entity.User;
import com.oae.fakka.exception.AlreadyFriendsException;
import com.oae.fakka.exception.AmbiguousUserException;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.exception.SelfFriendshipException;
import com.oae.fakka.repository.FriendshipRepository;
import com.oae.fakka.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link FriendService} with both repositories mocked.
 * <p>
 * The integration tests prove the endpoint behaves; these pin down what the service actually
 * asks the database for -- which lookup it normalises, that the mirrored pair is written as one
 * call, and that no write happens on any rejection path.
 */
@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    private static final User AHMED = user(1L, "Ahmed Ragy", "ahmed@example.com", null);
    private static final User MOHAMED =
            user(2L, "Mohamed Salah", "mohamed@example.com", "https://img.example.com/m.jpg");

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Captor
    private ArgumentCaptor<List<Friendship>> friendshipsCaptor;

    @InjectMocks
    private FriendService friendService;

    @Test
    void addFriendLooksUpTheNormalisedEmail() {
        givenUsersExist();
        given(userRepository.findByEmail("mohamed@example.com")).willReturn(Optional.of(MOHAMED));

        friendService.addFriend(new AddFriendRequest(1L, "  MOHAMED@Example.COM ", null));

        verify(userRepository).findByEmail("mohamed@example.com");
    }

    /** Two rows in one call: the pair must be written together or the friendship is half-visible. */
    @Test
    void addFriendWritesTheMirroredPairInOneCall() {
        givenUsersExist();
        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(MOHAMED));

        friendService.addFriend(new AddFriendRequest(1L, "mohamed@example.com", null));

        verify(friendshipRepository).saveAllAndFlush(friendshipsCaptor.capture());
        assertThat(friendshipsCaptor.getValue())
                .extracting(Friendship::getUserId, Friendship::getFriendId)
                .containsExactly(tuple(1L, 2L), tuple(2L, 1L));
    }

    @Test
    void addFriendReturnsTheNewFriendSummary() {
        givenUsersExist();
        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(MOHAMED));

        UserSummaryResponse response =
                friendService.addFriend(new AddFriendRequest(1L, "mohamed@example.com", null));

        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.name()).isEqualTo("Mohamed Salah");
        assertThat(response.profileImageUrl()).isEqualTo("https://img.example.com/m.jpg");
    }

    @Test
    void addFriendResolvesASingleUsernameMatch() {
        givenUsersExist();
        given(userRepository.findByNameIgnoreCase("Mohamed Salah")).willReturn(List.of(MOHAMED));

        UserSummaryResponse response =
                friendService.addFriend(new AddFriendRequest(1L, null, "Mohamed Salah"));

        assertThat(response.userId()).isEqualTo(2L);
        verify(userRepository, never()).findByEmail(anyString());
    }

    /** Display names are not unique, so several matches is a conflict, never a guess. */
    @Test
    void addFriendRejectsAnAmbiguousUsername() {
        given(userRepository.findById(1L)).willReturn(Optional.of(AHMED));
        given(userRepository.findByNameIgnoreCase("Mohamed Salah")).willReturn(List.of(
                MOHAMED, user(3L, "mohamed salah", "other@example.com", null)));

        assertThatThrownBy(() -> friendService.addFriend(
                new AddFriendRequest(1L, null, "Mohamed Salah")))
                .isInstanceOf(AmbiguousUserException.class)
                .hasMessageContaining("2 users share this name");

        verify(friendshipRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void addFriendRejectsSelfByResolvedIdRatherThanByIdentifier() {
        given(userRepository.findById(1L)).willReturn(Optional.of(AHMED));
        // Resolved through their own name, not their own id, so only the id comparison catches it.
        given(userRepository.findByNameIgnoreCase("Ahmed Ragy")).willReturn(List.of(AHMED));

        assertThatThrownBy(() -> friendService.addFriend(
                new AddFriendRequest(1L, null, "Ahmed Ragy")))
                .isInstanceOf(SelfFriendshipException.class);

        verify(friendshipRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void addFriendRejectsAnExistingFriendshipWithoutWriting() {
        given(userRepository.findById(1L)).willReturn(Optional.of(AHMED));
        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(MOHAMED));
        given(friendshipRepository.existsByUserIdAndFriendId(1L, 2L)).willReturn(true);

        assertThatThrownBy(() -> friendService.addFriend(
                new AddFriendRequest(1L, "mohamed@example.com", null)))
                .isInstanceOf(AlreadyFriendsException.class);

        verify(friendshipRepository, never()).saveAllAndFlush(any());
    }

    /** The unique index is the real arbiter when two adds race; its loss must still read as 409. */
    @Test
    void addFriendTranslatesUniqueIndexLossIntoConflict() {
        givenUsersExist();
        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(MOHAMED));
        given(friendshipRepository.saveAllAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("uk_friendship_pair"));

        assertThatThrownBy(() -> friendService.addFriend(
                new AddFriendRequest(1L, "mohamed@example.com", null)))
                .isInstanceOf(AlreadyFriendsException.class);
    }

    @Test
    void addFriendReports404ForAnUnknownActingUser() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.addFriend(
                new AddFriendRequest(99L, "mohamed@example.com", null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User 99 was not found");

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void addFriendReports404ForAnUnknownEmail() {
        given(userRepository.findById(1L)).willReturn(Optional.of(AHMED));
        given(userRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.addFriend(
                new AddFriendRequest(1L, "nobody@example.com", null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No registered user has this email address");
    }

    @Test
    void addFriendReports404ForAnUnknownUsername() {
        given(userRepository.findById(1L)).willReturn(Optional.of(AHMED));
        given(userRepository.findByNameIgnoreCase("Nobody At All")).willReturn(List.of());

        assertThatThrownBy(() -> friendService.addFriend(
                new AddFriendRequest(1L, null, "Nobody At All")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No registered user has this username");
    }

    @Test
    void listFriendsMapsUsersToSummariesKeepingRepositoryOrder() {
        given(userRepository.existsById(1L)).willReturn(true);
        given(friendshipRepository.findFriendsOf(1L)).willReturn(List.of(
                user(3L, "Bilal Omar", "bilal@example.com", null), MOHAMED));

        List<UserSummaryResponse> friends = friendService.listFriends(1L);

        assertThat(friends)
                .extracting(UserSummaryResponse::name)
                .containsExactly("Bilal Omar", "Mohamed Salah");
    }

    /** An unknown user is a 404; an empty list would tell the client the wrong thing. */
    @Test
    void listFriendsReports404ForAnUnknownUser() {
        given(userRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> friendService.listFriends(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User 99 was not found");

        verify(friendshipRepository, never()).findFriendsOf(anyLong());
    }

    @Test
    void listFriendsReturnsEmptyListForUserWithNoFriends() {
        given(userRepository.existsById(1L)).willReturn(true);
        given(friendshipRepository.findFriendsOf(1L)).willReturn(List.of());

        assertThat(friendService.listFriends(1L)).isEmpty();
    }

    /** Shared happy-path stubs for the tests that get as far as writing. */
    private void givenUsersExist() {
        given(userRepository.findById(1L)).willReturn(Optional.of(AHMED));
    }

    private static User user(Long id, String name, String email, String profileImageUrl) {
        return User.builder()
                .id(id)
                .name(name)
                .email(email)
                .passwordHash("$2a$10$" + "x".repeat(53))
                .profileImageUrl(profileImageUrl)
                .build();
    }
}
