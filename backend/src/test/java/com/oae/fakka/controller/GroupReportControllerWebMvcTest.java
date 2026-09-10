package com.oae.fakka.controller;

import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.service.ExpenseReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link GroupReportController} with {@link ExpenseReportService} mocked.
 * <p>
 * The file format is covered in {@code ExpenseReportServiceTest}. What matters here is
 * everything around the bytes: the content type and filename a browser needs to save it, the
 * refusal of a format that does not exist, and above all that an unknown group is a 404 rather
 * than a 200 that turns into an error halfway through a download.
 */
@WebMvcTest(GroupReportController.class)
@ActiveProfiles("dev")
class GroupReportControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseReportService expenseReportService;

    @Test
    void theReportIsReturnedAsAttachedCsv() throws Exception {
        givenTheServiceWrites("date,category\r\n");

        MvcResult started = mockMvc.perform(get("/api/groups/{groupId}/export", 10))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"group-10-expenses.csv\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string("date,category\r\n"));
    }

    /**
     * Omitting the format is the same as asking for csv, which is the only one there is.
     * <p>
     * The dispatch is what runs the streaming body, so the verification has to come after it:
     * asyncStarted only means the controller returned, and the writing happens on another thread.
     */
    @Test
    void theFormatDefaultsToCsv() throws Exception {
        givenTheServiceWrites("ok");

        MvcResult started = mockMvc.perform(get("/api/groups/{groupId}/export", 10))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk());

        verify(expenseReportService).writeCsv(eq(10L), any(OutputStream.class));
    }

    /** Nobody typing a URL expects case to matter. */
    @ParameterizedTest
    @ValueSource(strings = {"csv", "CSV", "Csv", " csv "})
    void theFormatIsAcceptedWhateverItsCase(String format) throws Exception {
        givenTheServiceWrites("ok");

        MvcResult started = mockMvc.perform(get("/api/groups/{groupId}/export", 10)
                        .param("format", format))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "xlsx", "json", "html"})
    void anyOtherFormatIsRejectedBeforeAnythingIsWritten(String format) throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/export", 10).param("format", format))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Export format %s is not supported; the only supported format is csv"
                                .formatted(format)));

        verifyNoInteractions(expenseReportService);
    }

    /**
     * The status is committed before the body streams, so the group has to be checked while a
     * 404 is still possible. If this ever regressed, a bad id would download a broken file.
     */
    @Test
    void anUnknownGroupIsA404RatherThanATruncatedDownload() throws Exception {
        willThrow(new ResourceNotFoundException("Group", 99L))
                .given(expenseReportService).requireReportableGroup(99L);

        mockMvc.perform(get("/api/groups/{groupId}/export", 99))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Group 99 was not found"))
                .andExpect(jsonPath("$.path").value("/api/groups/99/export"));

        verify(expenseReportService, never()).writeCsv(anyLong(), any(OutputStream.class));
    }

    @Test
    void aNonPositiveGroupIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/export", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(expenseReportService);
    }

    @Test
    void aNonNumericGroupIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/export", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'groupId' has an invalid value"));

        verifyNoInteractions(expenseReportService);
    }

    /** Whatever the service writes is what the client gets, byte for byte. */
    @Test
    void theBodyIsExactlyWhatTheServiceWrote() throws Exception {
        String expected = "date,category,description\r\n2026-09-10,FOOD,\"Pizza, salad\"\r\n";
        givenTheServiceWrites(expected);

        MvcResult started = mockMvc.perform(get("/api/groups/{groupId}/export", 10))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isEqualTo(expected);
    }

    private void givenTheServiceWrites(String csv) throws IOException {
        willAnswer(invocation -> {
            OutputStream outputStream = invocation.getArgument(1);
            outputStream.write(csv.getBytes(StandardCharsets.UTF_8));
            return null;
        }).given(expenseReportService).writeCsv(anyLong(), any(OutputStream.class));
    }
}
