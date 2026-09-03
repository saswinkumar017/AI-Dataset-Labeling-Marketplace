package com.labelmate.labelmate.controller;

import com.labelmate.labelmate.dto.SuggestRequest;
import com.labelmate.labelmate.dto.SuggestionResponse;
import com.labelmate.labelmate.service.AiSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for AI label suggestions.
 *
 * <p>One intentional suggestion per request: the annotator supplies candidate
 * labels, the service adds server-side context, and the response carries
 * only the parsed suggestion. Suggesting never creates annotations — the
 * annotator still submits manually and review still decides.
 */
@RestController
@Tag(name = "AI Suggestions", description = "Assistive label suggestions")
public class AiSuggestionController {

    private final AiSuggestionService suggestionService;

    public AiSuggestionController(AiSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    /** Generates and stores one suggestion for a task visible to the caller. */
    @PostMapping("/api/tasks/{taskId}/suggest")
    @Operation(summary = "Suggest a label for a task")
    public ResponseEntity<SuggestionResponse> suggest(
            @PathVariable Long taskId,
            @Valid @RequestBody SuggestRequest request,
            Authentication authentication) {
        SuggestionResponse suggestion = suggestionService.suggest(taskId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(suggestion);
    }
}
