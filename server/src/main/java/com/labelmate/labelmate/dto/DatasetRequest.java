package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

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
        String checksumSha256) {

    public DatasetRequest(String name, String description) {
        this(name, description, null, null, null, null);
    }
}
