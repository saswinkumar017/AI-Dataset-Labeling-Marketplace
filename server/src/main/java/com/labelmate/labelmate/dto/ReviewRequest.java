package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @NotBlank(message = "decision is required")
        @Pattern(regexp = "APPROVED|REJECTED", message = "decision must be APPROVED or REJECTED")
        String decision,

        @Size(max = 2000, message = "comment must be at most 2000 characters")
        String comment) {
}
