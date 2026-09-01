package com.labelmate.labelmate.service.ai;


/**
 * Fallback {@link AiClient} used whenever AI is not enabled.
 *
 * <p>This is the default bean: with {@code app.ai.enabled=false} the
 * application starts and runs with zero provider requirements, and every AI
 * call site degrades to "assist unavailable, annotate manually".
 */
public class NoOpAiClient implements AiClient {

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        throw new AiException(
                AiException.Reason.NOT_CONFIGURED,
                "AI assistance is not configured (set app.ai.enabled=true with provider credentials)");
    }
}
