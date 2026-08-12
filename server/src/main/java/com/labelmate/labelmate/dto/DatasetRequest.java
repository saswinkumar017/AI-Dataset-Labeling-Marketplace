package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DatasetRequest(
        @NotBlank(message = "name is required")
        @Size(min = 2, max = 150, message = "name must be between 2 and 150 characters")
        String name,

        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description) {
}
