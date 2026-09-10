package com.oae.fakka.controller;

import com.oae.fakka.dto.BalanceStatus;
import com.oae.fakka.dto.GroupCardResponse;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.service.GroupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link UserController} with {@link GroupService} mocked.
 * <p>
 * The point of mocking here is that the balance engine does not exist: a stubbed service can
 * return a non-zero balance, which is the only way to prove now that the wire format carries a
 * negative number and its matching status correctly. The end-to-end test asserts what the real
 * stub produces today, which is zero.
 */
@WebMvcTest(UserController.class)
@ActiveProfiles("dev")
class UserControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroupService groupService;

    @Test
    void listGroupsReturnsACardPerGroup() throws Exception {
        given(groupService.listGroupsForUser(1L)).willReturn(List.of(
                new GroupCardResponse(10L, "Dinner", "https://img.example.com/dinner.jpg", 5,
                        35_000L, BalanceStatus.POSITIVE),
                new GroupCardResponse(11L, "Trip", null, 2, -12_550L, BalanceStatus.NEGATIVE),
                new GroupCardResponse(12L, "Coffee", null, 3, 0L, BalanceStatus.SETTLED)));

        mockMvc.perform(get("/api/users/{userId}/groups", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].groupId").value(10))
                .andExpect(jsonPath("$[0].name").value("Dinner"))
                .andExpect(jsonPath("$[0].imageUrl").value("https://img.example.com/dinner.jpg"))
                .andExpect(jsonPath("$[0].memberCount").value(5))
                .andExpect(jsonPath("$[0].userBalance").value(35000))
                .andExpect(jsonPath("$[0].status").value("POSITIVE"))
                .andExpect(jsonPath("$[1].imageUrl").doesNotExist())
                .andExpect(jsonPath("$[1].userBalance").value(-12550))
                .andExpect(jsonPath("$[1].status").value("NEGATIVE"))
                .andExpect(jsonPath("$[2].userBalance").value(0))
                .andExpect(jsonPath("$[2].status").value("SETTLED"));
    }

    /**
     * The balance must serialise as a whole number of piastres. A decimal here would mean the
     * contract silently changed unit, and every client would be out by a factor of 100.
     */
    @Test
    void balanceIsSerialisedAsAnIntegerNumberOfPiastres() throws Exception {
        given(groupService.listGroupsForUser(1L)).willReturn(List.of(
                new GroupCardResponse(10L, "Dinner", null, 2, -12_550L, BalanceStatus.NEGATIVE)));

        mockMvc.perform(get("/api/users/{userId}/groups", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userBalance").isNumber())
                .andExpect(jsonPath("$[0].userBalance").value(-12550));
    }

    /** A user with no groups yet is an empty dashboard, not an error. */
    @Test
    void listGroupsReturnsAnEmptyArrayWhenTheUserHasNoGroups() throws Exception {
        given(groupService.listGroupsForUser(1L)).willReturn(List.of());

        mockMvc.perform(get("/api/users/{userId}/groups", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listGroupsMapsUnknownUserTo404() throws Exception {
        given(groupService.listGroupsForUser(99L)).willThrow(new ResourceNotFoundException("User", 99L));

        mockMvc.perform(get("/api/users/{userId}/groups", 99))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User 99 was not found"))
                .andExpect(jsonPath("$.path").value("/api/users/99/groups"));
    }

    @Test
    void listGroupsRejectsNonPositiveUserId() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/groups", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(groupService, never()).listGroupsForUser(anyLong());
    }

    @Test
    void listGroupsRejectsNonNumericUserId() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/groups", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'userId' has an invalid value"));

        verifyNoInteractions(groupService);
    }
}
