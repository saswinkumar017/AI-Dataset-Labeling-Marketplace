package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Which dataset columns a project labels — and which it hides.
 *
 * <p>{@code labelColumn} names the ground-truth answer column (hidden from
 * every labeling read so annotators never see the answer first; falls back
 * to the dataset's own label column when blank). {@code featureColumns} is
 * an explicit allowlist of columns shown as features (blank = all except
 * hidden). {@code hiddenColumns} names extra columns (ids, metadata) kept
 * out of labeling reads. Exports always keep every column.
 */
public record ProjectLabelConfigRequest(
        @Size(max = 100, message = "labelColumn must be at most 100 characters")
        String labelColumn,

        @Size(max = 100, message = "provide at most 100 feature columns")
        List<@Size(max = 100, message = "each feature column must be at most 100 characters") String> featureColumns,

        @Size(max = 100, message = "provide at most 100 hidden columns")
        List<@Size(max = 100, message = "each hidden column must be at most 100 characters") String> hiddenColumns) {
}
