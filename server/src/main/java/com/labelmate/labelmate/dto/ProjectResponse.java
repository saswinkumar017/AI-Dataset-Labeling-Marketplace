package com.labelmate.labelmate.dto;

import com.labelmate.labelmate.model.Project;
import com.labelmate.labelmate.model.ProjectStatus;
import java.time.LocalDateTime;
import java.util.List;

public record ProjectResponse(
        Long id,
        Long datasetId,
        String name,
        String instructions,
        String labelType,
        List<String> labels,
        ProjectStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ProjectResponse from(Project project) {
        return from(project, List.of());
    }

    public static ProjectResponse from(Project project, List<String> labels) {
        return new ProjectResponse(
                project.getId(),
                project.getDataset().getId(),
                project.getName(),
                project.getInstructions(),
                project.getLabelType(),
                List.copyOf(labels),
                project.getStatus(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
