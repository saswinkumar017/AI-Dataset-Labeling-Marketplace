package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record DatasetTableRequest(
        @NotNull(message = "columns are required")
        @Size(min = 1, max = 100, message = "provide between 1 and 100 columns")
        List<String> columns,

        @NotNull(message = "rows are required")
        @Size(min = 1, max = 5000, message = "provide between 1 and 5000 rows per request")
        List<Map<String, String>> rows) {
}
