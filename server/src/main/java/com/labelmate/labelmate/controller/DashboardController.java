package com.labelmate.labelmate.controller;

import com.labelmate.labelmate.dto.DashboardSummary;
import com.labelmate.labelmate.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for dashboard numbers.
 *
 * <p>Forwards the authenticated username to {@link DashboardService}, which
 * scopes every count to the caller's own projects.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Workflow summary")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** Returns workflow counts for the authenticated user. */
    @GetMapping("/summary")
    @Operation(summary = "Get my workflow summary")
    public ResponseEntity<DashboardSummary> summary(Authentication authentication) {
        return ResponseEntity.ok(dashboardService.summarize(authentication.getName()));
    }
}
