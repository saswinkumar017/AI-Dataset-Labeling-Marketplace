package com.labelmate.labelmate.controller;

import com.labelmate.labelmate.dto.TaskAssignRequest;
import com.labelmate.labelmate.dto.TaskResponse;
import com.labelmate.labelmate.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for single-task workflow actions (assignment, start,
 * submit) and the annotator's personal queue.
 *
 * <p>Kept separate from {@link TaskController} (which is scoped under
 * {@code /api/projects/{projectId}/tasks}) so these task-id routes stay
 * clean. Like the other controllers it only binds and validates the request
 * and forwards the authenticated username; every ownership and assignment
 * check lives in {@link TaskService}.
 */
@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Task workflow", description = "Assignment and annotator task actions")
public class TaskAssignmentController {

    private final TaskService taskService;

    public TaskAssignmentController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** Lists tasks assigned to the caller — the annotator's work queue. */
    @GetMapping("/assigned")
    @Operation(summary = "List my assigned tasks")
    public ResponseEntity<List<TaskResponse>> listAssigned(Authentication authentication) {
        return ResponseEntity.ok(taskService.listAssigned(authentication.getName()));
    }

    /** Returns one task visible to the caller (owner, assignee, or admin). */
    @GetMapping("/{taskId}")
    @Operation(summary = "Get an assigned task by id")
    public ResponseEntity<TaskResponse> getTask(
            @PathVariable Long taskId, Authentication authentication) {
        return ResponseEntity.ok(taskService.getTask(taskId, authentication.getName()));
    }

    /**
     * Assigns a task to one annotator by email. Only the project owner (or
     * an admin) may assign; the assignee gains read/annotate rights on that
     * task without gaining access to the rest of the project.
     */
    @PostMapping("/{taskId}/assign")
    @Operation(summary = "Assign a task to an annotator")
    public ResponseEntity<TaskResponse> assign(
            @PathVariable Long taskId,
            @Valid @RequestBody TaskAssignRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(taskService.assign(taskId, request, authentication.getName()));
    }

    /** Marks a task as actively being labeled (owner or assignee). */
    @PostMapping("/{taskId}/start")
    @Operation(summary = "Start a task")
    public ResponseEntity<TaskResponse> start(
            @PathVariable Long taskId, Authentication authentication) {
        return ResponseEntity.ok(taskService.start(taskId, authentication.getName()));
    }

    /** Submits a task for review (owner or assignee, needs annotations). */
    @PostMapping("/{taskId}/submit")
    @Operation(summary = "Submit a task for review")
    public ResponseEntity<TaskResponse> submit(
            @PathVariable Long taskId, Authentication authentication) {
        return ResponseEntity.ok(taskService.submit(taskId, authentication.getName()));
    }
}
