package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DatasetRequest(
        @NotBlank(message = "name is required")
        @Size(min = 2, max = 150, message = "name must be between 2 and 150 characters")
        String name,

        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        @Size(max = 255, message = "fileName must be at most 255 characters")
        @Pattern(regexp = "^[^/\\\\]*$", message = "fileName must be a plain file name without path separators")
        String fileName,

        @Size(max = 500, message = "filePath must be at most 500 characters")
        @Pattern(regexp = "^(?![/\\\\])(?!.*\\.\\.).*$", message = "filePath must be a relative path without parent references")
        String filePath,

        @PositiveOrZero(message = "fileSizeBytes must be zero or positive")
        Long fileSizeBytes,

        @Pattern(regexp = "^[0-9a-f]{64}$", message = "checksumSha256 must be 64 lowercase hex characters")
        String checksumSha256,

        @Pattern(regexp = "^(?i)(TEXT|TABULAR|IMAGE|MIXED)?$",
                message = "datasetType must be one of TEXT, TABULAR, IMAGE, MIXED")
        String datasetType,

        @Size(max = 100, message = "provide at most 100 features")
        List<FeatureDef> features,

        @Size(max = 100, message = "labelColumn must be at most 100 characters")
        String labelColumn) {

    public DatasetRequest(String name, String description) {
        this(name, description, null, null, null, null, null, null, null);
    }

    /** Compatibility for callers built before typed features existed. */
    public DatasetRequest(
            String name,
            String description,
            String fileName,
            String filePath,
            Long fileSizeBytes,
            String checksumSha256) {
        this(name, description, fileName, filePath, fileSizeBytes, checksumSha256, null, null, null);
    }
}
