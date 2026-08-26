package com.labelmate.labelmate.repository;

import com.labelmate.labelmate.model.Task;
import com.labelmate.labelmate.model.TaskStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProjectIdOrderByItemIndexAscIdAsc(Long projectId);

    List<Task> findByProjectIdAndStatusOrderByItemIndexAscIdAsc(Long projectId, TaskStatus status);

    List<Task> findByDatasetId(Long datasetId);
}
