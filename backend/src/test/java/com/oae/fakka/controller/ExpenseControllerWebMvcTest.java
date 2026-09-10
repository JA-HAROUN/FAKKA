package com.oae.fakka.controller;

import com.oae.fakka.dto.CreateExpenseRequest;
import com.oae.fakka.dto.ExpenseResponse;
import com.oae.fakka.dto.ExpenseShareResponse;
import com.oae.fakka.dto.SplitType;
import com.oae.fakka.entity.ExpenseCategory;
import com.oae.fakka.exception.NonGroupMemberException;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.exception.SplitParticipantMismatchException;
import com.oae.fakka.exception.SplitTotalMismatchException;
import com.oae.fakka.service.ExpenseService;
import com.oae.fakka.service.NaturalLanguageExpenseService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link ExpenseController} with {@link ExpenseService} mocked.
 * <p>
 * This is where BR-2 and BR-3 are provable at the edge: an expense with no participants, or with
 * a repeated one, must never reach the service, and a payload that disagrees with itself about
 * the split type must be refused before any arithmetic happens. Every rejection asserts the
 * service was not called.
 */
@WebMvcTest(ExpenseController.class)
@ActiveProfiles("dev")
class ExpenseControllerWebMvcTest {

    private static final ExpenseResponse RECORDED = new ExpenseResponse(
            100L, 10L, ExpenseCategory.FOOD, "Dinner at Pizza Hut", 100L, null, 1L,
            Instant.parse("2026-09-10T12:34:56.789Z"),
            List.of(new ExpenseShareResponse(1L, 34L),
                    new ExpenseShareResponse(2L, 33L),
                    new ExpenseShareResponse(3L, 33L)));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService expenseService;

    /**
     * Only required so the context loads: {@link ExpenseController} takes both services as
     * constructor dependencies. The natural-language endpoint has its own web-layer test,
     * {@code ExpenseControllerParseNlWebMvcTest}.
     */
    @MockitoBean
    private NaturalLanguageExpenseService naturalLanguageExpenseService;

    @Test
    void createExpenseReturns201WithTheShares() throws Exception {
        given(expenseService.createExpense(anyLong(), any(CreateExpenseRequest.class))).willReturn(RECORDED);

        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"category":"FOOD","description":"Dinner at Pizza Hut",
                                 "totalAmount":100,"participantUserIds":[1,2,3],"splitType":"EQUAL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.groupId").value(10))
                .andExpect(jsonPath("$.category").value("FOOD"))
                .andExpect(jsonPath("$.description").value("Dinner at Pizza Hut"))
                .andExpect(jsonPath("$.totalAmount").value(100))
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andExpect(jsonPath("$.paidByUserId").value(1))
                .andExpect(jsonPath("$.participants.length()").value(3))
                .andExpect(jsonPath("$.participants[0].userId").value(1))
                .andExpect(jsonPath("$.participants[0].shareAmount").value(34))
                .andExpect(jsonPath("$.participants[1].shareAmount").value(33))
                .andExpect(jsonPath("$.participants[2].shareAmount").value(33));
    }

    @Test
    void createExpensePassesTheGroupFromThePathAndTheStrippedPayload() throws Exception {
        given(expenseService.createExpense(anyLong(), any(CreateExpenseRequest.class))).willReturn(RECORDED);
        ArgumentCaptor<CreateExpenseRequest> captor = ArgumentCaptor.forClass(CreateExpenseRequest.class);

        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"category":"FOOD","description":"  Dinner  ",
                                 "totalAmount":100,"imageUrl":"   ","participantUserIds":[1,2,3]}
                                """))
                .andExpect(status().isCreated());

        verify(expenseService).createExpense(eq(10L), captor.capture());
        assertThat(captor.getValue().description()).isEqualTo("Dinner");
        assertThat(captor.getValue().imageUrl()).isNull();
        assertThat(captor.getValue().participantUserIds()).containsExactly(1L, 2L, 3L);
    }

    /** An omitted splitType is the ordinary equal split (FR-18), not a rejection. */
    @Test
    void createExpenseDefaultsToAnEqualSplit() throws Exception {
        given(expenseService.createExpense(anyLong(), any(CreateExpenseRequest.class))).willReturn(RECORDED);
        ArgumentCaptor<CreateExpenseRequest> captor = ArgumentCaptor.forClass(CreateExpenseRequest.class);

        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"category":"FOOD","description":"Dinner",
                                 "totalAmount":100,"participantUserIds":[1,2,3]}
                                """))
                .andExpect(status().isCreated());

        verify(expenseService).createExpense(anyLong(), captor.capture());
        assertThat(captor.getValue().splitType()).isEqualTo(SplitType.EQUAL);
        assertThat(captor.getValue().customShares()).isEmpty();
    }

    /** BR-3, at the edge: an expense nobody shares never reaches the service. */
    @Test
    void createExpenseRejectsAnEmptyParticipantListWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"category":"FOOD","description":"Dinner",
                                 "totalAmount":100,"participantUserIds":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("participantUserIds: must contain at least one participant"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void createExpenseRejectsAMissingParticipantListWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"category":"FOOD","description":"Dinner","totalAmount":100}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(expenseService);
    }

    /** BR-2, at the edge: with no payer there is nothing to owe against. */
    @Test
    void createExpenseRejectsAMissingPayerWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"FOOD","description":"Dinner","totalAmount":100,
                                 "participantUserIds":[1,2]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("paidByUserId: must not be null"));

        verifyNoInteractions(expenseService);
    }

    /** A repeated participant would take two shares and be double counted by the balance engine. */
    @Test
    void createExpenseRejectsADuplicateParticipantWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"category":"FOOD","description":"Dinner",
                                 "totalAmount":100,"participantUserIds":[2,2]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "participantsDistinct: participantUserIds must not contain duplicate ids"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void createExpenseRejectsANonPositiveTotalWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"category":"FOOD","description":"Dinner",
                                 "totalAmount":0,"participantUserIds":[1,2]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("totalAmount: must be greater than zero"));

        verifyNoInteractions(expenseService);
    }

    /** The cap is what keeps the split arithmetic clear of overflow, so it has to hold here. */
    @Test
    void createExpenseRejectsAnAbsurdTotalWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"category":"FOOD","description":"Dinner",
                                 "totalAmount":9223372036854775807,"participantUserIds":[1,2]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("totalAmount: must be at most 100000000000 piastres"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void createExpenseRejectsABlankDescriptionWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"category":"FOOD","description":"   ",
                                 "totalAmount":100,"participantUserIds":[1,2]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("description: must not be blank"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void createExpenseRejectsAMissingCategoryWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"description":"Dinner","totalAmount":100,
                                 "participantUserIds":[1,2]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("category: must not be null"));

        verifyNoInteractions(expenseService);
    }

    /** A category outside FR-13 is not a category; the body cannot be bound at all. */
    @Test
    void createExpenseRejectsAnUnknownCategory() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"category":"BITCOIN","description":"Dinner",
                                 "totalAmount":100,"participantUserIds":[1,2]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));

        verifyNoInteractions(expenseService);
    }

    /** A CUSTOM split with nothing to divide by is incoherent, so it never reaches the service. */
    @Test
    void createExpenseRejectsACustomSplitWithoutShares() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"category":"FOOD","description":"Dinner",
                                 "totalAmount":100,"participantUserIds":[1,2],"splitType":"CUSTOM"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("customSharesPresentWhenRequired: "
                        + "customShares must be provided when splitType is CUSTOM"));

        verifyNoInteractions(expenseService);
    }

    /**
     * Shares sent with an EQUAL split are refused rather than ignored: the client believes those
     * numbers will be used, and computing different ones silently is how a bill gets split in a
     * way nobody agreed to.
     */
    @Test
    void createExpenseRejectsSharesSentWithAnEqualSplit() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"category":"FOOD","description":"Dinner",
                                 "totalAmount":100,"participantUserIds":[1,2],"splitType":"EQUAL",
                                 "customShares":{"1":60,"2":40}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("customSharesAbsentWhenUnused: "
                        + "customShares must not be provided when splitType is EQUAL"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void createExpenseRejectsANegativeShareWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"category":"FOOD","description":"Dinner",
                                 "totalAmount":100,"participantUserIds":[1,2],"splitType":"CUSTOM",
                                 "customShares":{"1":160,"2":-60}}
                                """))
                .andExpect(status().isBadRequest())
                // The violation names the offending key, so the client knows whose share is wrong.
                .andExpect(jsonPath("$.message")
                        .value("customShares[2]: must not contain a negative share"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void createExpenseMapsANonMemberTo400() throws Exception {
        given(expenseService.createExpense(anyLong(), any(CreateExpenseRequest.class)))
                .willThrow(new NonGroupMemberException(List.of(9L)));

        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":9,"category":"FOOD","description":"Dinner",
                                 "totalAmount":100,"participantUserIds":[1,2]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Users [9] are not members of this group"))
                .andExpect(jsonPath("$.path").value("/api/groups/10/expenses"));
    }

    @Test
    void createExpenseMapsASplitTotalMismatchTo400() throws Exception {
        given(expenseService.createExpense(anyLong(), any(CreateExpenseRequest.class)))
                .willThrow(new SplitTotalMismatchException(9_000L, 10_000L));

        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"category":"FOOD","description":"Dinner",
                                 "totalAmount":10000,"participantUserIds":[1,2],"splitType":"CUSTOM",
                                 "customShares":{"1":5000,"2":4000}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Shares total 9000 piastres but the expense is 10000 piastres; "
                                + "they must add up exactly"));
    }

    @Test
    void createExpenseMapsASplitParticipantMismatchTo400() throws Exception {
        given(expenseService.createExpense(anyLong(), any(CreateExpenseRequest.class)))
                .willThrow(new SplitParticipantMismatchException(List.of(2L), List.of()));

        mockMvc.perform(post("/api/groups/{groupId}/expenses", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"category":"FOOD","description":"Dinner",
                                 "totalAmount":100,"participantUserIds":[1,2],"splitType":"CUSTOM",
                                 "customShares":{"1":100}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A custom split must give exactly one share "
                        + "per participant: no share was given for participants [2]"));
    }

    @Test
    void createExpenseMapsAnUnknownGroupTo404() throws Exception {
        given(expenseService.createExpense(anyLong(), any(CreateExpenseRequest.class)))
                .willThrow(new ResourceNotFoundException("Group", 99L));

        mockMvc.perform(post("/api/groups/{groupId}/expenses", 99)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"category":"FOOD","description":"Dinner",
                                 "totalAmount":100,"participantUserIds":[1,2]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Group 99 was not found"));
    }

    @Test
    void createExpenseRejectsANonPositiveGroupId() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", 0)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidByUserId":1,"category":"FOOD","description":"Dinner",
                                 "totalAmount":100,"participantUserIds":[1,2]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(expenseService);
    }
}
