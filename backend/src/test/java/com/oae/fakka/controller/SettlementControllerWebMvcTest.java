package com.oae.fakka.controller;

import com.oae.fakka.dto.CreateSettlementRequest;
import com.oae.fakka.dto.MarkSettlementPaidRequest;
import com.oae.fakka.dto.SettlementResponse;
import com.oae.fakka.dto.SuggestedSettlementResponse;
import com.oae.fakka.entity.SettlementStatus;
import com.oae.fakka.exception.NonGroupMemberException;
import com.oae.fakka.exception.NotSettlementPartyException;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.exception.SettlementAlreadyPaidException;
import com.oae.fakka.service.SettlementService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link SettlementController} with {@link SettlementService} mocked.
 * <p>
 * Three things live at this layer and nowhere else. The direction of a payment has to survive
 * the wire, because the pair of ids is its whole meaning and swapping them would tell two people
 * to pay each other backwards. A new settlement must come back PENDING with no timestamp. And
 * each refusal has to arrive as the right kind of answer: 403 for a bystander, 409 for a repeat,
 * 400 for a payload that cannot mean anything.
 */
@WebMvcTest(SettlementController.class)
@ActiveProfiles("dev")
class SettlementControllerWebMvcTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-10T12:34:56.789Z");
    private static final Instant PAID_AT = Instant.parse("2026-09-11T08:00:00Z");

    private static final SettlementResponse PENDING = new SettlementResponse(
            100L, 10L, 2L, 1L, 20_000L, SettlementStatus.PENDING, CREATED_AT, null);
    private static final SettlementResponse PAID = new SettlementResponse(
            100L, 10L, 2L, 1L, 20_000L, SettlementStatus.PAID, CREATED_AT, PAID_AT);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettlementService settlementService;

    @Test
    void suggestedSettlementsAreReturnedInOrderWithTheirDirection() throws Exception {
        given(settlementService.suggestSettlements(10L)).willReturn(List.of(
                new SuggestedSettlementResponse(2L, 1L, 20_000L),
                new SuggestedSettlementResponse(3L, 1L, 15_000L)));

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

    @Test
    void createSettlementReturns201WithThePendingRecord() throws Exception {
        given(settlementService.createSettlement(anyLong(), any(CreateSettlementRequest.class)))
                .willReturn(PENDING);

        mockMvc.perform(post("/api/groups/{groupId}/settlements", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromUserId":2,"toUserId":1,"amount":20000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.groupId").value(10))
                .andExpect(jsonPath("$.fromUserId").value(2))
                .andExpect(jsonPath("$.toUserId").value(1))
                .andExpect(jsonPath("$.amount").value(20000))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-10T12:34:56.789Z"))
                // Null rather than absent from the shape: PENDING means not paid yet.
                .andExpect(jsonPath("$.paidAt").doesNotExist());
    }

    @Test
    void createSettlementPassesTheGroupFromThePath() throws Exception {
        given(settlementService.createSettlement(anyLong(), any(CreateSettlementRequest.class)))
                .willReturn(PENDING);
        ArgumentCaptor<CreateSettlementRequest> captor =
                ArgumentCaptor.forClass(CreateSettlementRequest.class);

        mockMvc.perform(post("/api/groups/{groupId}/settlements", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromUserId":2,"toUserId":1,"amount":20000}
                                """))
                .andExpect(status().isCreated());

        verify(settlementService).createSettlement(eq(10L), captor.capture());
        assertThat(captor.getValue().fromUserId()).isEqualTo(2L);
        assertThat(captor.getValue().toUserId()).isEqualTo(1L);
        assertThat(captor.getValue().amount()).isEqualTo(20_000L);
    }

    /** Paying yourself moves nothing, so it is a mistake rather than a row worth storing. */
    @Test
    void createSettlementRejectsTheSamePersonOnBothSidesWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/settlements", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromUserId":1,"toUserId":1,"amount":20000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("betweenTwoDifferentPeople: "
                        + "fromUserId and toUserId must be different people"));

        verifyNoInteractions(settlementService);
    }

    @Test
    void createSettlementRejectsANonPositiveAmountWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/settlements", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromUserId":2,"toUserId":1,"amount":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("amount: must be greater than zero"));

        verifyNoInteractions(settlementService);
    }

    @Test
    void createSettlementRejectsAMissingPartyWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/settlements", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toUserId":1,"amount":20000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("fromUserId: must not be null"));

        verifyNoInteractions(settlementService);
    }

    /** The same cap as an expense, because both end up in the same balance sums. */
    @Test
    void createSettlementRejectsAnAbsurdAmountWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/settlements", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromUserId":2,"toUserId":1,"amount":9223372036854775807}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("amount: must be at most 100000000000 piastres"));

        verifyNoInteractions(settlementService);
    }

    @Test
    void createSettlementMapsANonMemberTo400() throws Exception {
        given(settlementService.createSettlement(anyLong(), any(CreateSettlementRequest.class)))
                .willThrow(new NonGroupMemberException(List.of(9L)));

        mockMvc.perform(post("/api/groups/{groupId}/settlements", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromUserId":9,"toUserId":1,"amount":20000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Users [9] are not members of this group"))
                .andExpect(jsonPath("$.path").value("/api/groups/10/settlements"));
    }

    @Test
    void createSettlementMapsAnUnknownGroupTo404() throws Exception {
        given(settlementService.createSettlement(anyLong(), any(CreateSettlementRequest.class)))
                .willThrow(new ResourceNotFoundException("Group", 99L));

        mockMvc.perform(post("/api/groups/{groupId}/settlements", 99)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromUserId":2,"toUserId":1,"amount":20000}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Group 99 was not found"));
    }

    @Test
    void markPaidReturns200WithTheStampedRecord() throws Exception {
        given(settlementService.markPaid(anyLong(), any(MarkSettlementPaidRequest.class)))
                .willReturn(PAID);

        mockMvc.perform(patch("/api/settlements/{settlementId}/pay", 100)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").value("2026-09-11T08:00:00Z"));
    }

    @Test
    void markPaidPassesTheSettlementIdAndTheClaimingParty() throws Exception {
        given(settlementService.markPaid(anyLong(), any(MarkSettlementPaidRequest.class)))
                .willReturn(PAID);
        ArgumentCaptor<MarkSettlementPaidRequest> captor =
                ArgumentCaptor.forClass(MarkSettlementPaidRequest.class);

        mockMvc.perform(patch("/api/settlements/{settlementId}/pay", 100)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":2}
                                """))
                .andExpect(status().isOk());

        verify(settlementService).markPaid(eq(100L), captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(2L);
    }

    /** Without a claimed identity there is nobody to check against, so it never reaches the service. */
    @Test
    void markPaidRejectsAMissingUserIdWithoutCallingTheService() throws Exception {
        mockMvc.perform(patch("/api/settlements/{settlementId}/pay", 100)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("userId: must not be null"));

        verifyNoInteractions(settlementService);
    }

    @Test
    void markPaidRejectsAMissingBodyWithoutCallingTheService() throws Exception {
        mockMvc.perform(patch("/api/settlements/{settlementId}/pay", 100)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));

        verifyNoInteractions(settlementService);
    }

    /** FR-35: a bystander gets a 403, which is a permission answer rather than a bad request. */
    @Test
    void markPaidMapsANonPartyTo403() throws Exception {
        given(settlementService.markPaid(anyLong(), any(MarkSettlementPaidRequest.class)))
                .willThrow(new NotSettlementPartyException());

        mockMvc.perform(patch("/api/settlements/{settlementId}/pay", 100)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":9}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message")
                        .value("Only the payer or the recipient can mark this settlement paid"))
                .andExpect(jsonPath("$.path").value("/api/settlements/100/pay"));
    }

    @Test
    void markPaidMapsAnAlreadyPaidSettlementTo409() throws Exception {
        given(settlementService.markPaid(anyLong(), any(MarkSettlementPaidRequest.class)))
                .willThrow(new SettlementAlreadyPaidException(100L, PAID_AT));

        mockMvc.perform(patch("/api/settlements/{settlementId}/pay", 100)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":2}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("Settlement 100 was already marked paid at " + PAID_AT));
    }

    @Test
    void markPaidMapsAnUnknownSettlementTo404() throws Exception {
        given(settlementService.markPaid(anyLong(), any(MarkSettlementPaidRequest.class)))
                .willThrow(new ResourceNotFoundException("Settlement", 99L));

        mockMvc.perform(patch("/api/settlements/{settlementId}/pay", 99)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":2}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Settlement 99 was not found"));
    }

    @Test
    void markPaidRejectsANonPositiveSettlementId() throws Exception {
        mockMvc.perform(patch("/api/settlements/{settlementId}/pay", 0)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":2}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(settlementService);
    }
}
