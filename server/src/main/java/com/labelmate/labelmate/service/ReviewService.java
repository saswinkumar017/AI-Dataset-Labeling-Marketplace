package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.ReviewRequest;
import com.labelmate.labelmate.dto.ReviewResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Annotation;
import com.labelmate.labelmate.model.AnnotationSource;
import com.labelmate.labelmate.model.Review;
import com.labelmate.labelmate.model.ReviewDecision;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.Task;
import com.labelmate.labelmate.model.TaskStatus;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.ReviewRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Human review over annotations. Review is the final quality authority: an
 * {@code APPROVED} decision is the only path that turns an annotation into a
 * {@code HUMAN_APPROVED} final label, and a {@code REJECTED} decision sends
 * the task back for re-annotation. AI is not involved at any point.
 *
 * <p>Visibility and decision rights are separate gates, both enforced
 * server-side: reading requires the caller to be the project owner, the
 * assigned annotator, or an {@code ADMIN} (anything else yields 404), while
 * recording a decision additionally requires the caller to be the project
 * owner or an {@code ADMIN} and to differ from the annotation's
 * original annotator (self-review yields 403).
 */
@Service
public class ReviewService {

    private final ReviewRepository reviews;
    private final AnnotationRepository annotations;
    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final UserRepository users;

    public ReviewService(
            ReviewRepository reviews,
            AnnotationRepository annotations,
            TaskRepository tasks,
            ProjectRepository projects,
            UserRepository users) {
        this.reviews = reviews;
        this.annotations = annotations;
        this.tasks = tasks;
        this.projects = projects;
        this.users = users;
    }

    /**
     * Records the caller's review decision for one annotation.
     * Each annotation accepts a single current review; a second decision for
     * the same annotation yields 409. Only the project owner or an admin may
     * decide, and never on their own annotation (403).
     */
    @Transactional
    public ReviewResponse submit(Long annotationId, ReviewRequest request, String reviewerEmail) {
        User reviewer = loadUser(reviewerEmail);
        Annotation annotation = loadVisibleAnnotation(annotationId, reviewer);
        if (!canDecide(annotation, reviewer)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Annotation not found");
        }
        if (annotation.getAnnotator() != null
                && Objects.equals(annotation.getAnnotator().getId(), reviewer.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Reviewer must differ from the annotator");
        }
        if (reviews.existsByAnnotationId(annotation.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Annotation has already been reviewed");
        }
        ReviewDecision decision = parseDecision(request.decision());

        Review review = new Review(annotation, reviewer, decision, LocalDateTime.now());
        review.setComment(request.comment());
        Review saved = reviews.save(review);
        applyDecision(annotation, decision);
        return ReviewResponse.from(saved);
    }

    /**
     * Lists the reviews of one annotation when its project is visible to the
     * calling user.
     */
    @Transactional(readOnly = true)
    public List<ReviewResponse> listByAnnotation(Long annotationId, String userEmail) {
        User user = loadUser(userEmail);
        Annotation annotation = loadVisibleAnnotation(annotationId, user);
        return reviews.findByAnnotationIdOrderByReviewedAtDesc(annotation.getId()).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    /**
     * Lists every review in a project visible to the calling user (owner,
     * admin, or an annotator assigned to at least one of its tasks), newest
     * first.
     */
    @Transactional(readOnly = true)
    public List<ReviewResponse> listByProject(Long projectId, String userEmail) {
        User user = loadUser(userEmail);
        if (!canSeeProject(projectId, user)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Project not found");
        }
        return reviews.findByProjectIdOrderByReviewedAtDesc(projectId).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    private void applyDecision(Annotation annotation, ReviewDecision decision) {
        Task task = annotation.getTask();
        if (decision == ReviewDecision.APPROVED) {
            annotation.setSource(AnnotationSource.HUMAN_APPROVED);
            annotation.setUpdatedAt(LocalDateTime.now());
            annotations.save(annotation);
            task.setStatus(TaskStatus.APPROVED);
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            tasks.save(task);
        } else {
            task.setStatus(TaskStatus.REJECTED);
            task.setUpdatedAt(LocalDateTime.now());
            tasks.save(task);
        }
    }

    private ReviewDecision parseDecision(String decision) {
        try {
            return ReviewDecision.valueOf(decision);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "decision must be APPROVED or REJECTED");
        }
    }

    private User loadUser(String userEmail) {
        return users.findByEmail(userEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    }

    private Annotation loadVisibleAnnotation(Long annotationId, User user) {
        Annotation annotation = annotations.findById(annotationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Annotation not found"));
        Task task = annotation.getTask();
        if (task == null || task.getProject() == null || task.getProject().getOwner() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Annotation not found");
        }
        boolean owner = Objects.equals(task.getProject().getOwner().getId(), user.getId());
        boolean admin = user.getRole() == Role.ADMIN;
        boolean assignee = task.getAssignedTo() != null
                && Objects.equals(task.getAssignedTo().getId(), user.getId());
        if (!owner && !admin && !assignee) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Annotation not found");
        }
        return annotation;
    }

    private boolean canDecide(Annotation annotation, User reviewer) {
        Task task = annotation.getTask();
        if (task == null || task.getProject() == null || task.getProject().getOwner() == null) {
            return false;
        }
        boolean owner = Objects.equals(task.getProject().getOwner().getId(), reviewer.getId());
        boolean admin = reviewer.getRole() == Role.ADMIN;
        return owner || admin;
    }

    private boolean canSeeProject(Long projectId, User user) {
        if (user.getRole() == Role.ADMIN) {
            return projects.findById(projectId).isPresent();
        }
        if (projects.findByIdAndOwnerId(projectId, user.getId()).isPresent()) {
            return true;
        }
        return tasks.findByAssignedToIdOrderByIdAsc(user.getId()).stream()
                .anyMatch(task -> task.getProject() != null
                        && Objects.equals(task.getProject().getId(), projectId));
    }
}
