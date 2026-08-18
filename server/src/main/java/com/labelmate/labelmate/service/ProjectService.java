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

    public ProjectResponse create(ProjectRequest request, String ownerEmail) {
        User owner = loadOwner(ownerEmail);
        Dataset dataset = loadOwnedDataset(request.datasetId(), owner);
        Project project = new Project(
                dataset, owner, request.name().trim(), ProjectStatus.DRAFT, LocalDateTime.now());
        project.setInstructions(request.instructions());
        project.setLabelType(request.labelType());
        return ProjectResponse.from(projects.save(project));
    }

    public List<ProjectResponse> listMine(String ownerEmail) {
        User owner = loadOwner(ownerEmail);
        return projects.findByOwnerIdOrderByCreatedAtDesc(owner.getId()).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    public ProjectResponse getByIdForOwner(Long id, String ownerEmail) {
        return ProjectResponse.from(loadOwned(id, ownerEmail));
    }

    public ProjectResponse update(Long id, ProjectRequest request, String ownerEmail) {
        Project project = loadOwned(id, ownerEmail);
        project.setName(request.name().trim());
        project.setInstructions(request.instructions());
        project.setLabelType(request.labelType());
        project.setUpdatedAt(LocalDateTime.now());
        return ProjectResponse.from(projects.save(project));
    }

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
