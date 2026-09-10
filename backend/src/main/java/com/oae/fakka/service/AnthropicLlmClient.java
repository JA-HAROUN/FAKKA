package com.oae.fakka.service;

import com.oae.fakka.config.AiProperties;
import com.oae.fakka.exception.AiUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Calls the Claude Messages API over HTTP.
 *
 * <h2>Raw HTTP rather than the Anthropic Java SDK</h2>
 * The SDK is the better default and this class is shaped so it can be swapped in behind
 * {@link LlmClient} without touching anything else. It is not used here because this build cannot
 * resolve new dependencies from Maven Central, so adding it would leave the project unable to
 * compile or run its tests. One request and one response is also the entire surface needed --
 * no tools, no streaming, no conversation state.
 *
 * <h2>Request shape</h2>
 * {@code POST /v1/messages} with the {@code x-api-key} and {@code anthropic-version} headers.
 * Thinking is left at its default, which is on for this model; effort is set low instead, because
 * pulling five fields out of one sentence is not a reasoning problem and somebody is waiting for
 * the answer. {@code max_tokens} has room to spare since thinking tokens count against it, and a
 * truncated reply would arrive here as malformed JSON.
 *
 * <h2>Every failure becomes one exception</h2>
 * A timeout, a refused connection, a 429, a 500, an empty reply: all of them are
 * {@link AiUnavailableException}, because the caller has exactly one useful response to all of
 * them (BR-7). The distinction that does matter -- reachable but unusable -- is drawn one layer
 * up, where the reply is parsed.
 */
@Slf4j
@Component
public class AnthropicLlmClient implements LlmClient {

    private static final String MESSAGES_PATH = "/v1/messages";
    private static final String API_KEY_HEADER = "x-api-key";
    private static final String VERSION_HEADER = "anthropic-version";
    private static final String API_VERSION = "2023-06-01";

    private final AiProperties properties;
    private final RestClient restClient;

    public AnthropicLlmClient(AiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactoryWithTimeouts(properties))
                .defaultHeader(VERSION_HEADER, API_VERSION)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public boolean isAvailable() {
        return properties.isUsable();
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        if (!isAvailable()) {
            throw new AiUnavailableException("The expense parser is not configured");
        }

        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "max_tokens", properties.maxTokens(),
                "output_config", Map.of("effort", properties.effort()),
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userMessage)));

        AnthropicMessage reply;
        try {
            reply = restClient.post()
                    .uri(MESSAGES_PATH)
                    .header(API_KEY_HEADER, properties.apiKey())
                    .body(body)
                    .retrieve()
                    .body(AnthropicMessage.class);
        } catch (RestClientException exception) {
            /*
             * Covers the timeout, the refused connection and every non-2xx status. The cause is
             * logged rather than returned: an upstream error body can carry request detail, and a
             * client that only needs to know to show the manual form should not receive it.
             */
            log.warn("Expense parser call failed", exception);
            throw new AiUnavailableException("The expense parser could not be reached", exception);
        }

        return firstTextOf(reply);
    }

    /**
     * The reply text, or a failure if there is none.
     * <p>
     * A model can stop for reasons that leave no text at all -- a refusal, or a ceiling hit while
     * still thinking. Those are unavailability rather than a bad answer, because there is nothing
     * to try to parse.
     */
    private static String firstTextOf(AnthropicMessage reply) {
        if (reply == null || reply.content() == null) {
            throw new AiUnavailableException("The expense parser returned no content");
        }

        String text = reply.content().stream()
                .filter(block -> "text".equals(block.type()))
                .map(AnthropicMessage.ContentBlock::text)
                .filter(blockText -> blockText != null && !blockText.isBlank())
                .findFirst()
                .orElse(null);

        if (text == null) {
            log.warn("Expense parser returned no text block, stop_reason={}", reply.stopReason());
            throw new AiUnavailableException("The expense parser returned no usable answer");
        }
        return text;
    }

    /**
     * Timeouts are set on both the connect and the read: without them the default is unbounded,
     * and a request thread parked on a hung upstream is exactly the failure BR-7 exists to avoid.
     */
    private static ClientHttpRequestFactory requestFactoryWithTimeouts(AiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        return factory;
    }
}
