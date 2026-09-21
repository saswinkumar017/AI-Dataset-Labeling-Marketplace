package com.labelmate.labelmate.dto;

import com.labelmate.labelmate.model.Annotation;
import com.labelmate.labelmate.model.Review;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * One verified export row: a task item plus its human-approved final label.
 * Only annotations with an approving review ever become rows — submitted but
 * unreviewed work is excluded by construction.
 *
 * <p>Besides the legacy {@code itemData} snapshot, rows carry the full
 * dataset-item payload ({@code content}, {@code rowData}, {@code imageUrl})
 * plus the dataset's ordered {@code columns}, so ML pipelines receive every
 * field, not just the summary the workspace showed.
 */
public record ExportRow(
        Integer itemIndex,
        String itemData,
        String content,
        Map<String, String> rowData,
        String imageUrl,
        List<String> columns,
        String label,
        String source,
        String taskStatus,
        LocalDateTime reviewedAt) {

    public static ExportRow from(
            Annotation annotation, Review review, DatasetItemResponse item, List<String> columns) {
        String label = annotation.getLabel() != null
                ? annotation.getLabel().getName()
                : annotation.getContent();
        return new ExportRow(
                annotation.getTask().getItemIndex(),
                annotation.getTask().getItemData(),
                item != null ? item.content() : annotation.getTask().getItemData(),
                item != null ? item.rowData() : Map.of(),
                item != null ? item.imageUrl() : null,
                columns == null ? List.of() : List.copyOf(columns),
                label,
                annotation.getSource() != null ? annotation.getSource().name() : null,
                annotation.getTask().getStatus().name(),
                review != null ? review.getReviewedAt() : null);
    }
}
