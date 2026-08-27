package com.labelmate.labelmate.dto;

import com.labelmate.labelmate.model.Review;
import com.labelmate.labelmate.model.ReviewDecision;
import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long annotationId,
        Long taskId,
        Long projectId,
        String reviewer,
        ReviewDecision decision,
        String comment,
        LocalDateTime reviewedAt) {

    public static ReviewResponse from(Review review) {
        Long taskId = review.getAnnotation() != null && review.getAnnotation().getTask() != null
                ? review.getAnnotation().getTask().getId()
                : null;
        Long projectId = review.getAnnotation() != null
                && review.getAnnotation().getTask() != null
                && review.getAnnotation().getTask().getProject() != null
                ? review.getAnnotation().getTask().getProject().getId()
                : null;
        return new ReviewResponse(
                review.getId(),
                review.getAnnotation() != null ? review.getAnnotation().getId() : null,
                taskId,
                projectId,
                review.getReviewer() != null ? review.getReviewer().getUsername() : null,
                review.getDecision(),
                review.getComment(),
                review.getReviewedAt());
    }
}
