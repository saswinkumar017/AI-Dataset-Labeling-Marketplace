package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.ProjectRequest;
import com.labelmate.labelmate.dto.ProjectResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Annotation;
import com.labelmate.labelmate.model.Dataset;
import com.labelmate.labelmate.model.Label;
import com.labelmate.labelmate.model.Project;
import com.labelmate.labelmate.model.ProjectStatus;
import com.labelmate.labelmate.model.Task;
import com.labelmate.labelmate.model.TaskStatus;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.AiSuggestionRepository;
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.DatasetRepository;
import com.labelmate.labelmate.repository.LabelRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.ReviewRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application logic for annotation projects.
 *
 * <p>All authorization lives here rather than in the controller, so every
 * caller is covered: the owner identity always comes from the security
 * context (never from client input) and each lookup filters by owner id,
 * which keeps one user from reaching another user's project even by
 * guessing its id. Creation additionally requires the dataset to be owned
 * by the same user, keeping the Dataset → Project link referentially
 * honest.
 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final DatasetRepository datasets;
    private final LabelRepository labels;
    private final TaskRepository tasks;
    private final AnnotationRepository annotations;
    private final ReviewRepository reviews;
    private final AiSuggestionRepository suggestions;
    private final UserRepository users;
    private final TaskService taskService;

    public ProjectService(
            ProjectRepository projects,
            DatasetRepository datasets,
            LabelRepository labels,
            TaskRepository tasks,
            AnnotationRepository annotations,
            ReviewRepository reviews,
            AiSuggestionRepository suggestions,
            UserRepository users,
            TaskService taskService) {
        this.projects = projects;
        this.datasets = datasets;
        this.labels = labels;
        this.tasks = tasks;
        this.annotations = annotations;
        this.reviews = reviews;
        this.suggestions = suggestions;
        this.users = users;
        this.taskService = taskService;
    }

    /**
     * Creates a project on a dataset owned by the calling user.
     *
     * <p>The dataset link is fixed at creation because the database design
     * forbids changing it afterwards; this keeps project ownership and
     * dataset ownership consistent by construction. A finite, non-empty
     * label scheme is required: annotation without possible labels is not
     * a labeling project. One task per dataset item is generated
     * immediately, so the annotation queue exists from the start (empty
     * datasets simply yield an empty queue).
     */
    @Transactional
    public ProjectResponse create(ProjectRequest request, String ownerEmail) {
        User owner = loadOwner(ownerEmail);
        Dataset dataset = loadOwnedDataset(request.datasetId(), owner);
        if (usableLabels(request.labels()).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "at least one label is required");
        }
        Project project = new Project(
                dataset, owner, request.name().trim(), ProjectStatus.DRAFT, LocalDateTime.now());
        project.setInstructions(request.instructions());
        project.setLabelType(request.labelType());
        Project saved = projects.save(project);
        List<String> scheme = syncLabels(saved, request.labels());
        taskService.generateTasks(saved.getId(), ownerEmail);
        return ProjectResponse.from(saved, scheme, taskCounts(saved.getId()));
    }

    /**
     * Lists only the calling user's projects, newest first.
     *
     * <p>Scoping the query by owner id keeps other users' projects out of
     * the result without any in-memory filtering.
     */
    public List<ProjectResponse> listMine(String ownerEmail) {
        User owner = loadOwner(ownerEmail);
        return projects.findByOwnerIdOrderByCreatedAtDesc(owner.getId()).stream()
                .map(project -> ProjectResponse.from(
                        project, labelNames(project.getId()), taskCounts(project.getId())))
                .toList();
    }

    /**
     * Returns a single project only when it belongs to the calling user.
     * A foreign or missing id yields 404 so project ids cannot be probed.
     */
    public ProjectResponse getByIdForOwner(Long id, String ownerEmail) {
        Project project = loadOwned(id, ownerEmail);
        return ProjectResponse.from(project, labelNames(project.getId()), taskCounts(project.getId()));
    }

    /**
     * Returns a single project when the caller owns it, administers the
     * system, or is assigned to at least one of its tasks. Assignees need the
     * label scheme and instructions to do their work; everything else about
     * project management (update/delete) stays owner-only.
     */
    public ProjectResponse getVisible(Long id, String userEmail) {
        User user = loadOwner(userEmail);
        Project project = switch (user.getRole()) {
            case ADMIN -> projects.findById(id).orElse(null);
            default -> projects.findByIdAndOwnerId(id, user.getId()).orElse(null);
        };
        if (project == null) {
            project = tasks.findByAssignedToIdOrderByIdAsc(user.getId()).stream()
                    .map(Task::getProject)
                    .filter(candidate -> candidate != null && candidate.getId().equals(id))
                    .findFirst()
                    .map(candidate -> projects.findById(id).orElse(null))
                    .orElse(null);
        }
        if (project == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Project not found");
        }
        return ProjectResponse.from(project, labelNames(project.getId()), taskCounts(project.getId()));
    }

    /**
     * Updates name, instructions, label type, and label scheme only when the
     * project belongs to the calling user. A null scheme leaves the existing
     * labels untouched; provided labels only ever add options, never delete
     * ones annotations may reference. The dataset link and status stay
     * untouched: the dataset is immutable after creation and status
     * transitions belong to the future task workflow, not to a generic
     * update.
     */
    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request, String ownerEmail) {
        Project project = loadOwned(id, ownerEmail);
        project.setName(request.name().trim());
        project.setInstructions(request.instructions());
        project.setLabelType(request.labelType());
        project.setUpdatedAt(LocalDateTime.now());
        Project saved = projects.save(project);
        return ProjectResponse.from(saved, syncLabels(saved, request.labels()), taskCounts(saved.getId()));
    }

    /**
     * Deletes a project only when it belongs to the calling user.
     *
     * <p>Deletion cascades through everything the project produced — reviews,
     * annotations, AI suggestions, tasks, then label options — because worked
     * data has no independent lifecycle: tasks cannot be listed, annotated,
     * or exported without their project, and leaving orphaned rows would
     * corrupt counts and exports. The UI confirms the scope before calling.
     */
    @Transactional
    public void delete(Long id, String ownerEmail) {
        Project project = loadOwned(id, ownerEmail);
        List<Task> queue = tasks.findByProjectIdOrderByItemIndexAscIdAsc(project.getId());
        for (Task task : queue) {
            List<Annotation> taskAnnotations =
                    annotations.findByTaskIdOrderByCreatedAtDesc(task.getId());
            for (Annotation annotation : taskAnnotations) {
                reviews.deleteAll(reviews.findByAnnotationIdOrderByReviewedAtDesc(annotation.getId()));
            }
            annotations.deleteAll(taskAnnotations);
            suggestions.deleteAll(suggestions.findByTaskId(task.getId()));
        }
        tasks.deleteAll(queue);
        labels.deleteAll(labels.findByProjectIdOrderByNameAsc(project.getId()));
        projects.delete(project);
    }

    /**
     * Deletes every project on one dataset for the calling user, cascading
     * through each project's queue. Used by dataset deletion; ownership is
     * re-checked per project inside {@link #delete}, so the whole operation
     * rolls back rather than partially deleting on any mismatch.
     */
    @Transactional
    public void deleteProjectsOfDataset(Long datasetId, String ownerEmail) {
        for (Project project : projects.findByDatasetId(datasetId)) {
            delete(project.getId(), ownerEmail);
        }
    }

    /**
     * Workflow counts driving progress bars: total queue size plus the
     * interesting states. Single aggregate queries, no entity loading.
     */
    private ProjectResponse.TaskCounts taskCounts(Long projectId) {
        return new ProjectResponse.TaskCounts(
                tasks.countByProjectId(projectId),
                tasks.countByProjectIdAndStatus(projectId, TaskStatus.PENDING)
                        + tasks.countByProjectIdAndStatus(projectId, TaskStatus.ASSIGNED)
                        + tasks.countByProjectIdAndStatus(projectId, TaskStatus.IN_PROGRESS),
                tasks.countByProjectIdAndStatus(projectId, TaskStatus.SUBMITTED),
                tasks.countByProjectIdAndStatus(projectId, TaskStatus.APPROVED),
                tasks.countByProjectIdAndStatus(projectId, TaskStatus.REJECTED));
    }

    /**
     * Adds label options that do not exist yet and returns the full scheme.
     * Labels are never deleted here: annotations may already reference them.
     */
    private List<String> syncLabels(Project project, List<String> requested) {
        if (requested != null) {
            Set<String> seen = new HashSet<>();
            for (Label existing :
                    labels.findByProjectIdOrderByNameAsc(project.getId())) {
                seen.add(existing.getName().toLowerCase());
            }
            List<Label> fresh = new ArrayList<>();
            for (String name : usableLabels(requested)) {
                if (!seen.add(name.toLowerCase())) {
                    continue;
                }
                fresh.add(new Label(project, name, LocalDateTime.now()));
            }
            labels.saveAll(fresh);
        }
        return labelNames(project.getId());
    }

    private List<String> usableLabels(List<String> requested) {
        if (requested == null) {
            return List.of();
        }
        List<String> usable = new ArrayList<>();
        for (String raw : requested) {
            if (raw == null) {
                continue;
            }
            String name = raw.trim();
            if (!name.isEmpty() && !usable.contains(name)) {
                usable.add(name);
            }
        }
        return usable;
    }

    private List<String> labelNames(Long projectId) {
        return labels.findByProjectIdOrderByNameAsc(projectId).stream()
                .map(Label::getName)
                .toList();
    }

    private User loadOwner(String ownerEmail) {
        return users.findByEmail(ownerEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    }

    private Dataset loadOwnedDataset(Long datasetId, User owner) {
        return datasets.findByIdAndOwnerId(datasetId, owner.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Dataset not found"));
    }

    private Project loadOwned(Long id, String ownerEmail) {
        User owner = loadOwner(ownerEmail);
        return projects.findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
    }
}
