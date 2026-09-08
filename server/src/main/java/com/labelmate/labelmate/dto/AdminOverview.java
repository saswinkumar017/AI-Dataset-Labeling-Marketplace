package com.labelmate.labelmate.dto;

public record AdminOverview(
        long userCount,
        long datasetCount,
        long projectCount,
        long taskCount,
        long annotationCount,
        long reviewCount,
        long reviewsApproved,
        long reviewsRejected,
        long suggestionCount) {
}
