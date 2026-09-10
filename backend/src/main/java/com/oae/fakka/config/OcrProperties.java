package com.oae.fakka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for the receipt OCR parser (FR-24 to FR-28).
 *
 * <h2>Absent configuration is a supported state, not a startup failure</h2>
 * BR-7 says AI/OCR must never block manual entry, so a missing API key cannot stop the
 * application from booting -- every other endpoint must keep working. An unconfigured OCR parser
 * simply reports itself unavailable, and the client falls back to manual item entry (FR-28).
 *
 * @param enabled    turns the feature off without removing the key, for a demo where OCR should be
 *                   inert
 * @param apiKey     the OCR.space API key. Blank means unconfigured, which reads the same as
 *                   disabled. Supply it through the environment, never through a committed file
 * @param baseUrl    the OCR.space API host, overridable so a test can point at a local stub
 * @param timeout    per-request ceiling. A parse attempt that hangs is worse than one that fails,
 *                   because the user could have entered the items by hand meanwhile
 * @param maxFileSizeBytes  upload ceiling enforced before the image is sent to the OCR service.
 *                          A very large image is unlikely to improve accuracy and wastes bandwidth.
 */
@ConfigurationProperties(prefix = "fakka.ocr")
public record OcrProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        Duration timeout,
        long maxFileSizeBytes
) {

    private static final String DEFAULT_BASE_URL = "https://api.ocr.space";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024L; // 5 MB

    /**
     * Defaults are applied here rather than in the property file so that they hold for a test or
     * any other caller that constructs this directly. {@code enabled} deliberately has no default
     * of its own: a boolean is false when absent, so the constructor treats it as on unless the
     * key is missing, and turning the feature off is an explicit {@code false} with a key present.
     */
    public OcrProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        baseUrl = isBlank(baseUrl) ? DEFAULT_BASE_URL : baseUrl.trim();
        timeout = timeout == null || timeout.isZero() || timeout.isNegative()
                ? DEFAULT_TIMEOUT
                : timeout;
        maxFileSizeBytes = maxFileSizeBytes <= 0 ? DEFAULT_MAX_FILE_SIZE_BYTES : maxFileSizeBytes;
    }

    /** A parser with no key cannot work, and says so rather than failing later mid-request. */
    public boolean isUsable() {
        return enabled && !apiKey.isEmpty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
