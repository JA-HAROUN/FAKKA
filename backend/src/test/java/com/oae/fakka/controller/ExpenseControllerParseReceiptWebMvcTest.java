package com.oae.fakka.controller;

import com.oae.fakka.dto.OcrReceiptItem;
import com.oae.fakka.dto.ParsedReceiptResponse;
import com.oae.fakka.exception.AiResponseNotUsableException;
import com.oae.fakka.exception.AiUnavailableException;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.service.ExpenseService;
import com.oae.fakka.service.NaturalLanguageExpenseService;
import com.oae.fakka.service.ReceiptOcrService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@code POST /api/groups/{groupId}/expenses/parse-receipt} with
 * {@link ReceiptOcrService} mocked.
 * <p>
 * The parsing logic itself is covered in {@code ReceiptOcrServiceTest}. What belongs at this
 * layer is the HTTP contract:
 * <ul>
 *   <li>A valid image upload produces a 200 with the correct response shape.</li>
 *   <li>A missing or empty part, or a non-image content type, is rejected with 400 before the
 *       service is called.</li>
 *   <li>Each of the service's three failure shapes (503, 422, 404) arrives at exactly that
 *       status and none of them as a 500.</li>
 *   <li>Nothing is written: {@link ExpenseService} is never touched.</li>
 * </ul>
 */
@WebMvcTest(ExpenseController.class)
@ActiveProfiles("dev")
class ExpenseControllerParseReceiptWebMvcTest {

    private static final ParsedReceiptResponse SAMPLE_RESPONSE = new ParsedReceiptResponse(
            List.of(
                    new OcrReceiptItem("Pizza", 1, 3500L),
                    new OcrReceiptItem("Burger", 2, 2000L)),
            8500L);

    private static final MockMultipartFile JPEG_IMAGE = new MockMultipartFile(
            "image", "receipt.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3});

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService expenseService;

    @MockitoBean
    private NaturalLanguageExpenseService naturalLanguageExpenseService;

    @MockitoBean
    private ReceiptOcrService receiptOcrService;

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void parsesReceiptAndReturnsItemsAs200() throws Exception {
        given(receiptOcrService.parse(anyLong(), any())).willReturn(SAMPLE_RESPONSE);

        mockMvc.perform(multipart("/api/groups/{groupId}/expenses/parse-receipt", 10)
                        .file(JPEG_IMAGE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Pizza"))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items[0].unitPricePiastres").value(3500))
                .andExpect(jsonPath("$.items[1].name").value("Burger"))
                .andExpect(jsonPath("$.items[1].quantity").value(2))
                .andExpect(jsonPath("$.suggestedTotalPiastres").value(8500));
    }

    /** An empty items list is a valid 200 — the client shows the manual entry form. */
    @Test
    void emptyItemsListIsStillA200() throws Exception {
        given(receiptOcrService.parse(anyLong(), any()))
                .willReturn(new ParsedReceiptResponse(List.of(), 0L));

        mockMvc.perform(multipart("/api/groups/{groupId}/expenses/parse-receipt", 10)
                        .file(JPEG_IMAGE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.suggestedTotalPiastres").value(0));
    }

    /** Nothing is written by this endpoint: the response is the whole point. */
    @Test
    void nothingIsWrittenOnlyTheItemsAreReturned() throws Exception {
        given(receiptOcrService.parse(anyLong(), any())).willReturn(SAMPLE_RESPONSE);

        mockMvc.perform(multipart("/api/groups/{groupId}/expenses/parse-receipt", 10)
                        .file(JPEG_IMAGE))
                .andExpect(status().isOk());

        verifyNoInteractions(expenseService);
    }

    // -------------------------------------------------------------------------
    // Input validation — checked before the service is invoked
    // -------------------------------------------------------------------------

    @Test
    void rejectsNonImageContentTypeWith400() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "image", "doc.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/groups/{groupId}/expenses/parse-receipt", 10)
                        .file(pdfFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Only image files")));

        verifyNoInteractions(receiptOcrService);
    }

    @Test
    void rejectsNonPositiveGroupIdWith400() throws Exception {
        mockMvc.perform(multipart("/api/groups/{groupId}/expenses/parse-receipt", 0)
                        .file(JPEG_IMAGE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(receiptOcrService);
    }

    @Test
    void wrongHttpMethodReturns405() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/expenses/parse-receipt", 10))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message")
                        .value("Method GET is not supported for this endpoint"));

        verifyNoInteractions(receiptOcrService);
    }

    // -------------------------------------------------------------------------
    // Service failure shapes — BR-7 / FR-28: never a 500, always signal "enter manually"
    // -------------------------------------------------------------------------

    @Test
    void mapsAnUnavailableOcrServiceTo503() throws Exception {
        given(receiptOcrService.parse(anyLong(), any()))
                .willThrow(new AiUnavailableException("The receipt scanner is not configured"));

        mockMvc.perform(multipart("/api/groups/{groupId}/expenses/parse-receipt", 10)
                        .file(JPEG_IMAGE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(
                        "The receipt scanner is not configured; enter items manually instead"))
                .andExpect(jsonPath("$.path")
                        .value("/api/groups/10/expenses/parse-receipt"));
    }

    @Test
    void mapsAnUnreadableReceiptTo422() throws Exception {
        given(receiptOcrService.parse(anyLong(), any()))
                .willThrow(new AiResponseNotUsableException(
                        "The receipt scanner found no text in the image"));

        mockMvc.perform(multipart("/api/groups/{groupId}/expenses/parse-receipt", 10)
                        .file(JPEG_IMAGE))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(
                        "The receipt scanner found no text in the image; "
                                + "review the text or enter the expense manually"));
    }

    @Test
    void mapsAnUnknownGroupTo404() throws Exception {
        given(receiptOcrService.parse(anyLong(), any()))
                .willThrow(new ResourceNotFoundException("Group", 99L));

        mockMvc.perform(multipart("/api/groups/{groupId}/expenses/parse-receipt", 99)
                        .file(JPEG_IMAGE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Group 99 was not found"));
    }

    /**
     * The BR-7 backstop at the HTTP boundary: an unanticipated bug in the parser must not
     * surface as a raw 500 with internal detail visible to the client.
     */
    @Test
    void anUnanticipatedFailureIsNeverARaw500WithInternalDetail() throws Exception {
        given(receiptOcrService.parse(anyLong(), any()))
                .willThrow(new IllegalStateException("connection pool exhausted at jdbc:mysql://prod"));

        mockMvc.perform(multipart("/api/groups/{groupId}/expenses/parse-receipt", 10)
                        .file(JPEG_IMAGE))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                        .value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.message").value(not(containsString("jdbc"))));
    }
}
