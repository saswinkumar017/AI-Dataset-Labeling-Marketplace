package com.labelmate.labelmate.controller;

import com.labelmate.labelmate.dto.ReviewRequest;
import com.labelmate.labelmate.dto.ReviewResponse;
import com.labelmate.labelmate.service.ReviewService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for human review.
 *
 * <p>Like the other controllers it only binds and validates the request and
 * forwards the authenticated username; reviewer authorization and the
 * annotation state transitions live in {@link ReviewService}.
 */
@RestController
@Tag(name = "Reviews", description = "Human review of annotations")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** Records the caller's review decision for one annotation. */
    @PostMapping("/api/annotations/{annotationId}/reviews")
    @Operation(summary = "Review an annotation")
    public ResponseEntity<ReviewResponse> submit(
            @PathVariable Long annotationId,
            @Valid @RequestBody ReviewRequest request,
            Authentication authentication) {
        ReviewResponse review = reviewService.submit(annotationId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }

    /** Lists the reviews of one annotation visible to the caller. */
    @GetMapping("/api/annotations/{annotationId}/reviews")
    @Operation(summary = "List reviews of an annotation")
    public ResponseEntity<List<ReviewResponse>> listByAnnotation(
            @PathVariable Long annotationId, Authentication authentication) {
        return ResponseEntity.ok(reviewService.listByAnnotation(annotationId, authentication.getName()));
    }

    /** Lists every review in a project visible to the caller. */
    @GetMapping("/api/projects/{projectId}/reviews")
    @Operation(summary = "List reviews in a project")
    public ResponseEntity<List<ReviewResponse>> listByProject(
            @PathVariable Long projectId, Authentication authentication) {
        return ResponseEntity.ok(reviewService.listByProject(projectId, authentication.getName()));
    }
}
