package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TaskBulkRequest(
        @NotNull(message = "items are required")
        @Size(min = 1, max = 500, message = "provide between 1 and 500 items per request")
        List<@Size(min = 1, max = 5000, message = "each item must be between 1 and 5000 characters") String> items) {
}
