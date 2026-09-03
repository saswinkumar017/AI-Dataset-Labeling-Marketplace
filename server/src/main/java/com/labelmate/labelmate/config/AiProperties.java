package com.labelmate.labelmate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-level AI settings (secret-free).
 *
 * <p>The provider API key lives only in {@code spring.ai.openai.api-key} and
 * is never bound here, so this bean is safe to inspect and log. AI is
 * dormant unless {@code app.ai.enabled=true} <em>and</em> a chat model is
 * configured ({@code spring.ai.model.chat=openai} plus key and model).
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** Master switch for AI assistance. Default off; manual work always works. */
    private boolean enabled = false;

    /** Display name of the configured model, persisted with suggestions. */
    private String model = "openrouter/auto";

    /** Provider base URL (OpenAI-compatible). Points at OpenRouter by default. */
    private String baseUrl = "https://openrouter.ai/api/v1";

    /** Max characters of item text sent in a single prompt (guardrail). */
    private int maxPromptChars = 4000;

    /** Upper bound in seconds for one provider call. */
    private int requestTimeoutSeconds = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getMaxPromptChars() {
        return maxPromptChars;
    }

    public void setMaxPromptChars(int maxPromptChars) {
        this.maxPromptChars = maxPromptChars;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }
}
