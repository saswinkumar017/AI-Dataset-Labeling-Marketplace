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

        @Size(min = 1, max = 50, message = "provide between 1 and 50 labels")
        List<@Size(min = 1, max = 100, message = "each label must be between 1 and 100 characters") String> labels,

        @Size(max = 100, message = "labelColumn must be at most 100 characters")
        String labelColumn,

        @Size(max = 100, message = "provide at most 100 feature columns")
        List<@Size(max = 100, message = "each feature column must be at most 100 characters") String> featureColumns,

        @Size(max = 100, message = "provide at most 100 hidden columns")
        List<@Size(max = 100, message = "each hidden column must be at most 100 characters") String> hiddenColumns) {

    /** Compatibility for callers without a label scheme. */
    public ProjectRequest(Long datasetId, String name, String instructions, String labelType) {
        this(datasetId, name, instructions, labelType, null, null, null, null);
    }

    /** Compatibility for callers built before column config existed. */
    public ProjectRequest(
            Long datasetId,
            String name,
            String instructions,
            String labelType,
            List<String> labels) {
        this(datasetId, name, instructions, labelType, labels, null, null, null);
    }
}
