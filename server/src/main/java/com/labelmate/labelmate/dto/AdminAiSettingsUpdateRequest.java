package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.Size;

/**
 * Admin AI settings update. A blank or masked (****) apiKey keeps the stored
 * key; any other value replaces it.
 */
public record AdminAiSettingsUpdateRequest(
        String apiKey,
        @Size(max = 256) String model,
        @Size(max = 512) String baseUrl,
        Boolean enabled,
        @Size(max = 64) String chatModel) {
}
