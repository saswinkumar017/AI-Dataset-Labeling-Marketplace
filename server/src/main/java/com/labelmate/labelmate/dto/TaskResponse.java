package com.labelmate.labelmate.dto;

import com.labelmate.labelmate.model.Task;
import com.labelmate.labelmate.model.TaskStatus;
import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        Long projectId,
        Long datasetId,
        Integer itemIndex,
        String itemData,
        TaskStatus status,
        Long assignedToId,
        String assignedToEmail,
        String projectName,
        DatasetItemResponse item,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getProject() != null ? task.getProject().getId() : null,
                task.getDataset() != null ? task.getDataset().getId() : null,
                task.getItemIndex(),
                task.getItemData(),
                task.getStatus(),
                task.getAssignedTo() != null ? task.getAssignedTo().getId() : null,
                task.getAssignedTo() != null ? task.getAssignedTo().getEmail() : null,
                task.getProject() != null ? task.getProject().getName() : null,
                task.getDatasetItem() != null ? DatasetItemResponse.from(task.getDatasetItem()) : null,
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
