package com.oae.fakka.service;

import com.oae.fakka.exception.AiUnavailableException;

/**
 * One call to a large language model: a system prompt in, the model text out.
 *
 * <h2>Why this is an interface</h2>
 * Two reasons, both practical. It keeps {@link NaturalLanguageExpenseService} testable without a
 * network or an API key, which matters because the interesting cases there are all failures --
 * a truncated reply, a name that matches nobody, a timeout. And it keeps the provider in one
 * class, so moving from raw HTTP to a vendor SDK, or to a different model host, changes one
 * implementation and nothing else.
 * <p>
 * Deliberately narrow: no tools, no streaming, no conversation. The parser sends one prompt and
 * reads one answer, and an interface that promised more would have to be honoured by every
 * future implementation.
 */
public interface LlmClient {

    /**
     * Sends {@code userMessage} under {@code systemPrompt} and returns the model reply text.
     *
     * @return the text of the reply, never null and never blank
     * @throws AiUnavailableException if the model cannot be reached, refuses, answers with an
     *                               error status, or takes longer than the configured timeout.
     *                               Implementations must not leak transport exceptions: BR-7
     *                               depends on this failing predictably
     */
    String complete(String systemPrompt, String userMessage);

    /**
     * Whether a call is worth attempting at all. False when the model is switched off or
     * unconfigured, which lets the caller answer immediately instead of building a prompt for a
     * request that cannot be sent.
     */
    boolean isAvailable();
}
