package com.labelmate.labelmate.service.ai;

/**
 * Application-level boundary for AI text completion.
 *
 * <p>Everything above this interface (annotation workflow, controllers) is
 * provider-agnostic: swapping OpenRouter for another OpenAI-compatible
 * provider only touches configuration, never callers. Implementations must
 * never leak provider SDK types, and must fail with {@link AiException}
 * instead of provider exceptions.
 */
public interface AiClient {

    /**
     * Completes one chat turn.
     *
     * @param systemPrompt role and task instructions (no secrets, no user data)
     * @param userPrompt the single item context the model should react to
     * @return the model's raw text output, never blank
     * @throws AiException for every failure mode (not configured, provider
     *         error, timeout, empty/unusable output)
     */
    String complete(String systemPrompt, String userPrompt);
}
