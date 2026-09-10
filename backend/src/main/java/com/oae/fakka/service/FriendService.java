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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Friends (FR-8 to FR-10). A friendship is symmetric and is stored as a mirrored pair of
 * {@link Friendship} rows; that choice and its reasoning live on the entity.
 */
@Slf4j
@Service
public class FriendService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;

    public FriendService(UserRepository userRepository, FriendshipRepository friendshipRepository) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
    }

    /**
     * Looks up the target user by email or username and befriends them.
     * <p>
     * Transactional because the friendship is two rows: a commit that wrote only one would leave
     * the pair visible to a single side, and nothing later would detect it.
     */
    @Transactional
    public UserSummaryResponse addFriend(AddFriendRequest request) {
        User owner = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));

        User target = resolveTarget(request);

        /*
         * Compared by resolved id, not by the identifier sent: a user can reach themselves by
         * their own email or their own name, and both are self-friending.
         */
        if (owner.getId().equals(target.getId())) {
            throw new SelfFriendshipException();
        }

        if (friendshipRepository.existsByUserIdAndFriendId(owner.getId(), target.getId())) {
            throw new AlreadyFriendsException();
        }

        try {
            friendshipRepository.saveAllAndFlush(List.of(
                    Friendship.builder().userId(owner.getId()).friendId(target.getId()).build(),
                    Friendship.builder().userId(target.getId()).friendId(owner.getId()).build()));
        } catch (DataIntegrityViolationException exception) {
            /*
             * Two concurrent adds can both pass the check above; uk_friendship_pair is what
             * actually decides. Flushing here is what lets the loser become a 409 from this
             * method rather than a generic conflict raised later at commit time.
             */
            throw new AlreadyFriendsException();
        }

        log.info("Created friendship between user id={} and user id={}", owner.getId(), target.getId());
        return UserSummaryResponse.from(target);
    }

    /** A user's friends, name and profile image, ordered for display (FR-10). */
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> listFriends(Long userId) {
        /*
         * An unknown user is a 404, not an empty list: "this user has no friends" and "this user
         * does not exist" are different answers, and a client that cannot tell them apart will
         * render an empty friends tab for what is really a bad id.
         */
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }

        return friendshipRepository.findFriendsOf(userId).stream()
                .map(UserSummaryResponse::from)
                .toList();
    }

    /**
     * Resolves the user to add. Exactly one identifier is present -- AddFriendRequest enforces
     * that -- so which branch runs is decided by which one it is.
     */
    private User resolveTarget(AddFriendRequest request) {
        if (request.email() != null) {
            return userRepository.findByEmail(User.normaliseEmail(request.email()))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No registered user has this email address"));
        }

        List<User> matches = userRepository.findByNameIgnoreCase(request.username());

        if (matches.isEmpty()) {
            throw new ResourceNotFoundException("No registered user has this username");
        }
        if (matches.size() > 1) {
            throw new AmbiguousUserException(matches.size());
        }
        return matches.getFirst();
    }
}
