package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProjectRequest(
        @NotNull(message = "datasetId is required")
        Long datasetId,

        @NotBlank(message = "name is required")
        @Size(min = 2, max = 150, message = "name must be between 2 and 150 characters")
        String name,

        @Size(max = 2000, message = "instructions must be at most 2000 characters")
        String instructions,

        @Size(max = 50, message = "labelType must be at most 50 characters")
        String labelType,

        @Size(max = 50, message = "at most 50 labels are allowed")
        List<@Size(min = 1, max = 100, message = "each label must be between 1 and 100 characters") String> labels) {

    /** Compatibility for callers without a label scheme. */
    public ProjectRequest(Long datasetId, String name, String instructions, String labelType) {
        this(datasetId, name, instructions, labelType, null);
    }
}
