package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.AnnotationRequest;
import com.labelmate.labelmate.dto.AnnotationResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Annotation;
import com.labelmate.labelmate.model.AnnotationSource;
import com.labelmate.labelmate.model.Label;
import com.labelmate.labelmate.model.Task;
import com.labelmate.labelmate.model.TaskStatus;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.LabelRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Foundation for the Annotation domain.
 *
 * <p>Every operation is scoped by project ownership: the task's project must
 * belong to the calling user, so one user cannot annotate another user's
 * project even by guessing a task id. The human-entered label string is
 * stored in {@code content}; when a {@link Label} with the same name already
 * exists in the project it is linked as well. Full update/delete and richer
 * workflow rules belong to later commits.
 */
@Service
public class AnnotationService {

    private final AnnotationRepository annotations;
    private final TaskRepository tasks;
    private final LabelRepository labels;
    private final UserRepository users;

    public AnnotationService(
            AnnotationRepository annotations,
            TaskRepository tasks,
            LabelRepository labels,
            UserRepository users) {
        this.annotations = annotations;
        this.tasks = tasks;
        this.labels = labels;
        this.users = users;
    }

    /**
     * Creates a human annotation for a task owned by the calling user.
     * Moves the task to {@code SUBMITTED} so the review queue can pick it up.
     */
    @Transactional
    public AnnotationResponse create(AnnotationRequest request, String userEmail) {
        User user = loadUser(userEmail);
        Task task = loadOwnedTask(request.taskId(), user);
        String labelValue = request.label().trim();

        Annotation annotation =
                new Annotation(task, user, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent(labelValue);
        labels.findByProjectIdAndName(task.getProject().getId(), labelValue)
                .ifPresent(annotation::setLabel);

        Annotation saved = annotations.save(annotation);
        moveTaskToSubmitted(task);
        return AnnotationResponse.from(saved);
    }

    /**
     * Returns a single annotation only when its project belongs to the user.
     */
    @Transactional(readOnly = true)
    public AnnotationResponse getByIdForUser(Long id, String userEmail) {
        return AnnotationResponse.from(loadOwned(id, userEmail));
    }

    /**
     * Lists annotations for one task, newest first, when the task's project
     * belongs to the calling user.
     */
    @Transactional(readOnly = true)
    public List<AnnotationResponse> listByTask(Long taskId, String userEmail) {
        User user = loadUser(userEmail);
        Task task = loadOwnedTask(taskId, user);
        return annotations.findByTaskIdOrderByCreatedAtDesc(task.getId()).stream()
                .map(AnnotationResponse::from)
                .toList();
    }

    private void moveTaskToSubmitted(Task task) {
        if (task.getStatus() == TaskStatus.PENDING
                || task.getStatus() == TaskStatus.ASSIGNED
                || task.getStatus() == TaskStatus.IN_PROGRESS
                || task.getStatus() == TaskStatus.REJECTED) {
            task.setStatus(TaskStatus.SUBMITTED);
            task.setUpdatedAt(LocalDateTime.now());
            tasks.save(task);
        }
    }

    private User loadUser(String userEmail) {
        return users.findByEmail(userEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    }

    private Task loadOwnedTask(Long taskId, User user) {
        Task task = tasks.findById(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Task not found"));
        if (task.getProject() == null
                || task.getProject().getOwner() == null
                || !Objects.equals(task.getProject().getOwner().getId(), user.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return task;
    }

    private Annotation loadOwned(Long id, User user) {
        Annotation annotation = annotations.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Annotation not found"));
        Task task = annotation.getTask();
        if (task == null
                || task.getProject() == null
                || task.getProject().getOwner() == null
                || !Objects.equals(task.getProject().getOwner().getId(), user.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Annotation not found");
        }
        return annotation;
    }

    private Annotation loadOwned(Long id, String userEmail) {
        return loadOwned(id, loadUser(userEmail));
    }
}
