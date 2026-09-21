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
        long totalTasks,
        long pendingTasks,
        long submittedTasks,
        long approvedTasks,
        long rejectedTasks,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** Workflow counts driving progress displays. */
    public record TaskCounts(
            long total, long pending, long submitted, long approved, long rejected) {
    }

    public static ProjectResponse from(Project project) {
        return from(project, List.of());
    }

    public static ProjectResponse from(Project project, List<String> labels) {
        return from(project, labels, new TaskCounts(0, 0, 0, 0, 0));
    }

    public static ProjectResponse from(Project project, List<String> labels, TaskCounts counts) {
        return new ProjectResponse(
                project.getId(),
                project.getDataset().getId(),
                project.getName(),
                project.getInstructions(),
                project.getLabelType(),
                List.copyOf(labels),
                project.getStatus(),
                counts.total(),
                counts.pending(),
                counts.submitted(),
                counts.approved(),
                counts.rejected(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
