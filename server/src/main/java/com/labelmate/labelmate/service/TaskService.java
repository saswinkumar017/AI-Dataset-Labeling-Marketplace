package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.TaskAssignRequest;
import com.labelmate.labelmate.dto.TaskBulkRequest;
import com.labelmate.labelmate.dto.TaskRequest;
import com.labelmate.labelmate.dto.TaskResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.DatasetItem;
import com.labelmate.labelmate.model.Project;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.Task;
import com.labelmate.labelmate.model.TaskStatus;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.DatasetItemRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minimal task queue supporting the annotation workflow.
 *
 * <p>A task is one unit of annotation work inside a project: it carries the
 * item to label ({@code itemData}) and its workflow status. Creation and
 * listing are scoped by project ownership, so one user can never see or feed
 * another user's queue — except through explicit assignment: the project
 * owner may assign a task to one annotator, who then sees it under
 * {@code /api/tasks/assigned} and may annotate, start, and submit it.
 * Status transitions themselves live here plus {@link AnnotationService}
 * (submit-on-annotate) and the review service (approve/reject).
 */
@Service
public class TaskService {

    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final AnnotationRepository annotations;
    private final DatasetItemRepository datasetItems;
    private final DatasetItemService datasetItemService;

    public TaskService(
            TaskRepository tasks,
            ProjectRepository projects,
            UserRepository users,
            AnnotationRepository annotations,
            DatasetItemRepository datasetItems,
            DatasetItemService datasetItemService) {
        this.tasks = tasks;
        this.projects = projects;
        this.users = users;
        this.annotations = annotations;
        this.datasetItems = datasetItems;
        this.datasetItemService = datasetItemService;
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
     * Generates one task per dataset item for a project owned by the caller.
     * Items page through 2000 at a time so large datasets never load fully
     * into memory; items that already have a task are skipped via a single
     * id query up front (never one EXISTS per item — that stalls real-size
     * datasets past client timeouts), so generation is idempotent and safe
     * to re-run after new ingest. New tasks append after the highest
     * existing queue index. Each task links its dataset item and snapshots
     * the rendered text into {@code itemData} so older consumers (suggest,
     * export, workspace) keep working unchanged.
     */
    @Transactional
    public List<TaskResponse> generateTasks(Long projectId, String userEmail) {
        Project project = loadOwnedProject(projectId, userEmail);
        int nextIndex = tasks.findByProjectIdOrderByItemIndexAscIdAsc(project.getId()).stream()
                .filter(task -> task.getItemIndex() != null)
                .mapToInt(Task::getItemIndex)
                .max()
                .orElse(-1)
                + 1;
        Set<Long> alreadyQueued = new HashSet<>(tasks.findDatasetItemIdsByProjectId(project.getId()));
        List<TaskResponse> created = new ArrayList<>();
        int page = 0;
        while (true) {
            Page<DatasetItem> slice = datasetItems.findByDatasetId(
                    project.getDataset().getId(), PageRequest.of(page, 2000));
            if (slice.isEmpty()) {
                break;
            }
            List<Task> batch = new ArrayList<>();
            for (DatasetItem item : slice.getContent()) {
                if (alreadyQueued.contains(item.getId())) {
                    continue;
                }
                Task task = new Task(project, project.getDataset(), TaskStatus.PENDING, LocalDateTime.now());
                task.setDatasetItem(item);
                task.setItemData(snapshotItemText(item));
                task.setItemIndex(nextIndex++);
                batch.add(task);
            }
            if (!batch.isEmpty()) {
                created.addAll(tasks.saveAll(batch).stream().map(TaskResponse::from).toList());
            }
            if (!slice.hasNext()) {
                break;
            }
            page++;
        }
        return created;
    }

    private String snapshotItemText(DatasetItem item) {
        String rendered = datasetItemService.renderItemForLabeling(item);
        if (rendered.length() > 5000) {
            return rendered.substring(0, 5000);
        }
        return rendered;
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

    /**
     * Returns one task when the caller owns its project, is assigned to it,
     * or is an admin. Anything else yields 404 so task ids cannot be probed.
     */
    @Transactional(readOnly = true)
    public TaskResponse getTask(Long taskId, String userEmail) {
        return TaskResponse.from(loadVisibleTask(taskId, loadUser(userEmail)));
    }

    /**
     * Lists every task currently assigned to the caller, newest first by id.
     * This is the annotator's dashboard feed: it needs no project ownership.
     */
    @Transactional(readOnly = true)
    public List<TaskResponse> listAssigned(String userEmail) {
        User user = loadUser(userEmail);
        return tasks.findByAssignedToIdOrderByIdAsc(user.getId()).stream()
                .map(TaskResponse::from)
                .toList();
    }

    /**
     * Assigns one task to an annotator. Only the project owner (or an admin)
     * may assign; the assignee is looked up by email so the owner never
     * handles raw user ids. Assigning moves a {@code PENDING} task to
     * {@code ASSIGNED}; any other state keeps its status so review decisions
     * are never silently overwritten. Re-assigning to someone else simply
     * moves the pointer.
     */
    @Transactional
    public TaskResponse assign(Long taskId, TaskAssignRequest request, String userEmail) {
        User caller = loadUser(userEmail);
        Task task = tasks.findById(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Task not found"));
        if (!canManage(task, caller)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Task not found");
        }
        String email = request.assigneeEmail().trim().toLowerCase();
        User assignee = users.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Assignee not found"));
        task.setAssignedTo(assignee);
        if (task.getStatus() == TaskStatus.PENDING) {
            task.setStatus(TaskStatus.ASSIGNED);
        }
        task.setUpdatedAt(LocalDateTime.now());
        return TaskResponse.from(tasks.save(task));
    }

    /**
     * Marks a task as actively being labeled. The caller must be the project
     * owner or the assignee. Allowed from {@code PENDING}, {@code ASSIGNED},
     * and {@code REJECTED} (rework after review); {@code SUBMITTED} and
     * {@code APPROVED} are already out of the annotator's hands and yield
     * 409.
     */
    @Transactional
    public TaskResponse start(Long taskId, String userEmail) {
        User caller = loadUser(userEmail);
        Task task = loadWritableTask(taskId, caller);
        switch (task.getStatus()) {
            case PENDING, ASSIGNED, REJECTED -> {
                task.setStatus(TaskStatus.IN_PROGRESS);
                task.setUpdatedAt(LocalDateTime.now());
            }
            case IN_PROGRESS -> {
                // Idempotent: starting twice is not an error.
            }
            default -> throw new ApiException(
                    HttpStatus.CONFLICT, "Task cannot be started from status " + task.getStatus());
        }
        return TaskResponse.from(tasks.save(task));
    }

    /**
     * Submits a task for review. The caller must be the project owner or the
     * assignee, the task must carry at least one annotation (there is nothing
     * to review otherwise), and approved tasks are immutable (409).
     * Submitting an already-submitted task is idempotent.
     */
    @Transactional
    public TaskResponse submit(Long taskId, String userEmail) {
        User caller = loadUser(userEmail);
        Task task = loadWritableTask(taskId, caller);
        if (task.getStatus() == TaskStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "Task is already approved");
        }
        if (task.getStatus() != TaskStatus.SUBMITTED
                && annotations.findByTaskIdOrderByCreatedAtDesc(task.getId()).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Task has no annotations to submit");
        }
        task.setStatus(TaskStatus.SUBMITTED);
        task.setUpdatedAt(LocalDateTime.now());
        return TaskResponse.from(tasks.save(task));
    }

    private boolean isOwner(Task task, User user) {
        return task.getProject() != null
                && task.getProject().getOwner() != null
                && Objects.equals(task.getProject().getOwner().getId(), user.getId());
    }

    private boolean isAssignee(Task task, User user) {
        return task.getAssignedTo() != null
                && Objects.equals(task.getAssignedTo().getId(), user.getId());
    }

    private boolean canManage(Task task, User caller) {
        return isOwner(task, caller) || caller.getRole() == Role.ADMIN;
    }

    private Task loadVisibleTask(Long taskId, User caller) {
        Task task = tasks.findById(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Task not found"));
        if (isOwner(task, caller) || isAssignee(task, caller) || caller.getRole() == Role.ADMIN) {
            return task;
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "Task not found");
    }

    private Task loadWritableTask(Long taskId, User caller) {
        Task task = tasks.findById(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Task not found"));
        if (isOwner(task, caller) || isAssignee(task, caller)) {
            return task;
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "Task not found");
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
