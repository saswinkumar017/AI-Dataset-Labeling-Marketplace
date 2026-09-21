package com.labelmate.labelmate.dto;

public record DashboardSummary(
        long datasetCount,
        long projectCount,
        long taskCount,
        long tasksPending,
        long tasksSubmitted,
        long tasksApproved,
        long tasksRejected,
        long annotationCount,
        long reviewsApproved,
        long reviewsRejected,
        long pendingReviews,
        long assignedToMe,
        long assignedNeedsAction) {
}
