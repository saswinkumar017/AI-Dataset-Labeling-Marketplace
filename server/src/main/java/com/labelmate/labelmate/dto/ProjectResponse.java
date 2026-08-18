package com.labelmate.labelmate.dto;

import com.labelmate.labelmate.model.Project;
import com.labelmate.labelmate.model.ProjectStatus;
import java.time.LocalDateTime;

public record ProjectResponse(
        Long id,
        Long datasetId,
        String name,
        String instructions,
        String labelType,
        ProjectStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getDataset().getId(),
                project.getName(),
                project.getInstructions(),
                project.getLabelType(),
                project.getStatus(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
