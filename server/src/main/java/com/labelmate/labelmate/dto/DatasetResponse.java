package com.labelmate.labelmate.dto;

import com.labelmate.labelmate.model.Dataset;
import com.labelmate.labelmate.model.DatasetStatus;
import java.time.LocalDateTime;
import java.util.List;

public record DatasetResponse(
        Long id,
        String name,
        String description,
        DatasetStatus status,
        String fileName,
        String filePath,
        Long fileSizeBytes,
        String checksumSha256,
        List<String> columns,
        long itemCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static DatasetResponse from(Dataset dataset) {
        return from(dataset, 0);
    }

    public static DatasetResponse from(Dataset dataset, long itemCount) {
        return new DatasetResponse(
                dataset.getId(),
                dataset.getName(),
                dataset.getDescription(),
                dataset.getStatus(),
                dataset.getFileName(),
                dataset.getFilePath(),
                dataset.getFileSizeBytes(),
                dataset.getChecksumSha256(),
                DatasetItemResponse.parseColumns(dataset.getColumnsJson()),
                itemCount,
                dataset.getCreatedAt(),
                dataset.getUpdatedAt());
    }
}
