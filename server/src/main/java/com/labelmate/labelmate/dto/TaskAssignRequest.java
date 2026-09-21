package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record TaskAssignRequest(
        @NotBlank(message = "assigneeEmail is required")
        @Email(message = "assigneeEmail must be a valid email")
        String assigneeEmail) {
}
