package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.AdminOverview;
import com.labelmate.labelmate.dto.UserResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.ReviewDecision;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.AiSuggestionRepository;
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.DatasetRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.ReviewRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform administration.
 *
 * <p>Every method re-checks the caller's persisted role server-side: admin
 * pages are never gated by a request flag, a URL, or frontend state.
 * Non-admin callers receive 403 (they are authenticated, just not
 * administrators); strangers to the system never reach here because
 * authentication runs first. No endpoint assigns roles — admin status is
 * granted directly in the database (see docs/admin.md).
 */
@Service
public class AdminService {

    private final UserRepository users;
    private final DatasetRepository datasets;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final AnnotationRepository annotations;
    private final ReviewRepository reviews;
    private final AiSuggestionRepository suggestions;

    public AdminService(
            UserRepository users,
            DatasetRepository datasets,
            ProjectRepository projects,
            TaskRepository tasks,
            AnnotationRepository annotations,
            ReviewRepository reviews,
            AiSuggestionRepository suggestions) {
        this.users = users;
        this.datasets = datasets;
        this.projects = projects;
        this.tasks = tasks;
        this.annotations = annotations;
        this.reviews = reviews;
        this.suggestions = suggestions;
    }

    /**
     * Lists every account (identity and role only — never credentials).
     */
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(String callerEmail) {
        requireAdmin(callerEmail);
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    /**
     * Platform-wide workflow totals. Counts only — no user content, and no
     * fabricated metrics: each number is a direct repository count.
     */
    @Transactional(readOnly = true)
    public AdminOverview overview(String callerEmail) {
        requireAdmin(callerEmail);
        long approved = 0;
        long rejected = 0;
        for (com.labelmate.labelmate.model.Review review : reviews.findAll()) {
            if (review.getDecision() == ReviewDecision.APPROVED) {
                approved++;
            } else {
                rejected++;
            }
        }
        return new AdminOverview(
                users.count(),
                datasets.count(),
                projects.count(),
                tasks.count(),
                annotations.count(),
                approved + rejected,
                approved,
                rejected,
                suggestions.count());
    }

    private User requireAdmin(String callerEmail) {
        User caller = users.findByEmail(callerEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
        if (caller.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return caller;
    }
}
