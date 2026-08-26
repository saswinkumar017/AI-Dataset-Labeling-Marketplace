package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnnotationUpdateRequest(
        @NotBlank(message = "label is required")
        @Size(min = 1, max = 100, message = "label must be between 1 and 100 characters")
        String label) {
}
