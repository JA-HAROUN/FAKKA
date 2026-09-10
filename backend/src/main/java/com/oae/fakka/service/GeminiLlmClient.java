package com.oae.fakka.service;

import com.oae.fakka.config.AiProperties;
import com.oae.fakka.exception.AiUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/** Calls the Google Gemini generateContent API over HTTP. */
@Slf4j
@Component
public class GeminiLlmClient implements LlmClient {

    private static final String GENERATE_CONTENT_PATH = "/v1beta/models/%s:generateContent";

    private final AiProperties properties;
    private final RestClient restClient;

    public GeminiLlmClient(AiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactoryWithTimeouts(properties))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
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
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userMessage)))),
                "generationConfig", Map.of(
                        "maxOutputTokens", properties.maxTokens(),
                        "responseMimeType", "application/json"));

        GeminiGenerateContentResponse reply;
        try {
            reply = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(GENERATE_CONTENT_PATH.formatted(properties.model()))
                            .queryParam("key", properties.apiKey())
                            .build())
                    .body(body)
                    .retrieve()
                    .body(GeminiGenerateContentResponse.class);
        } catch (RestClientException exception) {
            log.warn("Gemini expense parser call failed", exception);
            throw new AiUnavailableException("The expense parser could not be reached", exception);
        }

        return firstTextOf(reply);
    }

    private static String firstTextOf(GeminiGenerateContentResponse reply) {
        if (reply == null || reply.candidates() == null || reply.candidates().isEmpty()) {
            throw new AiUnavailableException("The Gemini expense parser returned no candidates");
        }

        String text = reply.candidates().stream()
                .filter(candidate -> candidate.content() != null && candidate.content().parts() != null)
                .flatMap(candidate -> candidate.content().parts().stream())
                .map(GeminiGenerateContentResponse.Part::text)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);

        if (text == null) {
            log.warn("Gemini expense parser returned no text");
            throw new AiUnavailableException("The expense parser returned no usable answer");
        }
        return text;
    }

    private static ClientHttpRequestFactory requestFactoryWithTimeouts(AiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        return factory;
    }
}