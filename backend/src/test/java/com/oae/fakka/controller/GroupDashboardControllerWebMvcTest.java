package com.oae.fakka.controller;

import com.oae.fakka.dto.BalanceStatus;
import com.oae.fakka.dto.ExpenseResponse;
import com.oae.fakka.dto.ExpenseShareResponse;
import com.oae.fakka.dto.GroupDashboardResponse;
import com.oae.fakka.dto.GroupResponse;
import com.oae.fakka.dto.MemberBalanceResponse;
import com.oae.fakka.dto.PagedResponse;
import com.oae.fakka.dto.SettlementResponse;
import com.oae.fakka.dto.SuggestedSettlementResponse;
import com.oae.fakka.entity.ExpenseCategory;
import com.oae.fakka.entity.SettlementStatus;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.service.GroupDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link GroupDashboardController} with {@link GroupDashboardService} mocked.
 * <p>
 * The composition is tested in {@code GroupDashboardServiceTest}. What this layer owns is the
 * paging window -- defaults when it is omitted, a refusal when it is nonsense -- and the shape of
 * the envelope, since a client renders the whole screen from these field names.
 */
@WebMvcTest(GroupDashboardController.class)
@ActiveProfiles("dev")
class GroupDashboardControllerWebMvcTest {

    private static final Instant WHEN = Instant.parse("2026-09-10T12:34:56.789Z");

    private static final GroupDashboardResponse DASHBOARD = new GroupDashboardResponse(
            new GroupResponse(10L, "Dinner", "https://img.example.com/dinner.jpg", 1L, WHEN, 3),
            List.of(new MemberBalanceResponse(1L, "Ahmed Ragy", null,
                    90_000L, 30_000L, 0L, 0L, 60_000L, BalanceStatus.POSITIVE)),
            90_000L,
            new PagedResponse<>(List.of(new ExpenseResponse(
                    100L, 10L, ExpenseCategory.FOOD, "Dinner", 90_000L, null, 1L, WHEN,
                    List.of(new ExpenseShareResponse(1L, 30_000L)))), 0, 20, 1, 1),
            List.of(new SuggestedSettlementResponse(2L, 1L, 30_000L)),
            List.of(new SettlementResponse(
                    200L, 10L, 3L, 1L, 30_000L, SettlementStatus.PENDING, WHEN, null)));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroupDashboardService groupDashboardService;

    @Test
    void theDashboardCarriesEverySectionOfTheGroupScreen() throws Exception {
        given(groupDashboardService.loadDashboard(anyLong(), anyInt(), anyInt()))
                .willReturn(DASHBOARD);

        mockMvc.perform(get("/api/groups/{groupId}/dashboard", 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group.id").value(10))
                .andExpect(jsonPath("$.group.name").value("Dinner"))
                .andExpect(jsonPath("$.group.memberCount").value(3))
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].net").value(60000))
                .andExpect(jsonPath("$.totalGroupExpenses").value(90000))
                .andExpect(jsonPath("$.expenses.content.length()").value(1))
                .andExpect(jsonPath("$.expenses.content[0].id").value(100))
                .andExpect(jsonPath("$.expenses.content[0].participants[0].shareAmount").value(30000))
                .andExpect(jsonPath("$.expenses.page").value(0))
                .andExpect(jsonPath("$.expenses.size").value(20))
                .andExpect(jsonPath("$.expenses.totalElements").value(1))
                .andExpect(jsonPath("$.expenses.totalPages").value(1))
                .andExpect(jsonPath("$.suggestedSettlements.length()").value(1))
                .andExpect(jsonPath("$.suggestedSettlements[0].amount").value(30000))
                .andExpect(jsonPath("$.pendingSettlements.length()").value(1))
                .andExpect(jsonPath("$.pendingSettlements[0].id").value(200))
                .andExpect(jsonPath("$.pendingSettlements[0].status").value("PENDING"));
    }

    /** The page envelope is ours, so none of Spring paging internals should leak into it. */
    @Test
    void thePageEnvelopeExposesOnlyTheFourFieldsWeControl() throws Exception {
        given(groupDashboardService.loadDashboard(anyLong(), anyInt(), anyInt()))
                .willReturn(DASHBOARD);

        mockMvc.perform(get("/api/groups/{groupId}/dashboard", 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenses.pageable").doesNotExist())
                .andExpect(jsonPath("$.expenses.sort").doesNotExist())
                .andExpect(jsonPath("$.expenses.first").doesNotExist())
                .andExpect(jsonPath("$.expenses.numberOfElements").doesNotExist());
    }

    @Test
    void thePagingWindowDefaultsToTheFirstTwentyExpenses() throws Exception {
        given(groupDashboardService.loadDashboard(anyLong(), anyInt(), anyInt()))
                .willReturn(DASHBOARD);

        mockMvc.perform(get("/api/groups/{groupId}/dashboard", 10))
                .andExpect(status().isOk());

        verify(groupDashboardService).loadDashboard(10L, 0, 20);
    }

    @Test
    void thePagingWindowIsTakenFromTheQueryString() throws Exception {
        given(groupDashboardService.loadDashboard(anyLong(), anyInt(), anyInt()))
                .willReturn(DASHBOARD);

        mockMvc.perform(get("/api/groups/{groupId}/dashboard", 10)
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isOk());

        verify(groupDashboardService).loadDashboard(10L, 2, 50);
    }

    @Test
    void aNegativePageIsRejected() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/dashboard", 10).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(groupDashboardService, never()).loadDashboard(anyLong(), anyInt(), anyInt());
    }

    @Test
    void aZeroPageSizeIsRejected() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/dashboard", 10).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(groupDashboardService, never()).loadDashboard(anyLong(), anyInt(), anyInt());
    }

    /** The cap is what stops a caller pulling a whole history through the dashboard. */
    @Test
    void aPageSizeAboveTheCapIsRejected() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/dashboard", 10).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(groupDashboardService, never()).loadDashboard(anyLong(), anyInt(), anyInt());
    }

    @Test
    void aNonNumericPageIsRejected() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/dashboard", 10).param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'page' has an invalid value"));

        verifyNoInteractions(groupDashboardService);
    }

    @Test
    void anUnknownGroupIsA404() throws Exception {
        given(groupDashboardService.loadDashboard(anyLong(), anyInt(), anyInt()))
                .willThrow(new ResourceNotFoundException("Group", 99L));

        mockMvc.perform(get("/api/groups/{groupId}/dashboard", 99))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Group 99 was not found"))
                .andExpect(jsonPath("$.path").value("/api/groups/99/dashboard"));
    }

    @Test
    void aNonPositiveGroupIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/dashboard", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(groupDashboardService, never()).loadDashboard(anyLong(), anyInt(), anyInt());
    }
}
