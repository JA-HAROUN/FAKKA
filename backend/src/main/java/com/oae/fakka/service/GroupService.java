package com.oae.fakka.service;

import com.oae.fakka.dto.CreateGroupRequest;
import com.oae.fakka.dto.GroupCardResponse;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SequencedSet;
import java.util.stream.Collectors;

/**
 * Group creation, membership, and the group cards on the personal dashboard
 * (FR-4 to FR-7, FR-11).
 */
@Slf4j
@Service
public class GroupService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final BalanceService balanceService;

    public GroupService(
            UserRepository userRepository,
            FriendshipRepository friendshipRepository,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            BalanceService balanceService) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.balanceService = balanceService;
    }

    /**
     * Creates the group and its member rows, creator included.
     * <p>
     * Transactional because a group is meaningless without its members: a commit that saved the
     * group and then failed on the member rows would leave an empty group that no endpoint can
     * repair, and the creator would not even be in it.
     */
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request) {
        User creator = userRepository.findById(request.createdBy())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.createdBy()));

        SequencedSet<Long> memberIds = resolveMembers(creator, request.memberUserIds());

        Group group = groupRepository.saveAndFlush(Group.builder()
                .name(request.name())
                .imageUrl(request.imageUrl())
                .createdBy(creator.getId())
                .build());

        List<GroupMember> members = new ArrayList<>(memberIds.size());
        for (Long userId : memberIds) {
            members.add(GroupMember.builder().groupId(group.getId()).userId(userId).build());
        }
        groupMemberRepository.saveAllAndFlush(members);

        log.info("User id={} created group id={} with {} members",
                creator.getId(), group.getId(), members.size());
        return GroupResponse.of(group, members.size());
    }

    /** The members of a group, name and profile image, ordered for display (FR-11). */
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> listMembers(Long groupId) {
        /*
         * An unknown group is a 404 rather than an empty list, for the same reason as the friend
         * list: every group has at least its creator, so an empty member list can only mean a
         * bad id, and a client that cannot tell the two apart renders an empty group page.
         */
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group", groupId);
        }

        return groupMemberRepository.findMembersOf(groupId).stream()
                .map(UserSummaryResponse::from)
                .toList();
    }

    /**
     * The dashboard: every group the user belongs to, with its size and their standing in it
     * (FR-4, FR-5).
     * <p>
     * Four queries in total, whatever the number of groups: the groups, their member counts, and
     * the two halves of the balance for all of them at once.
     */
    @Transactional(readOnly = true)
    public List<GroupCardResponse> listGroupsForUser(Long userId) {
        /*
         * An unknown user is a 404 rather than an empty dashboard, for the same reason as the
         * friend list: a client that cannot tell "no groups yet" from "no such user" renders the
         * empty state for what is really a bad id.
         */
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }

        List<Group> groups = groupRepository.findGroupsOf(userId);
        if (groups.isEmpty()) {
            // Nothing to count, and the count query must not be handed an empty IN list.
            return List.of();
        }

        List<Long> groupIds = groups.stream().map(Group::getId).toList();
        Map<Long, Long> memberCounts = groupMemberRepository
                .countMembersOf(groupIds).stream()
                .collect(Collectors.toMap(
                        GroupMemberRepository.MemberCount::getGroupId,
                        GroupMemberRepository.MemberCount::getMemberCount));

        /*
         * One balance lookup for the whole dashboard rather than one per card: now that the
         * engine reads expenses, a per-group call would scan a twenty-group dashboard twenty
         * times over.
         */
        Map<Long, Long> balances = balanceService.calculateUserBalanceInGroups(userId, groupIds);

        return groups.stream()
                .map(group -> GroupCardResponse.of(
                        group,
                        /*
                         * The user is a member of every group listed here, so a count is always
                         * present. Defaulting rather than unboxing null keeps a dashboard
                         * rendering if that ever stops being true.
                         */
                        Math.toIntExact(memberCounts.getOrDefault(group.getId(), 0L)),
                        balances.getOrDefault(group.getId(), 0L)))
                .toList();
    }

    /**
     * The final member set: the creator first, then the requested friends in the order given.
     * <p>
     * Ordered and deduplicated as one step, which is what makes "at least the creator" and "no
     * duplicate members" hold by construction rather than by a later check -- the creator is
     * always element one, and a set cannot repeat an id even when the creator also appears in
     * the request.
     */
    private SequencedSet<Long> resolveMembers(User creator, List<Long> requestedIds) {
        SequencedSet<Long> memberIds = new LinkedHashSet<>();
        memberIds.add(creator.getId());
        memberIds.addAll(requestedIds);

        SequencedSet<Long> others = new LinkedHashSet<>(memberIds);
        others.remove(creator.getId());
        if (others.isEmpty()) {
            // Nothing to validate, and the friend lookup must not be handed an empty IN list.
            return memberIds;
        }

        Set<Long> friendIds = friendshipRepository.findFriendIdsAmong(creator.getId(), others);
        List<Long> strangers = others.stream().filter(id -> !friendIds.contains(id)).toList();
        if (!strangers.isEmpty()) {
            throw new NonFriendMemberException(strangers);
        }

        return memberIds;
    }
}
