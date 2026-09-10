package com.oae.fakka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for the natural-language expense parser (FR-20 to FR-23).
 *
 * <h2>Absent configuration is a supported state, not a startup failure</h2>
 * BR-7 says AI must never block manual entry, so a missing API key cannot stop the application
 * from booting -- every other endpoint has to keep working. An unconfigured parser simply reports
 * itself unavailable, and the client falls back to the manual form.
 *
 * @param enabled    turns the feature off without removing the key, for a demo where the AI
 *                   should be inert
 * @param apiKey     the Anthropic API key. Blank means unconfigured, which reads the same as
 *                   disabled. Supply it through the environment, never through a committed file
 * @param baseUrl    the API host, overridable so a test can point at a local stub
 * @param model      the model id. Complete as-is -- no date suffix
 * @param maxTokens  output ceiling. The answer is a small JSON object, but thinking tokens count
 *                   towards this, so there is headroom rather than the bare minimum
 * @param effort     how hard the model should work. Extraction from one or two sentences is not
 *                   a reasoning problem, and a person is waiting for the response
 * @param timeout    per-request ceiling. A parse attempt that hangs is worse than one that fails,
 *                   because the user could have typed the expense in by hand meanwhile
 */
@ConfigurationProperties(prefix = "fakka.ai")
public record AiProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String model,
        int maxTokens,
        String effort,
        Duration timeout
) {

    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String DEFAULT_MODEL = "claude-opus-5";
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final String DEFAULT_EFFORT = "low";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    /**
     * Defaults are applied here rather than in the property file so that they hold for a test or
     * any other caller that constructs this directly. {@code enabled} deliberately has no default
     * of its own: a boolean is false when absent, so the constructor treats it as on unless the
     * key is missing, and turning the feature off is an explicit {@code false} with a key present.
     */
    public AiProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        baseUrl = isBlank(baseUrl) ? DEFAULT_BASE_URL : baseUrl.trim();
        model = isBlank(model) ? DEFAULT_MODEL : model.trim();
        maxTokens = maxTokens <= 0 ? DEFAULT_MAX_TOKENS : maxTokens;
        effort = isBlank(effort) ? DEFAULT_EFFORT : effort.trim();
        timeout = timeout == null || timeout.isZero() || timeout.isNegative()
                ? DEFAULT_TIMEOUT
                : timeout;
    }

    /** A parser with no key cannot work, and says so rather than failing later mid-request. */
    public boolean isUsable() {
        return enabled && !apiKey.isEmpty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
