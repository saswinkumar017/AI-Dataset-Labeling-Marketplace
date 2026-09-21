package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DatasetItemsRequest(
        @NotNull(message = "contents are required")
        @Size(min = 1, max = 5000, message = "provide between 1 and 5000 items per request")
        List<@Size(max = 100000, message = "each item must be at most 100000 characters") String> contents) {
}
