package com.labelmate.labelmate.service.ai;

import com.labelmate.labelmate.config.AiProperties;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;

/**
 * {@link AiClient} backed by Spring AI.
 *
 * <p>Active only when {@code app.ai.enabled=true}. The chat model itself is
 * still optional at runtime: if the operator enabled the flag without
 * configuring a model, calls fail with a clear {@code NOT_CONFIGURED}
 * error instead of crashing startup, so manual annotation keeps working.
 * Provider calls run on virtual threads with a bounded wait, and failures
 * are mapped to {@link AiException} reasons. Only prompt/output sizes are
 * logged — never keys, prompts, or dataset content.
 */
public class SpringAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(SpringAiClient.class);

    private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
    private final AiProperties properties;
    private final String apiKey;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SpringAiClient(
            ObjectProvider<ChatClient.Builder> chatClientBuilder,
            AiProperties properties,
            @Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.chatClientBuilder = chatClientBuilder;
        this.properties = properties;
        this.apiKey = apiKey;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank() || userPrompt == null || userPrompt.isBlank()) {
            throw new AiException(AiException.Reason.INVALID_RESPONSE, "AI prompt must not be empty");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiException(
                    AiException.Reason.NOT_CONFIGURED,
                    "AI provider key is not configured (set OPENROUTER_API_KEY)");
        }
        ChatClient.Builder builder = chatClientBuilder.getIfAvailable();
        if (builder == null) {
            throw new AiException(
                    AiException.Reason.NOT_CONFIGURED,
                    "AI is enabled but no chat model is configured "
                            + "(set spring.ai.model.chat=openai with api-key and model)");
        }
        ChatClient chatClient = builder.build();
        long timeoutSeconds = Math.max(1, properties.getRequestTimeoutSeconds());
        try {
            String content = CompletableFuture.supplyAsync(
                            () -> chatClient.prompt().system(systemPrompt).user(userPrompt).call().content(),
                            executor)
                    .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .join();
            if (content == null || content.isBlank()) {
                throw new AiException(AiException.Reason.INVALID_RESPONSE, "AI returned an empty response");
            }
            log.info(
                    "AI completion succeeded (system {} chars, prompt {} chars, output {} chars)",
                    systemPrompt.length(),
                    userPrompt.length(),
                    content.length());
            return content;
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof TimeoutException) {
                log.warn("AI completion timed out after {}s", timeoutSeconds);
                throw new AiException(
                        AiException.Reason.TIMEOUT,
                        "AI request timed out after " + timeoutSeconds + "s",
                        cause);
            }
            log.warn("AI completion failed: {}", cause.getClass().getSimpleName());
            throw new AiException(AiException.Reason.PROVIDER_ERROR, "AI provider call failed", cause);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
