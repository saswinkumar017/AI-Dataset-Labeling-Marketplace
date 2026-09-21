package com.labelmate.labelmate.repository;

import com.labelmate.labelmate.model.Task;
import com.labelmate.labelmate.model.TaskStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProjectIdOrderByItemIndexAscIdAsc(Long projectId);

    List<Task> findByProjectIdAndStatusOrderByItemIndexAscIdAsc(Long projectId, TaskStatus status);

    List<Task> findByDatasetId(Long datasetId);

    List<Task> findByAssignedToIdOrderByIdAsc(Long assignedToId);

    /**
     * Ids of dataset items that already have a task in the project, in one
     * query — task generation filters against this set in memory instead of
     * issuing one EXISTS round trip per item (which stalls large datasets
     * past client timeouts).
     */
    @Query("SELECT t.datasetItem.id FROM Task t WHERE t.project.id = :projectId AND t.datasetItem IS NOT NULL")
    List<Long> findDatasetItemIdsByProjectId(@Param("projectId") Long projectId);

    long countByProjectId(Long projectId);

    long countByProjectIdAndStatus(Long projectId, TaskStatus status);
}
