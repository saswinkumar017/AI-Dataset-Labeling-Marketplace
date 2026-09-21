package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.ProjectRequest;
import com.labelmate.labelmate.dto.ProjectResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Dataset;
import com.labelmate.labelmate.model.Label;
import com.labelmate.labelmate.model.Project;
import com.labelmate.labelmate.model.ProjectStatus;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.DatasetRepository;
import com.labelmate.labelmate.repository.LabelRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
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
    private final UserRepository users;

    public ProjectService(
            ProjectRepository projects,
            DatasetRepository datasets,
            LabelRepository labels,
            TaskRepository tasks,
            UserRepository users) {
        this.projects = projects;
        this.datasets = datasets;
        this.labels = labels;
        this.tasks = tasks;
        this.users = users;
    }

    /**
     * Creates a project on a dataset owned by the calling user.
     *
     * <p>The dataset link is fixed at creation because the database design
     * forbids changing it afterwards; this keeps project ownership and
     * dataset ownership consistent by construction. A finite, non-empty
     * label scheme is required: annotation without possible labels is not
     * a labeling project.
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
        return ProjectResponse.from(saved, syncLabels(saved, request.labels()));
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
                .map(project -> ProjectResponse.from(project, labelNames(project.getId())))
                .toList();
    }

    /**
     * Returns a single project only when it belongs to the calling user.
     * A foreign or missing id yields 404 so project ids cannot be probed.
     */
    public ProjectResponse getByIdForOwner(Long id, String ownerEmail) {
        Project project = loadOwned(id, ownerEmail);
        return ProjectResponse.from(project, labelNames(project.getId()));
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
        return ProjectResponse.from(saved, syncLabels(saved, request.labels()));
    }

    /**
     * Deletes a project only when it belongs to the calling user.
     *
     * <p>Label options are owned configuration and go down with the project.
     * A project with annotation tasks is refused with 409 instead: tasks
     * (and their annotations) are worked data, never silently cascadeable.
     */
    @Transactional
    public void delete(Long id, String ownerEmail) {
        Project project = loadOwned(id, ownerEmail);
        if (!tasks.findByProjectIdOrderByItemIndexAscIdAsc(project.getId()).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "project has annotation tasks and cannot be deleted");
        }
        labels.deleteAll(labels.findByProjectIdOrderByNameAsc(project.getId()));
        projects.delete(project);
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
