package com.labelmate.labelmate.controller;

import com.labelmate.labelmate.dto.ProjectRequest;
import com.labelmate.labelmate.dto.ProjectResponse;
import com.labelmate.labelmate.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for annotation projects.
 *
 * <p>Keeps no authorization logic of its own: it only binds and validates
 * the request body and forwards the authenticated username from the
 * security context, leaving every ownership check to {@link ProjectService}.
 */
@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Annotation project management")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /** Creates a project for the authenticated user. */
    @PostMapping
    @Operation(summary = "Create a project")
    public ResponseEntity<ProjectResponse> create(
            @Valid @RequestBody ProjectRequest request, Authentication authentication) {
        ProjectResponse project = projectService.create(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    /** Lists the authenticated user's projects. */
    @GetMapping
    @Operation(summary = "List my projects")
    public ResponseEntity<List<ProjectResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(projectService.listMine(authentication.getName()));
    }

    /** Returns one of the authenticated user's projects. */
    @GetMapping("/{id}")
    @Operation(summary = "Get a project by id")
    public ResponseEntity<ProjectResponse> get(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(projectService.getByIdForOwner(id, authentication.getName()));
    }

    /** Updates one of the authenticated user's projects. */
    @PutMapping("/{id}")
    @Operation(summary = "Update a project")
    public ResponseEntity<ProjectResponse> update(
            @PathVariable Long id, @Valid @RequestBody ProjectRequest request, Authentication authentication) {
        return ResponseEntity.ok(projectService.update(id, request, authentication.getName()));
    }

    /** Deletes one of the authenticated user's projects. */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a project")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        projectService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
