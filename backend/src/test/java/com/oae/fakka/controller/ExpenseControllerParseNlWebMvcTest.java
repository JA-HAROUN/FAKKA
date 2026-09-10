package com.oae.fakka.controller;

import com.oae.fakka.dto.ParseExpenseTextRequest;
import com.oae.fakka.dto.ParsedExpenseDraftResponse;
import com.oae.fakka.dto.SplitType;
import com.oae.fakka.dto.UserSummaryResponse;
import com.oae.fakka.exception.AiResponseNotUsableException;
import com.oae.fakka.exception.AiUnavailableException;
import com.oae.fakka.exception.ResourceNotFoundException;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@code POST /api/groups/{groupId}/expenses/parse-nl} with
 * {@link NaturalLanguageExpenseService} mocked.
 * <p>
 * The extraction logic itself is covered exhaustively in {@code NaturalLanguageExpenseServiceTest}
 * and {@code MemberNameMatcherTest}. What belongs at this layer is BR-7 as an HTTP contract: each
 * of the service's three failure shapes must arrive at the right status with a message that says
 * to fall back to manual entry, none of them may reach the client as a 500, and a malformed
 * request must be refused before the service is asked anything at all.
 */
@WebMvcTest(ExpenseController.class)
@ActiveProfiles("dev")
class ExpenseControllerParseNlWebMvcTest {

    private static final ParsedExpenseDraftResponse DRAFT = new ParsedExpenseDraftResponse(
            "Dinner", 90_000L, null,
            new UserSummaryResponse(1L, "Ahmed Ragy", null),
            List.of(new UserSummaryResponse(1L, "Ahmed Ragy", null),
                    new UserSummaryResponse(2L, "Mohamed Salah", "https://img.example.com/m.jpg")),
            SplitType.EQUAL,
            List.of());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService expenseService;

    @MockitoBean
    private NaturalLanguageExpenseService naturalLanguageExpenseService;

    @Test
    void parsesTheTextAndReturnsTheDraftAs200() throws Exception {
        given(naturalLanguageExpenseService.parse(anyLong(), any(ParseExpenseTextRequest.class)))
                .willReturn(DRAFT);

        mockMvc.perform(post("/api/groups/{groupId}/expenses/parse-nl", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Ahmed paid 900 EGP for dinner, split with Mohamed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Dinner"))
                .andExpect(jsonPath("$.totalAmount").value(90000))
                .andExpect(jsonPath("$.suggestedCategory").doesNotExist())
                .andExpect(jsonPath("$.paidBy.userId").value(1))
                .andExpect(jsonPath("$.paidBy.name").value("Ahmed Ragy"))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.participants[1].profileImageUrl")
                        .value("https://img.example.com/m.jpg"))
                .andExpect(jsonPath("$.splitType").value("EQUAL"))
                .andExpect(jsonPath("$.unresolvedNames.length()").value(0));
    }

    /** Nothing is written by this endpoint: the response is the whole point, never a location header. */
    @Test
    void nothingIsWrittenOnlyTheDraftIsReturned() throws Exception {
        given(naturalLanguageExpenseService.parse(anyLong(), any(ParseExpenseTextRequest.class)))
                .willReturn(DRAFT);

        mockMvc.perform(post("/api/groups/{groupId}/expenses/parse-nl", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Ahmed paid 900 EGP for dinner"}
                                """))
                .andExpect(status().isOk());

        verifyNoInteractions(expenseService);
    }

    @Test
    void passesTheGroupIdAndTheStrippedTextToTheService() throws Exception {
        given(naturalLanguageExpenseService.parse(anyLong(), any(ParseExpenseTextRequest.class)))
                .willReturn(DRAFT);
        ArgumentCaptor<ParseExpenseTextRequest> captor =
                ArgumentCaptor.forClass(ParseExpenseTextRequest.class);

        mockMvc.perform(post("/api/groups/{groupId}/expenses/parse-nl", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"  Ahmed paid 900 EGP for dinner  "}
                                """))
                .andExpect(status().isOk());

        verify(naturalLanguageExpenseService).parse(eq(10L), captor.capture());
        assertThat(captor.getValue().text()).isEqualTo("Ahmed paid 900 EGP for dinner");
    }

    /** A draft where a name went unresolved is still a 200: the client asks the reviewer, not us. */
    @Test
    void anUnresolvedNameStillReturns200WithItListed() throws Exception {
        ParsedExpenseDraftResponse draftWithUnresolved = new ParsedExpenseDraftResponse(
                "Dinner", 90_000L, null,
                new UserSummaryResponse(1L, "Ahmed Ragy", null),
                List.of(new UserSummaryResponse(1L, "Ahmed Ragy", null)),
                SplitType.EQUAL,
                List.of("Youssef"));
        given(naturalLanguageExpenseService.parse(anyLong(), any(ParseExpenseTextRequest.class)))
                .willReturn(draftWithUnresolved);

        mockMvc.perform(post("/api/groups/{groupId}/expenses/parse-nl", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Ahmed and Youssef split 900 EGP for dinner"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unresolvedNames[0]").value("Youssef"));
    }

    @Test
    void rejectsBlankTextWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses/parse-nl", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("text: must not be blank"));

        verifyNoInteractions(naturalLanguageExpenseService);
    }

    @Test
    void rejectsMissingTextWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses/parse-nl", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(naturalLanguageExpenseService);
    }

    @Test
    void rejectsTextOverTheLengthCapWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses/parse-nl", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"%s"}
                                """.formatted("x".repeat(1001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("text: must be at most 1000 characters"));

        verifyNoInteractions(naturalLanguageExpenseService);
    }

    @Test
    void rejectsANonPositiveGroupIdWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses/parse-nl", 0)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Ahmed paid 900 EGP for dinner"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(naturalLanguageExpenseService);
    }

    /*
     * BR-7: every one of the service's failure shapes must arrive as the status it names, never
     * a 500, and with a message pointing at manual entry.
     */

    @Test
    void mapsAnUnavailableParserTo503() throws Exception {
        given(naturalLanguageExpenseService.parse(anyLong(), any(ParseExpenseTextRequest.class)))
                .willThrow(new AiUnavailableException("The expense parser is not configured"));

        mockMvc.perform(post("/api/groups/{groupId}/expenses/parse-nl", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Ahmed paid 900 EGP for dinner"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(
                        "The expense parser is not configured; enter the expense manually instead"))
                .andExpect(jsonPath("$.path").value("/api/groups/10/expenses/parse-nl"));
    }

    @Test
    void mapsAnUnusableReplyTo422() throws Exception {
        given(naturalLanguageExpenseService.parse(anyLong(), any(ParseExpenseTextRequest.class)))
                .willThrow(new AiResponseNotUsableException("Could not tell which group member paid"));

        mockMvc.perform(post("/api/groups/{groupId}/expenses/parse-nl", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"somebody paid for something"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("Could not tell which group member paid; "
                        + "review the text or enter the expense manually"));
    }

    @Test
    void mapsAnUnknownGroupTo404() throws Exception {
        given(naturalLanguageExpenseService.parse(anyLong(), any(ParseExpenseTextRequest.class)))
                .willThrow(new ResourceNotFoundException("Group", 99L));

        mockMvc.perform(post("/api/groups/{groupId}/expenses/parse-nl", 99)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Ahmed paid 900 EGP for dinner"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Group 99 was not found"));
    }

    /**
     * The BR-7 backstop, at the HTTP boundary: even a bug nobody anticipated in the parser must
     * not surface as a raw 500 with a stack trace visible to the client.
     */
    @Test
    void anUnanticipatedFailureIsStillNeverARaw500WithInternalDetail() throws Exception {
        given(naturalLanguageExpenseService.parse(anyLong(), any(ParseExpenseTextRequest.class)))
                .willThrow(new IllegalStateException("connection pool exhausted at jdbc:mysql://prod"));

        mockMvc.perform(post("/api/groups/{groupId}/expenses/parse-nl", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Ahmed paid 900 EGP for dinner"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                        .value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.message").value(not(containsString("jdbc"))));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses/parse-nl", 10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));

        verifyNoInteractions(naturalLanguageExpenseService);
    }

    @Test
    void wrongHttpMethodReturns405() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/expenses/parse-nl", 10))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message")
                        .value("Method GET is not supported for this endpoint"));

        verifyNoInteractions(naturalLanguageExpenseService);
    }
}
