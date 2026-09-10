package com.oae.fakka.controller;

import com.oae.fakka.dto.AddFriendRequest;
import com.oae.fakka.dto.UserSummaryResponse;
import com.oae.fakka.exception.AlreadyFriendsException;
import com.oae.fakka.exception.AmbiguousUserException;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.exception.SelfFriendshipException;
import com.oae.fakka.service.FriendService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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
 * Web-layer tests for {@link FriendController} with {@link FriendService} mocked.
 * <p>
 * These cover what the HTTP edge owns: the exactly-one-identifier rule, request-parameter
 * validation, and the mapping from each service exception to a status. Whether a friendship is
 * actually stored as a mirrored pair is a service concern, tested in {@code FriendServiceTest}.
 */
@WebMvcTest(FriendController.class)
@ActiveProfiles("dev")
class FriendControllerWebMvcTest {

    private static final UserSummaryResponse MOHAMED =
            new UserSummaryResponse(2L, "Mohamed Salah", "https://img.example.com/m.jpg");
    private static final UserSummaryResponse BILAL =
            new UserSummaryResponse(3L, "Bilal Omar", null);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FriendService friendService;

    @Test
    void addFriendReturns201WithTheNewFriend() throws Exception {
        given(friendService.addFriend(any(AddFriendRequest.class))).willReturn(MOHAMED);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"email":"mohamed@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.name").value("Mohamed Salah"))
                .andExpect(jsonPath("$.profileImageUrl").value("https://img.example.com/m.jpg"))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void addFriendPassesTheStrippedIdentifierToTheService() throws Exception {
        given(friendService.addFriend(any(AddFriendRequest.class))).willReturn(MOHAMED);
        ArgumentCaptor<AddFriendRequest> captor = ArgumentCaptor.forClass(AddFriendRequest.class);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"email":"  MOHAMED@Example.COM "}
                                """))
                .andExpect(status().isCreated());

        verify(friendService).addFriend(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(1L);
        assertThat(captor.getValue().email()).isEqualTo("MOHAMED@Example.COM");
        assertThat(captor.getValue().username()).isNull();
    }

    /** Both identifiers could point at different accounts, so there is no safe way to proceed. */
    @Test
    void addFriendRejectsBothIdentifiersWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"email":"mohamed@example.com","username":"Mohamed Salah"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "exactlyOneIdentifier: exactly one of email or username must be provided"));

        verifyNoInteractions(friendService);
    }

    @Test
    void addFriendRejectsMissingIdentifierWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(friendService);
    }

    /** A blank email is not an identifier, so this must reach the service as a username lookup. */
    @Test
    void addFriendTreatsBlankEmailAsAbsent() throws Exception {
        given(friendService.addFriend(any(AddFriendRequest.class))).willReturn(MOHAMED);
        ArgumentCaptor<AddFriendRequest> captor = ArgumentCaptor.forClass(AddFriendRequest.class);

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"email":"   ","username":"Mohamed Salah"}
                                """))
                .andExpect(status().isCreated());

        verify(friendService).addFriend(captor.capture());
        assertThat(captor.getValue().email()).isNull();
        assertThat(captor.getValue().username()).isEqualTo("Mohamed Salah");
    }

    @Test
    void addFriendRejectsMissingUserIdWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"mohamed@example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("userId: must not be null"));

        verifyNoInteractions(friendService);
    }

    @Test
    void addFriendRejectsNonPositiveUserIdWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":0,"email":"mohamed@example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("userId: must be a positive id"));

        verifyNoInteractions(friendService);
    }

    @Test
    void addFriendMapsSelfFriendshipTo400() throws Exception {
        given(friendService.addFriend(any(AddFriendRequest.class)))
                .willThrow(new SelfFriendshipException());

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"email":"ahmed@example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot add yourself as a friend"));
    }

    @Test
    void addFriendMapsExistingFriendshipTo409() throws Exception {
        given(friendService.addFriend(any(AddFriendRequest.class)))
                .willThrow(new AlreadyFriendsException());

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"email":"mohamed@example.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You are already friends with this user"));
    }

    @Test
    void addFriendMapsAmbiguousUsernameTo409() throws Exception {
        given(friendService.addFriend(any(AddFriendRequest.class)))
                .willThrow(new AmbiguousUserException(3));

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"username":"Mohamed Salah"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "3 users share this name. Add this friend by email address instead."));
    }

    @Test
    void addFriendMapsUnknownTargetTo404() throws Exception {
        given(friendService.addFriend(any(AddFriendRequest.class)))
                .willThrow(new ResourceNotFoundException("No registered user has this email address"));

        mockMvc.perform(post("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"email":"nobody@example.com"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/friends"));
    }

    @Test
    void listFriendsReturnsTheServiceListAsJson() throws Exception {
        given(friendService.listFriends(1L)).willReturn(List.of(BILAL, MOHAMED));

        mockMvc.perform(get("/api/friends").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(3))
                .andExpect(jsonPath("$[0].name").value("Bilal Omar"))
                .andExpect(jsonPath("$[0].profileImageUrl").doesNotExist())
                .andExpect(jsonPath("$[1].name").value("Mohamed Salah"));
    }

    @Test
    void listFriendsReturnsAnEmptyArrayWhenTheServiceReturnsNone() throws Exception {
        given(friendService.listFriends(1L)).willReturn(List.of());

        mockMvc.perform(get("/api/friends").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listFriendsMapsUnknownUserTo404() throws Exception {
        given(friendService.listFriends(99L)).willThrow(new ResourceNotFoundException("User", 99L));

        mockMvc.perform(get("/api/friends").param("userId", "99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User 99 was not found"));
    }

    @Test
    void listFriendsRequiresTheUserIdParameter() throws Exception {
        mockMvc.perform(get("/api/friends"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required parameter 'userId' is missing"));

        verifyNoInteractions(friendService);
    }

    @Test
    void listFriendsRejectsNonNumericUserId() throws Exception {
        mockMvc.perform(get("/api/friends").param("userId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'userId' has an invalid value"));

        verifyNoInteractions(friendService);
    }

    @Test
    void listFriendsRejectsNonPositiveUserId() throws Exception {
        mockMvc.perform(get("/api/friends").param("userId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(friendService, never()).listFriends(anyLong());
    }
}
