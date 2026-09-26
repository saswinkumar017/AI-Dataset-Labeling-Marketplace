package com.labelmate.labelmate.service.ai;

import com.labelmate.labelmate.config.AiProperties;
import com.labelmate.labelmate.model.AppSetting;
import com.labelmate.labelmate.repository.AppSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Effective AI configuration: admin-panel overrides first, startup
 * configuration second.
 *
 * <p>The admin panel stores credentials in {@code app_settings}; the
 * provider key and model chosen there take effect without a restart. The
 * base URL stays startup-only (it is baked into the chat client), and the
 * {@code spring.ai.model.chat} bean selection stays environment-driven.
 * Every lookup tolerates a missing table and falls back silently.
 */
@Service
public class AiSettingsResolver {

    public static final String KEY_API_KEY = "OPENROUTER_API_KEY";
    public static final String KEY_MODEL = "AI_MODEL";
    public static final String KEY_BASE_URL = "OPENROUTER_BASE_URL";
    public static final String KEY_ENABLED = "APP_AI_ENABLED";
    public static final String KEY_CHAT_MODEL = "SPRING_AI_MODEL_CHAT";

    private final AppSettingRepository settings;
    private final AiProperties properties;
    private final String startupApiKey;

    public AiSettingsResolver(
            AppSettingRepository settings,
            AiProperties properties,
            @Value("${spring.ai.openai.api-key:}") String startupApiKey) {
        this.settings = settings;
        this.properties = properties;
        this.startupApiKey = startupApiKey;
    }

    public boolean isEnabled() {
        return Boolean.parseBoolean(stored(KEY_ENABLED, String.valueOf(properties.isEnabled())));
    }

    public String apiKey() {
        String override = stored(KEY_API_KEY, "");
        if (!override.isBlank()) {
            return override;
        }
        return startupApiKey == null ? "" : startupApiKey;
    }

    public String model() {
        return stored(KEY_MODEL, properties.getModel());
    }

    public String baseUrl() {
        return stored(KEY_BASE_URL, properties.getBaseUrl());
    }

    /**
     * Which Spring AI chat backend to boot ({@code openai} for any
     * OpenAI-compatible provider, {@code none} to skip it). Startup-only:
     * it selects beans, so a saved change needs a backend restart.
     */
    public String chatModel() {
        return stored(KEY_CHAT_MODEL, "openai");
    }

    public static String mask(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String tail = key.length() <= 4 ? key : key.substring(key.length() - 4);
        return "****" + tail;
    }

    private String stored(String key, String fallback) {
        try {
            return settings.findById(key).map(AppSetting::getValue).orElse(fallback);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}
