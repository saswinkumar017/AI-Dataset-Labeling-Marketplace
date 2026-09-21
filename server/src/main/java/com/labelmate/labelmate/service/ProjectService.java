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
import com.labelmate.labelmate.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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
    private final UserRepository users;

    public ProjectService(
            ProjectRepository projects,
            DatasetRepository datasets,
            LabelRepository labels,
            UserRepository users) {
        this.projects = projects;
        this.datasets = datasets;
        this.labels = labels;
        this.users = users;
    }

    /**
     * Creates a project on a dataset owned by the calling user.
     *
     * <p>The dataset link is fixed at creation because the database design
     * forbids changing it afterwards; this keeps project ownership and
     * dataset ownership consistent by construction.
     */
    public ProjectResponse create(ProjectRequest request, String ownerEmail) {
        User owner = loadOwner(ownerEmail);
        Dataset dataset = loadOwnedDataset(request.datasetId(), owner);
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
     * Updates name, instructions, and label type only when the project
     * belongs to the calling user. The dataset link and status stay
     * untouched: the dataset is immutable after creation and status
     * transitions belong to the future task workflow, not to a generic
     * update.
     */
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
     */
    public void delete(Long id, String ownerEmail) {
        projects.delete(loadOwned(id, ownerEmail));
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
            for (String raw : requested) {
                if (raw == null) {
                    continue;
                }
                String name = raw.trim();
                if (name.isEmpty() || !seen.add(name.toLowerCase())) {
                    continue;
                }
                fresh.add(new Label(project, name, LocalDateTime.now()));
            }
            labels.saveAll(fresh);
        }
        return labelNames(project.getId());
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
