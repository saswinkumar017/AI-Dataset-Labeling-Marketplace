package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SuggestRequest(
        @NotNull(message = "labels are required")
        @Size(min = 1, max = 50, message = "provide between 1 and 50 labels")
        List<@Size(min = 1, max = 100, message = "each label must be between 1 and 100 characters") String> labels) {
}
