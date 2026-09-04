package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.ExportRow;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Annotation;
import com.labelmate.labelmate.model.AnnotationSource;
import com.labelmate.labelmate.model.Project;
import com.labelmate.labelmate.model.Review;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.Task;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.ReviewRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verified-dataset export.
 *
 * <p>A row exists only for a task whose latest human work was approved in
 * review ({@code HUMAN_APPROVED}): unreviewed, rejected, or never-annotated
 * tasks are excluded, never exported as verified. Export is read-only for
 * the workflow — it changes no state — and scoped to the project owner (or
 * an admin), so one owner can never export another's dataset.
 */
@Service
public class ExportService {

    private final TaskRepository tasks;
    private final AnnotationRepository annotations;
    private final ReviewRepository reviews;
    private final ProjectRepository projects;
    private final UserRepository users;

    public ExportService(
            TaskRepository tasks,
            AnnotationRepository annotations,
            ReviewRepository reviews,
            ProjectRepository projects,
            UserRepository users) {
        this.tasks = tasks;
        this.annotations = annotations;
        this.reviews = reviews;
        this.projects = projects;
        this.users = users;
    }

    /**
     * Collects verified rows for a project visible to the caller, in queue order.
     */
    @Transactional(readOnly = true)
    public List<ExportRow> exportProject(Long projectId, String userEmail) {
        Project project = loadVisibleProject(projectId, userEmail);
        List<ExportRow> rows = new ArrayList<>();
        for (Task task : tasks.findByProjectIdOrderByItemIndexAscIdAsc(project.getId())) {
            latestApproved(task).ifPresent(approved -> rows.add(approved));
        }
        return rows;
    }

    /**
     * Renders verified rows as CSV (header plus one line per row, RFC-4180
     * field escaping). No extra dependency: the format is a few lines.
     */
    public String exportProjectCsv(Long projectId, String userEmail) {
        StringBuilder csv = new StringBuilder("item_index,item_data,label,task_status,reviewed_at\n");
        for (ExportRow row : exportProject(projectId, userEmail)) {
            csv.append(cell(row.itemIndex() == null ? "" : row.itemIndex().toString())).append(',');
            csv.append(cell(row.itemData())).append(',');
            csv.append(cell(row.label())).append(',');
            csv.append(cell(row.taskStatus())).append(',');
            csv.append(cell(row.reviewedAt() == null ? "" : row.reviewedAt().toString())).append('\n');
        }
        return csv.toString();
    }

    private Optional<ExportRow> latestApproved(Task task) {
        for (Annotation annotation : annotations.findByTaskIdOrderByCreatedAtDesc(task.getId())) {
            if (annotation.getSource() == AnnotationSource.HUMAN_APPROVED) {
                Review review = reviews.findByAnnotationIdOrderByReviewedAtDesc(annotation.getId()).stream()
                        .findFirst()
                        .orElse(null);
                return Optional.of(ExportRow.from(annotation, review));
            }
        }
        return Optional.empty();
    }

    private String cell(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private Project loadVisibleProject(Long projectId, String userEmail) {
        User user = users.findByEmail(userEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
        if (user.getRole() == Role.ADMIN) {
            return projects.findById(projectId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
        }
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
        if (project.getOwner() == null || !Objects.equals(project.getOwner().getId(), user.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Project not found");
        }
        return project;
    }
}
