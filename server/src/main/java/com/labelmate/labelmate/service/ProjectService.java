package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.ProjectRequest;
import com.labelmate.labelmate.dto.ProjectResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Dataset;
import com.labelmate.labelmate.model.Project;
import com.labelmate.labelmate.model.ProjectStatus;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.DatasetRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
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
    private final UserRepository users;

    public ProjectService(
            ProjectRepository projects, DatasetRepository datasets, UserRepository users) {
        this.projects = projects;
        this.datasets = datasets;
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
        return ProjectResponse.from(projects.save(project));
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
                .map(ProjectResponse::from)
                .toList();
    }

    /**
     * Returns a single project only when it belongs to the calling user.
     * A foreign or missing id yields 404 so project ids cannot be probed.
     */
    public ProjectResponse getByIdForOwner(Long id, String ownerEmail) {
        return ProjectResponse.from(loadOwned(id, ownerEmail));
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
        return ProjectResponse.from(projects.save(project));
    }

    /**
     * Deletes a project only when it belongs to the calling user.
     */
    public void delete(Long id, String ownerEmail) {
        projects.delete(loadOwned(id, ownerEmail));
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
