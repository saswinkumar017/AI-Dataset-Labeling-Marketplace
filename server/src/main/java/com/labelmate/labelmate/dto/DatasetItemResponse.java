package com.labelmate.labelmate.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelmate.labelmate.model.DatasetItem;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DatasetItemResponse(
        Long id,
        Long datasetId,
        String content,
        Map<String, String> rowData,
        String imageUrl,
        String mediaType,
        LocalDateTime createdAt) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static DatasetItemResponse from(DatasetItem item) {
        return new DatasetItemResponse(
                item.getId(),
                item.getDataset() != null ? item.getDataset().getId() : null,
                item.getContent(),
                parseRowData(item.getRowDataJson()),
                item.getImageUrl(),
                item.getMediaType(),
                item.getCreatedAt());
    }

    public static Map<String, String> parseRowData(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            LinkedHashMap<String, String> parsed =
                    MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {});
            return parsed == null ? Map.of() : Map.copyOf(parsed);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public static List<String> parseColumns(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = MAPPER.readValue(json, new TypeReference<List<String>>() {});
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
