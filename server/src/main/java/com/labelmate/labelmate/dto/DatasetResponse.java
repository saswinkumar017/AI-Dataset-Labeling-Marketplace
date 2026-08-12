package com.labelmate.labelmate.dto;

import com.labelmate.labelmate.model.Dataset;
import com.labelmate.labelmate.model.DatasetStatus;
import java.time.LocalDateTime;

public record DatasetResponse(
        Long id,
        String name,
        String description,
        DatasetStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static DatasetResponse from(Dataset dataset) {
        return new DatasetResponse(
                dataset.getId(),
                dataset.getName(),
                dataset.getDescription(),
                dataset.getStatus(),
                dataset.getCreatedAt(),
                dataset.getUpdatedAt());
    }
}
