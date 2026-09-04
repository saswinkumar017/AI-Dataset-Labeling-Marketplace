package com.labelmate.labelmate.dto;

import com.labelmate.labelmate.model.Annotation;
import com.labelmate.labelmate.model.Review;
import java.time.LocalDateTime;

/**
 * One verified export row: a task item plus its human-approved final label.
 * Only annotations with an approving review ever become rows — submitted but
 * unreviewed work is excluded by construction.
 */
public record ExportRow(
        Integer itemIndex,
        String itemData,
        String label,
        String taskStatus,
        LocalDateTime reviewedAt) {

    public static ExportRow from(Annotation annotation, Review review) {
        String label = annotation.getLabel() != null
                ? annotation.getLabel().getName()
                : annotation.getContent();
        return new ExportRow(
                annotation.getTask().getItemIndex(),
                annotation.getTask().getItemData(),
                label,
                annotation.getTask().getStatus().name(),
                review != null ? review.getReviewedAt() : null);
    }
}
