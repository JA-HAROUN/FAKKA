package com.oae.fakka.controller;

import com.oae.fakka.dto.SettlementResponse;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.service.SettlementService;
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
 * Web-layer tests for {@link SettlementController} with {@link SettlementService} mocked.
 * <p>
 * The matching itself is covered in {@code DebtSimplificationServiceTest}. Here the question is
 * only whether the direction survives the wire: the pair of ids is the whole meaning of a
 * suggested payment, and swapping them would tell two people to pay each other backwards.
 */
@WebMvcTest(SettlementController.class)
@ActiveProfiles("dev")
class SettlementControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettlementService settlementService;

    @Test
    void suggestedSettlementsAreReturnedInOrderWithTheirDirection() throws Exception {
        given(settlementService.suggestSettlements(10L)).willReturn(List.of(
                new SettlementResponse(2L, 1L, 20_000L),
                new SettlementResponse(3L, 1L, 15_000L)));

        mockMvc.perform(get("/api/groups/{groupId}/settlements/suggested", 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fromUserId").value(2))
                .andExpect(jsonPath("$[0].toUserId").value(1))
                .andExpect(jsonPath("$[0].amount").value(20000))
                .andExpect(jsonPath("$[1].fromUserId").value(3))
                .andExpect(jsonPath("$[1].toUserId").value(1))
                .andExpect(jsonPath("$[1].amount").value(15000));
    }

    /** A settled group is an empty array, not a 404 and not a null. */
    @Test
    void aSettledGroupReturnsAnEmptyArray() throws Exception {
        given(settlementService.suggestSettlements(10L)).willReturn(List.of());

        mockMvc.perform(get("/api/groups/{groupId}/settlements/suggested", 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void suggestedSettlementsMapAnUnknownGroupTo404() throws Exception {
        given(settlementService.suggestSettlements(99L))
                .willThrow(new ResourceNotFoundException("Group", 99L));

        mockMvc.perform(get("/api/groups/{groupId}/settlements/suggested", 99))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Group 99 was not found"))
                .andExpect(jsonPath("$.path").value("/api/groups/99/settlements/suggested"));
    }

    @Test
    void suggestedSettlementsRejectANonPositiveGroupId() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/settlements/suggested", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(settlementService, never()).suggestSettlements(anyLong());
    }

    @Test
    void suggestedSettlementsRejectANonNumericGroupId() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/settlements/suggested", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'groupId' has an invalid value"));

        verifyNoInteractions(settlementService);
    }
}
