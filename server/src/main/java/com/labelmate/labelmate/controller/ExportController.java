package com.labelmate.labelmate.controller;

import com.labelmate.labelmate.dto.ExportRow;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for verified-dataset export.
 *
 * <p>Only human-approved rows leave the server (see {@link ExportService});
 * the format parameter selects JSON or CSV rendering of the same rows.
 */
@RestController
@Tag(name = "Export", description = "Verified dataset export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    /** Exports a project's verified rows as JSON (default) or CSV. */
    @GetMapping("/api/projects/{projectId}/export")
    @Operation(summary = "Export verified annotations of a project")
    public ResponseEntity<?> export(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "json") String format,
            Authentication authentication) {
        String name = authentication.getName();
        if ("csv".equalsIgnoreCase(format)) {
            String csv = exportService.exportProjectCsv(projectId, name);
            return ResponseEntity.ok()
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment()
                                    .filename("project-" + projectId + "-export.csv", StandardCharsets.UTF_8)
                                    .build()
                                    .toString())
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .body(csv);
        }
        if ("json".equalsIgnoreCase(format)) {
            List<ExportRow> rows = exportService.exportProject(projectId, name);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rows);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "format must be json or csv");
    }
}
