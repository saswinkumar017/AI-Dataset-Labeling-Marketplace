package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.SuggestRequest;
import com.labelmate.labelmate.dto.SuggestionResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.AiSuggestion;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.Task;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.AiSuggestionRepository;
import com.labelmate.labelmate.repository.LabelRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import com.labelmate.labelmate.service.ai.AiException;
import com.labelmate.labelmate.service.ai.LabelSuggestionService;
import com.labelmate.labelmate.service.ai.SuggestionResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connects AI suggestions to the annotation workflow.
 *
 * <p>One intentional call per request: the caller supplies the candidate
 * labels, the service adds the task's item text plus project instructions,
 * and the resulting suggestion is stored as an {@code ai_suggestions} row
 * for traceability. Nothing here creates annotations — the annotator still
 * submits manually, and review still decides. No result caching: a fresh
 * suggestion per request avoids stale labels entirely. AI failures surface
 * as {@link AiException} so the workspace falls back to manual labeling.
 */
@Service
public class AiSuggestionService {

    private final AiSuggestionRepository suggestions;
    private final TaskRepository tasks;
    private final LabelRepository labels;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final LabelSuggestionService suggestionService;

    public AiSuggestionService(
            AiSuggestionRepository suggestions,
            TaskRepository tasks,
            LabelRepository labels,
            ProjectRepository projects,
            UserRepository users,
            LabelSuggestionService suggestionService) {
        this.suggestions = suggestions;
        this.tasks = tasks;
        this.labels = labels;
        this.projects = projects;
        this.users = users;
        this.suggestionService = suggestionService;
    }

    /**
     * Generates and stores one suggestion for a task visible to the caller.
     */
    @Transactional
    public SuggestionResponse suggest(Long taskId, SuggestRequest request, String userEmail) {
        User user = users.findByEmail(userEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
        Task task = tasks.findById(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Task not found"));
        if (task.getProject() == null
                || task.getProject().getOwner() == null
                || (!Objects.equals(task.getProject().getOwner().getId(), user.getId())
                        && user.getRole() != Role.ADMIN)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Task not found");
        }
        if (task.getItemData() == null || task.getItemData().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Task has no text to suggest from");
        }
        List<String> scheme = request.labels().stream()
                .filter(label -> label != null && !label.isBlank())
                .map(String::strip)
                .toList();
        SuggestionResult result = suggestionService.suggest(
                task.getItemData(), scheme, task.getProject().getInstructions());

        AiSuggestion suggestion = new AiSuggestion(task, LocalDateTime.now());
        suggestion.setSuggestedLabelName(result.suggestedLabel());
        labels.findByProjectIdAndName(task.getProject().getId(), result.suggestedLabel())
                .ifPresent(suggestion::setSuggestedLabel);
        suggestion.setConfidence(result.confidence());
        suggestion.setModel(result.model());
        // raw_response stays null: only the parsed label/confidence are kept,
        // never an invented transcript of provider output.
        return SuggestionResponse.from(suggestions.save(suggestion));
    }
}
