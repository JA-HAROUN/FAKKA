package com.oae.fakka.controller;

import com.oae.fakka.dto.BalanceStatus;
import com.oae.fakka.dto.MemberBalanceResponse;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.service.BalanceService;
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
 * Web-layer tests for {@link BalanceController} with {@link BalanceService} mocked.
 * <p>
 * The arithmetic is covered in {@code BalanceServiceTest}; what matters here is that both halves
 * and the net reach the client as whole piastres, and that a negative net survives the wire
 * intact -- a balance that lost its sign somewhere in serialisation would read as a debt owed the
 * wrong way round.
 */
@WebMvcTest(BalanceController.class)
@ActiveProfiles("dev")
class BalanceControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BalanceService balanceService;

    @Test
    void listBalancesReturnsPaidOwedAndNetForEveryMember() throws Exception {
        given(balanceService.listMemberBalances(10L)).willReturn(List.of(
                new MemberBalanceResponse(1L, "Ahmed Ragy", null,
                        90_000L, 30_000L, 60_000L, BalanceStatus.POSITIVE),
                new MemberBalanceResponse(2L, "Mohamed Salah", "https://img.example.com/m.jpg",
                        0L, 30_000L, -30_000L, BalanceStatus.NEGATIVE),
                new MemberBalanceResponse(3L, "Zeinab Hassan", null,
                        30_000L, 30_000L, 0L, BalanceStatus.SETTLED)));

        mockMvc.perform(get("/api/groups/{groupId}/balances", 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].userId").value(1))
                .andExpect(jsonPath("$[0].name").value("Ahmed Ragy"))
                .andExpect(jsonPath("$[0].profileImageUrl").doesNotExist())
                .andExpect(jsonPath("$[0].paid").value(90000))
                .andExpect(jsonPath("$[0].owed").value(30000))
                .andExpect(jsonPath("$[0].net").value(60000))
                .andExpect(jsonPath("$[0].status").value("POSITIVE"))
                .andExpect(jsonPath("$[1].net").value(-30000))
                .andExpect(jsonPath("$[1].status").value("NEGATIVE"))
                .andExpect(jsonPath("$[1].profileImageUrl").value("https://img.example.com/m.jpg"))
                .andExpect(jsonPath("$[2].net").value(0))
                .andExpect(jsonPath("$[2].status").value("SETTLED"))
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    /** Whole piastres on the wire, never a decimal: the API has one unit and it is the minor one. */
    @Test
    void balancesAreSerialisedAsWholePiastres() throws Exception {
        given(balanceService.listMemberBalances(10L)).willReturn(List.of(
                new MemberBalanceResponse(1L, "Ahmed Ragy", null,
                        100L, 34L, 66L, BalanceStatus.POSITIVE)));

        mockMvc.perform(get("/api/groups/{groupId}/balances", 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].net").isNumber())
                .andExpect(jsonPath("$[0].net").value(66));
    }

    @Test
    void listBalancesMapsAnUnknownGroupTo404() throws Exception {
        given(balanceService.listMemberBalances(99L))
                .willThrow(new ResourceNotFoundException("Group", 99L));

        mockMvc.perform(get("/api/groups/{groupId}/balances", 99))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Group 99 was not found"))
                .andExpect(jsonPath("$.path").value("/api/groups/99/balances"));
    }

    @Test
    void listBalancesRejectsANonPositiveGroupId() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/balances", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(balanceService, never()).listMemberBalances(anyLong());
    }

    @Test
    void listBalancesRejectsANonNumericGroupId() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/balances", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'groupId' has an invalid value"));

        verifyNoInteractions(balanceService);
    }
}
