package com.labelmate.labelmate.service.ai;

import java.math.BigDecimal;

/**
 * Structured application-level AI suggestion.
 *
 * <p>This is the only shape the annotation workflow ever sees: a label from
 * the project's own scheme plus an optional confidence. Raw provider output
 * never leaves the suggestion service. Confidence is {@code null} whenever
 * the model did not supply a usable number — it is never invented.
 */
public record SuggestionResult(String suggestedLabel, BigDecimal confidence, String model) {
}
