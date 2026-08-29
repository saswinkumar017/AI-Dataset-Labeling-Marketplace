package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.AnnotationRequest;
import com.labelmate.labelmate.dto.AnnotationResponse;
import com.labelmate.labelmate.dto.AnnotationUpdateRequest;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Annotation;
import com.labelmate.labelmate.model.AnnotationSource;
import com.labelmate.labelmate.model.Label;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.Task;
import com.labelmate.labelmate.model.TaskStatus;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.LabelRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.ReviewRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application logic for the Annotation domain.
 *
 * <p>Every operation is scoped by project ownership: the task's project must
 * belong to the calling user, so one user cannot annotate another user's
 * project even by guessing a task id. The human-entered label string is
 * stored in {@code content}; when a {@link Label} with the same name already
 * exists in the project it is linked as well.
 *
 * <p>State transitions stay consistent with review: approved tasks are
 * immutable, and reviewed annotations cannot be deleted because the review
 * row references them.
 */
@Service
public class AnnotationService {

    private final AnnotationRepository annotations;
    private final TaskRepository tasks;
    private final LabelRepository labels;
    private final ProjectRepository projects;
    private final ReviewRepository reviews;
    private final UserRepository users;

    public AnnotationService(
            AnnotationRepository annotations,
            TaskRepository tasks,
            LabelRepository labels,
            ProjectRepository projects,
            ReviewRepository reviews,
            UserRepository users) {
        this.annotations = annotations;
        this.tasks = tasks;
        this.labels = labels;
        this.projects = projects;
        this.reviews = reviews;
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
        if (task.getStatus() == TaskStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "Task is already approved");
        }
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
     * Returns a single annotation when its project belongs to the calling
     * user. Admins may read any project so they can review it; annotation
     * writes stay owner-only.
     */
    @Transactional(readOnly = true)
    public AnnotationResponse getByIdForUser(Long id, String userEmail) {
        return AnnotationResponse.from(loadReadable(id, loadUser(userEmail)));
    }

    /**
     * Lists annotations for one task, newest first, when the task's project
     * belongs to the calling user.
     */
    @Transactional(readOnly = true)
    public List<AnnotationResponse> listByTask(Long taskId, String userEmail) {
        User user = loadUser(userEmail);
        Task task = loadReadableTask(taskId, user);
        return annotations.findByTaskIdOrderByCreatedAtDesc(task.getId()).stream()
                .map(AnnotationResponse::from)
                .toList();
    }

    /**
     * Lists every annotation in a project owned by the calling user, newest
     * first. A foreign or missing project id yields 404 so project ids
     * cannot be probed.
     */
    @Transactional(readOnly = true)
    public List<AnnotationResponse> listByProject(Long projectId, String userEmail) {
        User user = loadUser(userEmail);
        if (!canReadProject(projectId, user)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Project not found");
        }
        return annotations.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(AnnotationResponse::from)
                .toList();
    }

    /**
     * Updates the label of an annotation whose project belongs to the calling
     * user. Approved tasks are immutable: relabeling an approved annotation
     * would silently invalidate the human review, so it yields 409.
     */
    @Transactional
    public AnnotationResponse update(Long id, AnnotationUpdateRequest request, String userEmail) {
        Annotation annotation = loadOwned(id, userEmail);
        if (annotation.getTask().getStatus() == TaskStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "Annotation is already approved");
        }
        String labelValue = request.label().trim();
        annotation.setContent(labelValue);
        annotation.setLabel(null);
        labels.findByProjectIdAndName(annotation.getTask().getProject().getId(), labelValue)
                .ifPresent(annotation::setLabel);
        annotation.setUpdatedAt(LocalDateTime.now());
        return AnnotationResponse.from(annotations.save(annotation));
    }

    /**
     * Deletes an annotation whose project belongs to the calling user.
     * Reviewed annotations are kept: the review row references them, so
     * deleting one yields 409 instead of breaking referential integrity.
     * When the task has no annotations left it returns to
     * {@code IN_PROGRESS} so the workspace shows it as work to do rather
     * than as submitted.
     */
    @Transactional
    public void delete(Long id, String userEmail) {
        Annotation annotation = loadOwned(id, userEmail);
        if (reviews.existsByAnnotationId(annotation.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Annotation has already been reviewed");
        }
        Task task = annotation.getTask();
        annotations.delete(annotation);
        if (task != null && annotations.findByTaskIdOrderByCreatedAtDesc(task.getId()).isEmpty()) {
            task.setStatus(TaskStatus.IN_PROGRESS);
            task.setUpdatedAt(LocalDateTime.now());
            tasks.save(task);
        }
    }

    private boolean canReadProject(Long projectId, User user) {
        if (user.getRole() == Role.ADMIN) {
            return projects.findById(projectId).isPresent();
        }
        return ownsProject(projectId, user);
    }

    private boolean ownsProject(Long projectId, User user) {
        return projects.findByIdAndOwnerId(projectId, user.getId()).isPresent();
    }

    private boolean isVisible(Task task, User user) {
        return task.getProject() != null
                && task.getProject().getOwner() != null
                && (Objects.equals(task.getProject().getOwner().getId(), user.getId())
                        || user.getRole() == Role.ADMIN);
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

    private Annotation loadReadable(Long id, User user) {
        Annotation annotation = annotations.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Annotation not found"));
        if (annotation.getTask() == null || !isVisible(annotation.getTask(), user)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Annotation not found");
        }
        return annotation;
    }

    private Task loadReadableTask(Long taskId, User user) {
        Task task = tasks.findById(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Task not found"));
        if (!isVisible(task, user)) {
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
