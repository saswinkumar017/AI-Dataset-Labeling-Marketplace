package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One typed feature (column) of a multi-feature dataset.
 *
 * <p>Types are advisory — they drive input widgets and hints in the UI;
 * ingest never rejects data for a mismatch. Supported: TEXT, NUMBER,
 * CATEGORY, BOOLEAN, IMAGE.
 */
public record FeatureDef(
        @NotBlank(message = "feature name is required")
        @Size(max = 100, message = "feature name must be at most 100 characters")
        String name,

        @Pattern(regexp = "^(?i)(TEXT|NUMBER|CATEGORY|BOOLEAN|IMAGE)$",
                message = "feature type must be one of TEXT, NUMBER, CATEGORY, BOOLEAN, IMAGE")
        String type) {

    public FeatureDef(String name) {
        this(name, "TEXT");
    }

    public String normalizedType() {
        if (type == null || type.isBlank()) {
            return "TEXT";
        }
        return type.strip().toUpperCase();
    }
}
