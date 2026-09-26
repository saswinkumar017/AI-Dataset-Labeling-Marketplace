package com.labelmate.labelmate.dto;

/** AI settings as shown to admins — key is masked, never returned raw. */
public record AdminAiSettingsResponse(
        String apiKeyMasked,
        boolean apiKeyConfigured,
        String model,
        String baseUrl,
        boolean enabled,
        String chatModel,
        boolean beanActive,
        boolean live) {
}
