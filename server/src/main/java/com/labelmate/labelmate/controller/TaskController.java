package com.labelmate.labelmate.controller;

import com.labelmate.labelmate.dto.TaskBulkRequest;
import com.labelmate.labelmate.dto.TaskRequest;
import com.labelmate.labelmate.dto.TaskResponse;
import com.labelmate.labelmate.model.TaskStatus;
import com.labelmate.labelmate.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for the annotation task queue.
 *
 * <p>Like the other controllers it only binds and validates the request and
 * forwards the authenticated username; every ownership check lives in
 * {@link TaskService}.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@Tag(name = "Tasks", description = "Annotation task queue")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** Adds one item to the queue of a project owned by the caller. */
    @PostMapping
    @Operation(summary = "Create a task in a project")
    public ResponseEntity<TaskResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody(required = false) TaskRequest request,
            Authentication authentication) {
        TaskRequest effective = request == null ? new TaskRequest(null, null) : request;
        TaskResponse task = taskService.create(projectId, effective, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    /** Adds many items to the queue at once (dataset/CSV import path). */
    @PostMapping("/bulk")
    @Operation(summary = "Create many tasks in a project")
    public ResponseEntity<List<TaskResponse>> createBulk(
            @PathVariable Long projectId,
            @Valid @RequestBody TaskBulkRequest request,
            Authentication authentication) {
        List<TaskResponse> tasks = taskService.createBulk(projectId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(tasks);
    }

    /**
     * Generates one task per dataset item for a project owned by the caller.
     * Idempotent: items that already have a task are skipped, so it is safe
     * to re-run after ingesting more data.
     */
    @PostMapping("/generate")
    @Operation(summary = "Generate tasks from the project's dataset items")
    public ResponseEntity<List<TaskResponse>> generate(
            @PathVariable Long projectId, Authentication authentication) {
        List<TaskResponse> tasks = taskService.generateTasks(projectId, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(tasks);
    }

    /** Lists the queue of a project owned by the caller. */
    @GetMapping
    @Operation(summary = "List tasks in a project")
    public ResponseEntity<List<TaskResponse>> list(
            @PathVariable Long projectId,
            @RequestParam(required = false) TaskStatus status,
            Authentication authentication) {
        return ResponseEntity.ok(taskService.list(projectId, status, authentication.getName()));
    }
}
