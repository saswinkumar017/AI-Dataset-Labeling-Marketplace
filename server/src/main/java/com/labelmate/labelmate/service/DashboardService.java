package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.DashboardSummary;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Annotation;
import com.labelmate.labelmate.model.Project;
import com.labelmate.labelmate.model.Review;
import com.labelmate.labelmate.model.ReviewDecision;
import com.labelmate.labelmate.model.Task;
import com.labelmate.labelmate.model.TaskStatus;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.DatasetRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.ReviewRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real workflow numbers for the dashboard. Every count is derived from the
 * calling user's own projects through the existing repositories, so the
 * dashboard can never show another user's work and no statistic is
 * fabricated. Only counts are returned, never entity graphs.
 */
@Service
public class DashboardService {

    private final DatasetRepository datasets;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final AnnotationRepository annotations;
    private final ReviewRepository reviews;
    private final UserRepository users;

    public DashboardService(
            DatasetRepository datasets,
            ProjectRepository projects,
            TaskRepository tasks,
            AnnotationRepository annotations,
            ReviewRepository reviews,
            UserRepository users) {
        this.datasets = datasets;
        this.projects = projects;
        this.tasks = tasks;
        this.annotations = annotations;
        this.reviews = reviews;
        this.users = users;
    }

    /**
     * Summarizes dataset, project, task, annotation, and review state for the
     * calling user.
     */
    @Transactional(readOnly = true)
    public DashboardSummary summarize(String userEmail) {
        User user = users.findByEmail(userEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

        long datasetCount = datasets.findByOwnerIdOrderByCreatedAtDesc(user.getId()).size();
        List<Project> owned = projects.findByOwnerIdOrderByCreatedAtDesc(user.getId());

        long taskCount = 0;
        long tasksPending = 0;
        long tasksSubmitted = 0;
        long tasksApproved = 0;
        long tasksRejected = 0;
        long annotationCount = 0;
        long reviewsApproved = 0;
        long reviewsRejected = 0;
        Set<Long> annotatedIds = new HashSet<>();
        Set<Long> reviewedIds = new HashSet<>();

        for (Project project : owned) {
            for (Task task : tasks.findByProjectIdOrderByItemIndexAscIdAsc(project.getId())) {
                taskCount++;
                if (task.getStatus() == TaskStatus.APPROVED) {
                    tasksApproved++;
                } else if (task.getStatus() == TaskStatus.SUBMITTED) {
                    tasksSubmitted++;
                } else if (task.getStatus() == TaskStatus.REJECTED) {
                    tasksRejected++;
                } else {
                    tasksPending++;
                }
            }
            for (Annotation annotation : annotations.findByProjectIdOrderByCreatedAtDesc(project.getId())) {
                annotationCount++;
                if (annotation.getId() != null) {
                    annotatedIds.add(annotation.getId());
                }
            }
            for (Review review : reviews.findByProjectIdOrderByReviewedAtDesc(project.getId())) {
                if (review.getDecision() == ReviewDecision.APPROVED) {
                    reviewsApproved++;
                } else {
                    reviewsRejected++;
                }
                if (review.getAnnotation() != null && review.getAnnotation().getId() != null) {
                    reviewedIds.add(review.getAnnotation().getId());
                }
            }
        }

        annotatedIds.removeAll(reviewedIds);
        return new DashboardSummary(
                datasetCount,
                owned.size(),
                taskCount,
                tasksPending,
                tasksSubmitted,
                tasksApproved,
                tasksRejected,
                annotationCount,
                reviewsApproved,
                reviewsRejected,
                annotatedIds.size());
    }
}
