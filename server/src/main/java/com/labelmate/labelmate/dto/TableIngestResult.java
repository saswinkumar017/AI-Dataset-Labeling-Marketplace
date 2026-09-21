package com.labelmate.labelmate.dto;

import java.util.List;

public record TableIngestResult(
        Long datasetId,
        List<String> columns,
        long inserted,
        long totalItems) {
}
