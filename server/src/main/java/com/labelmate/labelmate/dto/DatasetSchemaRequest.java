package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Typed feature schema of a dataset: the named feature columns annotators
 * label, each with a type driving the input widget (TEXT, NUMBER, CATEGORY,
 * BOOLEAN, IMAGE), plus the optional ground-truth column that labeling must
 * hide.
 */
public record DatasetSchemaRequest(
        @Pattern(regexp = "^(?i)(TEXT|TABULAR|IMAGE|MIXED)?$",
                message = "datasetType must be one of TEXT, TABULAR, IMAGE, MIXED")
        String datasetType,

        @Size(max = 100, message = "provide at most 100 features")
        List<FeatureDef> features,

        @Size(max = 100, message = "labelColumn must be at most 100 characters")
        String labelColumn) {
}
