package com.oae.fakka.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oae.fakka.config.OcrProperties;
import com.oae.fakka.dto.OcrReceiptItem;
import com.oae.fakka.dto.ParsedReceiptResponse;
import com.oae.fakka.exception.AiResponseNotUsableException;
import com.oae.fakka.exception.AiUnavailableException;
import com.oae.fakka.exception.ApiException;
import com.oae.fakka.exception.OcrUnavailableException;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.repository.GroupRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends a receipt image to OCR.space and extracts line items for the
 * purchased-items UI
 * (FR-24 to FR-28).
 *
 * <h2>It cannot write, by construction</h2>
 * The only repository injected is the one used to confirm the group exists.
 * There is no expense
 * repository here, so this class could not persist an expense if somebody asked
 * it to -- which is
 * what BR-6 needs: an OCR result pre-fills the form, and an expense exists only
 * when the user
 * confirms it through {@code POST /api/groups/{groupId}/expenses}. The
 * transaction is
 * {@code readOnly} as a second statement of the same thing.
 *
 * <h2>The OCR service extracts text; this class decides</h2>
 * A clean division, and it is deliberate. OCR.space turns pixels into
 * characters, which is what
 * it is good at. Everything with a right answer stays here: detecting line
 * items in the raw text,
 * converting EGP to piastres, and deciding whether what came back is usable at
 * all.
 *
 * <h2>Failure is a first-class outcome (BR-7, FR-28)</h2>
 * Three shapes of failure, three answers, none of them a 500:
 * <ul>
 * <li>Not configured, unreachable, timed out, or errored:
 * {@link AiUnavailableException},
 * a 503 with "enter items manually instead".</li>
 * <li>Reached but returned nothing parseable:
 * {@link AiResponseNotUsableException}, a 422.</li>
 * <li>Anything unforeseen: caught at the end of {@link #parse} and reported as
 * the 503,
 * because an unhandled 500 would tell the client nothing about what to do and
 * the answer
 * is always the same -- offer the manual item-entry form.</li>
 * </ul>
 */
@Slf4j
@Service
public class ReceiptOcrService {

    /** OCR.space endpoint path for image submission. */
    private static final String PARSE_IMAGE_PATH = "/parse/image";

    /**
     * Matches a receipt line that looks like a purchasable item.
     * <p>
     * Groups (all optional except the name):
     * <ol>
     * <li>Item name — one or more word characters and spaces (required)</li>
     * <li>Quantity prefix — a number followed by {@code x} or {@code ×}
     * (optional)</li>
     * <li>Price — a decimal number, optionally prefixed by {@code EGP} / {@code LE}
     * /
     * currency symbols (required to treat the line as an item)</li>
     * </ol>
     * Examples matched:
     * 
     * <pre>
     *   Pizza                   35.00
     *   Burger x1               20.50
     *   Coke ×2                 10.00
     *   French Fries  EGP 15
     * </pre>
     */
    private static final Pattern ITEM_LINE = Pattern.compile(
            "^(?<name>[\\w][\\w .,'&-]{0,59}?)\\s+"
                    + "(?:(?:(?<qty1>\\d+)\\s*[x×])|(?:[x×]\\s*(?<qty2>\\d+))\\s*)?"
                    + "(?:EGP|LE|L\\.E\\.|£|\\$)?\\s*"
                    + "(?<price>\\d{1,7}(?:[.,]\\d{1,2})?)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * Matches a total line. Used to read {@code suggestedTotalPiastres}
     * independently of items.
     * <p>
     * Examples matched: {@code Total 85.00}, {@code TOTAL EGP 850},
     * {@code Grand Total: 1200}.
     */
    private static final Pattern TOTAL_LINE = Pattern.compile(
            "(?i)\\b(?:grand\\s+)?total[:\\s]+(?:EGP|LE|L\\.E\\.|£|\\$)?\\s*"
                    + "(?<price>\\d{1,7}(?:[.,]\\d{1,2})?)");

    /**
     * Words that look like item names but are receipt boilerplate. Lines whose
     * trimmed text
     * matches any of these are skipped before the item regex is applied.
     */
    private static final List<String> BOILERPLATE_PATTERNS = List.of(
            "(?i)^(sub)?total[:\\s].*",
            "(?i)^tax[:\\s].*",
            "(?i)^vat[:\\s].*",
            "(?i)^service\\s+charge[:\\s].*",
            "(?i)^discount[:\\s].*",
            "(?i)^change[:\\s].*",
            "(?i)^cash[:\\s].*",
            "(?i)^card[:\\s].*",
            "(?i)^tip[:\\s].*",
            "(?i)^thank\\b.*",
            "(?i)^receipt\\b.*",
            "(?i)^order\\s*#?\\s*\\d+.*",
            "(?i)^table\\s*#?\\s*\\d+.*",
            "(?i)^\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}.*" // dates
    );

    private static final int MAX_ITEMS = 50;

    private final OcrProperties ocrProperties;
    private final GroupRepository groupRepository;
    private final RestClient restClient;

    /*
     * Same reasoning as NaturalLanguageExpenseService: a plain ObjectMapper is
     * cheap, stateless,
     * and needs no shared configuration with the HTTP message converters.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReceiptOcrService(OcrProperties ocrProperties, GroupRepository groupRepository) {
        this.ocrProperties = ocrProperties;
        this.groupRepository = groupRepository;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(ocrProperties.timeout());
        factory.setReadTimeout(ocrProperties.timeout());

        this.restClient = RestClient.builder()
                .baseUrl(ocrProperties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * Parses the image into a receipt item list. Writes nothing, ever.
     * <p>
     * The unknown-group 404 and the unavailable-service 503 both happen before the
     * image is sent,
     * so a misconfigured deployment or a bad id costs nothing.
     *
     * @param groupId the group this receipt would belong to (existence check only)
     * @param image   the uploaded image file
     * @return a response with the extracted items and suggested total, ready for
     *         the UI to pre-fill
     */
    @Transactional(readOnly = true)
    public ParsedReceiptResponse parse(Long groupId, MultipartFile image) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group", groupId);
        }
        if (!ocrProperties.isUsable()) {
            throw new OcrUnavailableException("The receipt scanner is not configured");
        }

        try {
            String rawText = callOcrSpaceForTest(image);
            return extractItems(rawText);
        } catch (ApiException deliberate) {
            // Already the right status with the right message; passing it through is the
            // point.
            throw deliberate;
        } catch (Exception unexpected) {
            /*
             * The BR-7 / FR-28 backstop. Whatever went wrong -- a bug here, something a
             * dependency threw that was not anticipated -- the caller gets the one answer
             * that
             * helps, and the detail goes to the log where it can be fixed.
             */
            log.error("Unexpected failure parsing receipt for group id={}", groupId, unexpected);
            throw new OcrUnavailableException("The receipt scanner failed unexpectedly", unexpected);
        }
    }

    /**
     * Protected hook so tests can override the HTTP call without a network.
     * Production code reaches here and delegates to the private
     * {@link #callOcrSpace}.
     */
    protected String callOcrSpaceForTest(MultipartFile image) {
        return callOcrSpace(image);
    }

    // -------------------------------------------------------------------------
    // OCR.space call
    // -------------------------------------------------------------------------

    /**
     * Encodes the image as base64 and submits it to OCR.space.
     *
     * @return the raw text OCR.space extracted from the image
     * @throws AiUnavailableException       if the service cannot be reached or
     *                                      returns an error
     * @throws AiResponseNotUsableException if the service replied but extracted no
     *                                      text
     */
    private String callOcrSpace(MultipartFile image) {
        byte[] imageBytes;
        try {
            imageBytes = image.getBytes();
        } catch (Exception e) {
            log.warn("Could not read uploaded image bytes", e);
            throw new OcrUnavailableException("Could not read the uploaded image");
        }

        String base64Image = "data:"
                + sanitizeContentType(image.getContentType())
                + ";base64,"
                + Base64.getEncoder().encodeToString(imageBytes);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("base64Image", base64Image);
        formData.add("language", "eng");
        formData.add("isOverlayRequired", "false");
        formData.add("detectOrientation", "true");
        formData.add("scale", "true");
        formData.add("OCREngine", "2"); // engine 2 handles printed receipts better

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(PARSE_IMAGE_PATH)
                    .header("apikey", ocrProperties.apiKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.warn("OCR.space call failed", e);
            throw new OcrUnavailableException("The receipt scanner could not be reached", e);
        }

        return extractTextFromOcrResponse(responseBody);
    }

    /**
     * Parses the OCR.space JSON envelope and returns the concatenated parsed text.
     *
     * @throws AiUnavailableException       on a service-reported error
     * @throws AiResponseNotUsableException when the response contains no text
     */
    private String extractTextFromOcrResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new OcrUnavailableException("The receipt scanner returned an empty response");
        }

        OcrSpaceResponse response;
        try {
            response = objectMapper.readValue(responseBody, OcrSpaceResponse.class);
        } catch (JacksonException e) {
            log.warn("OCR.space response was not valid JSON", e);
            throw new OcrUnavailableException("The receipt scanner returned an unreadable response");
        }

        if (response.erroredOnProcessing()) {
            log.warn("OCR.space reported a processing error: {}", response.errorMessage());
            throw new OcrUnavailableException("The receipt scanner could not process the image");
        }

        if (response.parsedResults() == null || response.parsedResults().isEmpty()) {
            throw new AiResponseNotUsableException("The receipt scanner found no text in the image");
        }

        StringBuilder combined = new StringBuilder();
        for (OcrSpaceResponse.ParsedResult result : response.parsedResults()) {
            if (result.parsedText() != null && !result.parsedText().isBlank()) {
                combined.append(result.parsedText()).append('\n');
            }
        }

        String text = combined.toString().strip();
        if (text.isEmpty()) {
            throw new AiResponseNotUsableException("The receipt scanner found no text in the image");
        }
        return text;
    }

    // -------------------------------------------------------------------------
    // Line-item extraction
    // -------------------------------------------------------------------------

    /**
     * Scans the raw OCR text line by line and extracts item rows.
     * <p>
     * Returns a response with the items found and the receipt total if one was
     * printed. A result
     * with an empty item list is a valid success -- the caller shows the manual
     * entry form.
     */
    private ParsedReceiptResponse extractItems(String rawText) {
        String[] lines = rawText.split("\\r?\\n");

        List<OcrReceiptItem> items = new ArrayList<>();
        long suggestedTotalPiastres = 0L;

        // Try to read the printed total from any line, independently of item matching.
        for (String line : lines) {
            Matcher totalMatcher = TOTAL_LINE.matcher(line);
            if (totalMatcher.find()) {
                suggestedTotalPiastres = toPiastres(totalMatcher.group("price"), 0L);
                break; // first total wins
            }
        }

        for (String rawLine : lines) {
            if (items.size() >= MAX_ITEMS) {
                break;
            }
            String line = rawLine.strip();
            if (line.isEmpty() || isBoilerplate(line)) {
                continue;
            }
            Matcher m = ITEM_LINE.matcher(line);
            if (!m.matches()) {
                continue;
            }

            String name = m.group("name").strip();
            String qtyGroup = m.group("qty1") != null ? m.group("qty1") : m.group("qty2");
            int quantity = parseQuantity(qtyGroup);
            long unitPrice = toPiastres(m.group("price"), -1L);
            if (unitPrice <= 0) {
                continue; // price zero or unparseable — skip
            }

            // If the receipt shows a line total, convert it to a unit price.
            if (quantity > 1) {
                unitPrice = Math.round((double) unitPrice / quantity);
            }

            items.add(new OcrReceiptItem(name, quantity, unitPrice));
        }

        return new ParsedReceiptResponse(List.copyOf(items), suggestedTotalPiastres);
    }

    /**
     * Whether the line is receipt boilerplate that should not be treated as a
     * purchased item.
     */
    private static boolean isBoilerplate(String line) {
        for (String pattern : BOILERPLATE_PATTERNS) {
            if (line.matches(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses a quantity string (e.g. {@code "2"}) to an int, defaulting to 1 when
     * absent.
     * A value of zero is also treated as 1.
     */
    private static int parseQuantity(String qtyGroup) {
        if (qtyGroup == null || qtyGroup.isBlank()) {
            return 1;
        }
        try {
            int qty = Integer.parseInt(qtyGroup.strip());
            return qty > 0 ? qty : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Parses a price string from OCR text into piastres.
     * <p>
     * Commas are treated as thousands separators (e.g. {@code "1,200.50"} → 1
     * 200.50 EGP). A
     * single comma with exactly two digits after it is treated as a decimal
     * separator instead
     * (European style: {@code "12,50"} → 12.50 EGP). Rounded to the nearest
     * piastre.
     *
     * @param priceText the raw price string from the regex match
     * @param fallback  returned when the string cannot be parsed
     * @return amount in piastres, or {@code fallback}
     */
    private static long toPiastres(String priceText, long fallback) {
        if (priceText == null || priceText.isBlank()) {
            return fallback;
        }
        try {
            String normalised = priceText.strip();

            // "1,200.50" → thousands separator; "12,50" → decimal separator (European)
            if (normalised.contains(",") && !normalised.contains(".")) {
                int commaPos = normalised.lastIndexOf(',');
                if (normalised.length() - commaPos - 1 == 2) {
                    // exactly two digits after comma → treat as decimal
                    normalised = normalised.replace(',', '.');
                } else {
                    // thousands separator → strip
                    normalised = normalised.replace(",", "");
                }
            } else {
                // Remove thousands commas before a decimal point
                normalised = normalised.replace(",", "");
            }

            BigDecimal egp = new BigDecimal(normalised);
            long piastres = egp.setScale(2, RoundingMode.HALF_UP)
                    .movePointRight(2)
                    .longValueExact();
            return piastres > 0 ? piastres : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Returns a safe content-type string for the base64 data URI.
     * Defaults to {@code image/jpeg} for anything unrecognised.
     */
    private static String sanitizeContentType(String contentType) {
        if (contentType != null && contentType.startsWith("image/")) {
            // Strip parameters (e.g. "image/jpeg; charset=UTF-8")
            return contentType.split(";")[0].strip();
        }
        return "image/jpeg";
    }
}
