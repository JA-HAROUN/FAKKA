package com.oae.fakka.controller;

import com.oae.fakka.dto.CreateGroupRequest;
import com.oae.fakka.dto.GroupResponse;
import com.oae.fakka.dto.UserSummaryResponse;
import com.oae.fakka.exception.NonFriendMemberException;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.service.GroupService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link GroupController} with {@link GroupService} mocked.
 * <p>
 * The payload rules live on {@code CreateGroupRequest}, so this is where they are provable in
 * isolation: a duplicate id, an over-long member list or a malformed image URL must all be
 * refused before any service call. Who may be a member is a service rule, tested in
 * {@code GroupServiceTest}.
 */
@WebMvcTest(GroupController.class)
@ActiveProfiles("dev")
class GroupControllerWebMvcTest {

    private static final GroupResponse DINNER = new GroupResponse(
            10L, "Dinner", "https://img.example.com/dinner.jpg", 1L,
            Instant.parse("2026-09-10T12:34:56.789Z"), 3);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroupService groupService;

    @Test
    void createGroupReturns201WithTheCreatedGroup() throws Exception {
        given(groupService.createGroup(any(CreateGroupRequest.class))).willReturn(DINNER);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":1,"name":"Dinner","memberUserIds":[2,3]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Dinner"))
                .andExpect(jsonPath("$.imageUrl").value("https://img.example.com/dinner.jpg"))
                .andExpect(jsonPath("$.createdBy").value(1))
                .andExpect(jsonPath("$.createdAt").value("2026-09-10T12:34:56.789Z"))
                .andExpect(jsonPath("$.memberCount").value(3));
    }

    @Test
    void createGroupPassesTheStrippedPayloadToTheService() throws Exception {
        given(groupService.createGroup(any(CreateGroupRequest.class))).willReturn(DINNER);
        ArgumentCaptor<CreateGroupRequest> captor = ArgumentCaptor.forClass(CreateGroupRequest.class);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":1,"name":"  Dinner  ","imageUrl":"   ","memberUserIds":[2,3]}
                                """))
                .andExpect(status().isCreated());

        verify(groupService).createGroup(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Dinner");
        assertThat(captor.getValue().imageUrl()).isNull();
        assertThat(captor.getValue().memberUserIds()).containsExactly(2L, 3L);
    }

    /** An absent list and an empty list must reach the service identically. */
    @Test
    void createGroupTurnsAnAbsentMemberListIntoAnEmptyOne() throws Exception {
        given(groupService.createGroup(any(CreateGroupRequest.class))).willReturn(DINNER);
        ArgumentCaptor<CreateGroupRequest> captor = ArgumentCaptor.forClass(CreateGroupRequest.class);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":1,"name":"Just me"}
                                """))
                .andExpect(status().isCreated());

        verify(groupService).createGroup(captor.capture());
        assertThat(captor.getValue().memberUserIds()).isEmpty();
    }

    @Test
    void createGroupRejectsDuplicateMemberIdsWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":1,"name":"Dinner","memberUserIds":[2,2]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "memberUserIdsDistinct: must not contain duplicate ids"));

        verifyNoInteractions(groupService);
    }

    /** The cap keeps one request from inserting an unbounded number of member rows. */
    @Test
    void createGroupRejectsMoreThan99MembersWithoutCallingTheService() throws Exception {
        String ids = IntStream.rangeClosed(2, 101)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(","));

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":1,"name":"Huge","memberUserIds":[%s]}
                                """.formatted(ids)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "memberUserIds: must not contain more than 99 ids"));

        verifyNoInteractions(groupService);
    }

    @Test
    void createGroupRejectsBlankNameWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":1,"name":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name: must not be blank"));

        verifyNoInteractions(groupService);
    }

    @Test
    void createGroupRejectsMalformedImageUrlWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":1,"name":"Dinner","imageUrl":"not a url"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("imageUrl: must be a valid URL"));

        verifyNoInteractions(groupService);
    }

    @Test
    void createGroupRejectsMissingCreatorWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Dinner"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("createdBy: must not be null"));

        verifyNoInteractions(groupService);
    }

    @Test
    void createGroupRejectsANullMemberIdWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":1,"name":"Dinner","memberUserIds":[2,null]}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupService);
    }

    @Test
    void createGroupMapsNonFriendMembersTo400() throws Exception {
        given(groupService.createGroup(any(CreateGroupRequest.class)))
                .willThrow(new NonFriendMemberException(List.of(3L, 4L)));

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":1,"name":"Dinner","memberUserIds":[3,4]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Users [3, 4] cannot be added: a group member must be a friend of the creator"))
                .andExpect(jsonPath("$.path").value("/api/groups"));
    }

    @Test
    void createGroupMapsUnknownCreatorTo404() throws Exception {
        given(groupService.createGroup(any(CreateGroupRequest.class)))
                .willThrow(new ResourceNotFoundException("User", 99L));

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"createdBy":99,"name":"Dinner"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User 99 was not found"));
    }

    @Test
    void listMembersReturnsTheServiceListAsJson() throws Exception {
        given(groupService.listMembers(10L)).willReturn(List.of(
                new UserSummaryResponse(1L, "Ahmed Ragy", null),
                new UserSummaryResponse(2L, "Mohamed Salah", "https://img.example.com/m.jpg")));

        mockMvc.perform(get("/api/groups/{id}/members", 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(1))
                .andExpect(jsonPath("$[0].name").value("Ahmed Ragy"))
                .andExpect(jsonPath("$[0].profileImageUrl").doesNotExist())
                .andExpect(jsonPath("$[1].profileImageUrl").value("https://img.example.com/m.jpg"))
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    void listMembersMapsUnknownGroupTo404() throws Exception {
        given(groupService.listMembers(99L)).willThrow(new ResourceNotFoundException("Group", 99L));

        mockMvc.perform(get("/api/groups/{id}/members", 99))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Group 99 was not found"))
                .andExpect(jsonPath("$.path").value("/api/groups/99/members"));
    }

    @Test
    void listMembersRejectsNonPositiveGroupId() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/members", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(groupService, never()).listMembers(anyLong());
    }

    @Test
    void listMembersRejectsNonNumericGroupId() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/members", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'id' has an invalid value"));

        verifyNoInteractions(groupService);
    }
}
