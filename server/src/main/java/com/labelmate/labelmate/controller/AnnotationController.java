package com.labelmate.labelmate.controller;

import com.labelmate.labelmate.dto.AnnotationRequest;
import com.labelmate.labelmate.dto.AnnotationResponse;
import com.labelmate.labelmate.service.AnnotationService;
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
 * HTTP boundary for annotations.
 *
 * <p>Keeps no authorization logic: it binds and validates the request and
 * forwards the authenticated username, leaving every ownership check to
 * {@link AnnotationService}.
 */
@RestController
@RequestMapping("/api/annotations")
@Tag(name = "Annotations", description = "Annotation management")
public class AnnotationController {

    private final AnnotationService annotationService;

    public AnnotationController(AnnotationService annotationService) {
        this.annotationService = annotationService;
    }

    /** Creates a human annotation for a task owned by the caller. */
    @PostMapping
    @Operation(summary = "Create an annotation")
    public ResponseEntity<AnnotationResponse> create(
            @Valid @RequestBody AnnotationRequest request, Authentication authentication) {
        AnnotationResponse annotation = annotationService.create(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(annotation);
    }

    /** Returns one annotation when its project belongs to the caller. */
    @GetMapping("/{id}")
    @Operation(summary = "Get an annotation by id")
    public ResponseEntity<AnnotationResponse> get(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(annotationService.getByIdForUser(id, authentication.getName()));
    }

    /** Lists annotations for one task owned by the caller. */
    @GetMapping
    @Operation(summary = "List annotations for a task")
    public ResponseEntity<List<AnnotationResponse>> listByTask(
            @RequestParam Long taskId, Authentication authentication) {
        return ResponseEntity.ok(annotationService.listByTask(taskId, authentication.getName()));
    }
}
