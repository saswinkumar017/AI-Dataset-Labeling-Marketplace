package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.TaskBulkRequest;
import com.labelmate.labelmate.dto.TaskRequest;
import com.labelmate.labelmate.dto.TaskResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Project;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.Task;
import com.labelmate.labelmate.model.TaskStatus;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minimal task queue supporting the annotation workflow.
 *
 * <p>A task is one unit of annotation work inside a project: it carries the
 * item to label ({@code itemData}) and its workflow status. Creation and
 * listing are scoped by project ownership, so one user can never see or feed
 * another user's queue. Status transitions themselves live in
 * {@link AnnotationService} and the later review service.
 */
@Service
public class TaskService {

    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final UserRepository users;

    public TaskService(TaskRepository tasks, ProjectRepository projects, UserRepository users) {
        this.tasks = tasks;
        this.projects = projects;
        this.users = users;
    }

    /**
     * Adds one item to the annotation queue of a project owned by the caller.
     */
    @Transactional
    public TaskResponse create(Long projectId, TaskRequest request, String userEmail) {
        Project project = loadOwnedProject(projectId, userEmail);
        Task task = new Task(project, project.getDataset(), TaskStatus.PENDING, LocalDateTime.now());
        task.setItemData(request.itemData());
        task.setItemIndex(request.itemIndex());
        return TaskResponse.from(tasks.save(task));
    }

    /**
     * Adds many items to the queue at once (dataset/CSV import path).
     * Blank lines are skipped; when nothing usable remains the request is
     * rejected instead of silently creating an empty batch. New items are
     * appended after the highest existing index so queue order stays stable.
     */
    @Transactional
    public List<TaskResponse> createBulk(Long projectId, TaskBulkRequest request, String userEmail) {
        Project project = loadOwnedProject(projectId, userEmail);
        List<String> usable = request.items().stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::strip)
                .toList();
        if (usable.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "no usable items provided");
        }
        int nextIndex = tasks.findByProjectIdOrderByItemIndexAscIdAsc(project.getId()).stream()
                .filter(task -> task.getItemIndex() != null)
                .mapToInt(Task::getItemIndex)
                .max()
                .orElse(-1)
                + 1;
        List<Task> batch = new ArrayList<>();
        for (String item : usable) {
            Task task = new Task(project, project.getDataset(), TaskStatus.PENDING, LocalDateTime.now());
            task.setItemData(item);
            task.setItemIndex(nextIndex++);
            batch.add(task);
        }
        return tasks.saveAll(batch).stream().map(TaskResponse::from).toList();
    }

    /**
     * Lists the queue of a project owned by the caller, optionally filtered
     * by status (for example {@code PENDING} for the annotator's next items).
     */
    @Transactional(readOnly = true)
    public List<TaskResponse> list(Long projectId, TaskStatus status, String userEmail) {
        Project project = loadVisibleProject(projectId, userEmail);
        List<Task> found = status == null
                ? tasks.findByProjectIdOrderByItemIndexAscIdAsc(project.getId())
                : tasks.findByProjectIdAndStatusOrderByItemIndexAscIdAsc(project.getId(), status);
        return found.stream().map(TaskResponse::from).toList();
    }

    private User loadUser(String userEmail) {
        return users.findByEmail(userEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    }

    private Project loadOwnedProject(Long projectId, String userEmail) {
        User user = loadUser(userEmail);
        return projects.findByIdAndOwnerId(projectId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private Project loadVisibleProject(Long projectId, String userEmail) {
        User user = loadUser(userEmail);
        if (user.getRole() == Role.ADMIN) {
            return projects.findById(projectId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
        }
        return projects.findByIdAndOwnerId(projectId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
    }
}
