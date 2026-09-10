package com.oae.fakka.service;

import com.oae.fakka.config.OcrProperties;
import com.oae.fakka.dto.OcrReceiptItem;
import com.oae.fakka.dto.ParsedReceiptResponse;
import com.oae.fakka.exception.AiResponseNotUsableException;
import com.oae.fakka.exception.OcrUnavailableException;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link ReceiptOcrService}.
 * <p>
 * The HTTP call to OCR.space is not made in these tests. Instead, tests that exercise the line
 * parser inject a subclass of {@link ReceiptOcrService} that overrides the private OCR call, or
 * they rely on the {@link TestableReceiptOcrService} helper below which exposes
 * {@code extractItemsPublic} for direct testing of the parsing logic.
 * <p>
 * The same three failure shapes as Phase 8.1 are verified here:
 * <ul>
 *   <li>404 for an unknown group — before the OCR service is asked anything</li>
 *   <li>503 for an unconfigured or unreachable OCR service</li>
 *   <li>422 for a service that answered but found no readable text</li>
 *   <li>503 for any other unexpected failure (the BR-7 backstop)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReceiptOcrServiceTest {

    private static final long GROUP_ID = 10L;

    @Mock
    private GroupRepository groupRepository;

    /** A subclass that exposes the item-extraction logic directly, bypassing the HTTP call. */
    private TestableReceiptOcrService service;

    @BeforeEach
    void setUp() {
        OcrProperties props = new OcrProperties(true, "test-key", "https://api.ocr.space",
                Duration.ofSeconds(5), 5 * 1024 * 1024L);
        service = new TestableReceiptOcrService(props, groupRepository);
    }

    // -------------------------------------------------------------------------
    // Pre-call guards
    // -------------------------------------------------------------------------

    @Test
    void reports404ForAnUnknownGroupBeforeCallingOcr() {
        given(groupRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> service.parse(99L, mockImage("image/jpeg")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Group 99 was not found");
    }

    @Test
    void reports503WhenOcrIsNotConfigured() {
        OcrProperties unconfigured = new OcrProperties(
                true, "", "https://api.ocr.space", Duration.ofSeconds(5), 0L);
        TestableReceiptOcrService unconfiguredService =
                new TestableReceiptOcrService(unconfigured, groupRepository);
        given(groupRepository.existsById(GROUP_ID)).willReturn(true);

        assertThatThrownBy(() -> unconfiguredService.parse(GROUP_ID, mockImage("image/jpeg")))
                .isInstanceOf(OcrUnavailableException.class)
                .hasMessageContaining("not configured")
                .hasMessageContaining("enter items manually instead");
    }

    @Test
    void reports503WhenOcrIsDisabled() {
        OcrProperties disabled = new OcrProperties(
                false, "a-key", "https://api.ocr.space", Duration.ofSeconds(5), 0L);
        TestableReceiptOcrService disabledService =
                new TestableReceiptOcrService(disabled, groupRepository);
        given(groupRepository.existsById(GROUP_ID)).willReturn(true);

        assertThatThrownBy(() -> disabledService.parse(GROUP_ID, mockImage("image/jpeg")))
                .isInstanceOf(OcrUnavailableException.class)
                .hasMessageContaining("not configured");
    }

    // -------------------------------------------------------------------------
    // Item extraction — happy paths
    // -------------------------------------------------------------------------

    @Test
    void extractsSimpleItemLines() {
        givenGroupExists();
        service.setFakeOcrText("""
                Pizza                   35.00
                Burger                  20.50
                Total                   55.50
                """);

        ParsedReceiptResponse response = service.parse(GROUP_ID, mockImage("image/jpeg"));

        assertThat(response.items()).hasSize(2);

        OcrReceiptItem pizza = response.items().get(0);
        assertThat(pizza.name()).isEqualTo("Pizza");
        assertThat(pizza.quantity()).isEqualTo(1);
        assertThat(pizza.unitPricePiastres()).isEqualTo(3500L);

        OcrReceiptItem burger = response.items().get(1);
        assertThat(burger.name()).isEqualTo("Burger");
        assertThat(burger.unitPricePiastres()).isEqualTo(2050L);
    }

    @Test
    void extractsTheReceiptTotalSeparatelyFromItems() {
        givenGroupExists();
        service.setFakeOcrText("""
                Pizza    35.00
                Burger   20.00
                Total    55.00
                """);

        ParsedReceiptResponse response = service.parse(GROUP_ID, mockImage("image/jpeg"));

        assertThat(response.suggestedTotalPiastres()).isEqualTo(5500L);
    }

    @Test
    void convertsEgpAmountsWithDecimalToPiastresExactly() {
        givenGroupExists();
        service.setFakeOcrText("French Fries  12.50\n");

        ParsedReceiptResponse response = service.parse(GROUP_ID, mockImage("image/jpeg"));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).unitPricePiastres()).isEqualTo(1250L);
    }

    @Test
    void parsesQuantityPrefixAndDividesLineTotalIntoUnitPrice() {
        givenGroupExists();
        service.setFakeOcrText("Coke x2  20.00\n");

        ParsedReceiptResponse response = service.parse(GROUP_ID, mockImage("image/jpeg"));

        assertThat(response.items()).hasSize(1);
        OcrReceiptItem item = response.items().get(0);
        assertThat(item.quantity()).isEqualTo(2);
        // 2000 piastres line total / 2 = 1000 per unit
        assertThat(item.unitPricePiastres()).isEqualTo(1000L);
    }

    @Test
    void parsesMultiWordItemNames() {
        givenGroupExists();
        service.setFakeOcrText("Chicken Tawook Sandwich  45.00\n");

        ParsedReceiptResponse response = service.parse(GROUP_ID, mockImage("image/jpeg"));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).name()).isEqualTo("Chicken Tawook Sandwich");
    }

    @Test
    void skipsBoilerplateLinesLikeSubtotalAndTax() {
        givenGroupExists();
        service.setFakeOcrText("""
                Pizza           35.00
                Subtotal        35.00
                Tax             3.50
                Service Charge  2.50
                Total           41.00
                """);

        ParsedReceiptResponse response = service.parse(GROUP_ID, mockImage("image/jpeg"));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).name()).isEqualTo("Pizza");
    }

    @Test
    void handlesEmptyItemsListWhenNoLineMatchesPattern() {
        givenGroupExists();
        service.setFakeOcrText("""
                Thank you for your visit!
                Order #12345
                """);

        ParsedReceiptResponse response = service.parse(GROUP_ID, mockImage("image/jpeg"));

        assertThat(response.items()).isEmpty();
        assertThat(response.suggestedTotalPiastres()).isEqualTo(0L);
    }

    /** A zero suggestedTotal when no total line is printed is a valid, usable response. */
    @Test
    void suggestedTotalIsZeroWhenNoTotalLineExists() {
        givenGroupExists();
        service.setFakeOcrText("Pizza  35.00\n");

        ParsedReceiptResponse response = service.parse(GROUP_ID, mockImage("image/jpeg"));

        assertThat(response.suggestedTotalPiastres()).isEqualTo(0L);
    }

    // -------------------------------------------------------------------------
    // Failure shapes — all must be deliberate, none a 500
    // -------------------------------------------------------------------------

    @Test
    void reports422WhenOcrFindsNoText() {
        givenGroupExists();
        service.setFakeOcrText(null); // triggers 422 path

        assertThatThrownBy(() -> service.parse(GROUP_ID, mockImage("image/jpeg")))
                .isInstanceOf(AiResponseNotUsableException.class)
                .hasMessageContaining("found no text")
                .hasMessageContaining("enter the expense manually");
    }

    @Test
    void reports503WhenCallOcrThrows() {
        givenGroupExists();
        service.setShouldThrowUnavailable(true);

        assertThatThrownBy(() -> service.parse(GROUP_ID, mockImage("image/jpeg")))
                .isInstanceOf(OcrUnavailableException.class)
                .hasMessageContaining("enter items manually instead");
    }

    /**
     * The BR-7 backstop: any unanticipated exception becomes a 503, never an unhandled 500.
     */
    @Test
    void wrapsAnUnanticipatedExceptionAs503() {
        givenGroupExists();
        service.setShouldThrowUnexpected(true);

        assertThatThrownBy(() -> service.parse(GROUP_ID, mockImage("image/jpeg")))
                .isInstanceOf(OcrUnavailableException.class)
                .hasMessageContaining("failed unexpectedly")
                .hasMessageContaining("enter items manually instead");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void givenGroupExists() {
        given(groupRepository.existsById(GROUP_ID)).willReturn(true);
    }

    private static MultipartFile mockImage(String contentType) {
        return mock(MultipartFile.class);
    }

    /**
     * Test-only subclass that bypasses the actual HTTP call to OCR.space.
     * <ul>
     *   <li>When {@code fakeOcrText} is set, {@code callOcrSpace} returns it.</li>
     *   <li>When {@code fakeOcrText} is null, {@code callOcrSpace} throws 422.</li>
     *   <li>{@code shouldThrowUnavailable} → 503 from {@code callOcrSpace}.</li>
     *   <li>{@code shouldThrowUnexpected} → unchecked exception (exercises the backstop).</li>
     * </ul>
     */
    static class TestableReceiptOcrService extends ReceiptOcrService {

        private String fakeOcrText = "";
        private boolean shouldThrowUnavailable = false;
        private boolean shouldThrowUnexpected = false;

        TestableReceiptOcrService(OcrProperties props, GroupRepository groupRepository) {
            super(props, groupRepository);
        }

        void setFakeOcrText(String text) {
            this.fakeOcrText = text;
        }

        void setShouldThrowUnavailable(boolean b) {
            this.shouldThrowUnavailable = b;
        }

        void setShouldThrowUnexpected(boolean b) {
            this.shouldThrowUnexpected = b;
        }

        @Override
        protected String callOcrSpaceForTest(MultipartFile image) {
            if (shouldThrowUnexpected) {
                throw new IllegalStateException("unexpected internal error");
            }
            if (shouldThrowUnavailable) {
                throw new OcrUnavailableException("The receipt scanner could not be reached");
            }
            if (fakeOcrText == null) {
                throw new AiResponseNotUsableException(
                        "The receipt scanner found no text in the image");
            }
            return fakeOcrText;
        }
    }
}
