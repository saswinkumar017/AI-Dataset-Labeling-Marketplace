package com.labelmate.labelmate.dto;

import com.labelmate.labelmate.model.AiSuggestion;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What the frontend receives for an AI suggestion: the label, an optional
 * confidence, and the model name. The raw provider output is intentionally
 * absent — it stays server-side on the {@code ai_suggestions} row.
 */
public record SuggestionResponse(
        Long id,
        Long taskId,
        String suggestedLabel,
        BigDecimal confidence,
        String model,
        LocalDateTime createdAt) {

    public static SuggestionResponse from(AiSuggestion suggestion) {
        return new SuggestionResponse(
                suggestion.getId(),
                suggestion.getTask() != null ? suggestion.getTask().getId() : null,
                suggestion.getSuggestedLabel() != null
                        ? suggestion.getSuggestedLabel().getName()
                        : suggestion.getSuggestedLabelName(),
                suggestion.getConfidence(),
                suggestion.getModel(),
                suggestion.getCreatedAt());
    }
}
