package com.labelmate.labelmate.dto;

import com.labelmate.labelmate.model.Annotation;
import com.labelmate.labelmate.model.AnnotationSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AnnotationResponse(
        Long id,
        Long taskId,
        Long projectId,
        String label,
        Long labelId,
        AnnotationSource source,
        BigDecimal confidence,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AnnotationResponse from(Annotation annotation) {
        Long projectId = annotation.getTask() != null && annotation.getTask().getProject() != null
                ? annotation.getTask().getProject().getId()
                : null;
        String label = annotation.getLabel() != null
                ? annotation.getLabel().getName()
                : annotation.getContent();
        Long labelId = annotation.getLabel() != null ? annotation.getLabel().getId() : null;
        return new AnnotationResponse(
                annotation.getId(),
                annotation.getTask() != null ? annotation.getTask().getId() : null,
                projectId,
                label,
                labelId,
                annotation.getSource(),
                annotation.getConfidence(),
                annotation.getContent(),
                annotation.getCreatedAt(),
                annotation.getUpdatedAt());
    }
}
