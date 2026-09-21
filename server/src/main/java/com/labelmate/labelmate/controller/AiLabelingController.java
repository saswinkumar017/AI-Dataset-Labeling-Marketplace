package com.labelmate.labelmate.controller;

import com.labelmate.labelmate.dto.AnnotationResponse;
import com.labelmate.labelmate.dto.AutoLabelRequest;
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
 * HTTP boundary for AI auto-labeling.
 *
 * <p>Unlike suggestions (which only propose), auto-label persists one
 * {@code AI} annotation — but it still enters the review queue as submitted
 * work, never as a final label. The project scheme and instructions come
 * from the server; the caller only triggers the run.
 */
@RestController
@Tag(name = "AI Auto-label", description = "Server-side AI annotation")
public class AiLabelingController {

    private final AiSuggestionService suggestionService;

    public AiLabelingController(AiSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    /** Labels a task with the AI for a caller who owns it or is assigned. */
    @PostMapping("/api/ai/tasks/{taskId}/auto-label")
    @Operation(summary = "Auto-label a task with AI")
    public ResponseEntity<AnnotationResponse> autoLabel(
            @PathVariable Long taskId,
            @Valid @RequestBody(required = false) AutoLabelRequest request,
            Authentication authentication) {
        AutoLabelRequest effective = request == null ? new AutoLabelRequest(null) : request;
        AnnotationResponse annotation =
                suggestionService.autoLabel(taskId, effective, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(annotation);
    }
}
