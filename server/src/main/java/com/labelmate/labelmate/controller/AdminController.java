package com.labelmate.labelmate.controller;

import com.labelmate.labelmate.dto.AdminAiSettingsResponse;
import com.labelmate.labelmate.dto.AdminAiSettingsUpdateRequest;
import com.labelmate.labelmate.dto.AdminOverview;
import com.labelmate.labelmate.dto.RoleUpdateRequest;
import com.labelmate.labelmate.dto.UserResponse;
import com.labelmate.labelmate.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for platform administration.
 *
 * <p>Role enforcement lives in {@link AdminService}, which re-reads the
 * caller's persisted role: nothing here trusts client-supplied roles.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Platform administration (ADMIN role only)")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** Lists every account (identity and role only). */
    @GetMapping("/users")
    @Operation(summary = "List all users (admin only)")
    public ResponseEntity<List<UserResponse>> listUsers(Authentication authentication) {
        return ResponseEntity.ok(adminService.listUsers(authentication.getName()));
    }

    /** Promotes or demotes one account (admin only, guarded in service). */
    @PatchMapping("/users/{id}/role")
    @Operation(summary = "Change a user's role (admin only)")
    public ResponseEntity<UserResponse> updateUserRole(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequest request) {
        return ResponseEntity.ok(adminService.updateUserRole(authentication.getName(), id, request.role()));
    }

    /** Returns platform-wide workflow totals. */
    @GetMapping("/overview")
    @Operation(summary = "Platform overview (admin only)")
    public ResponseEntity<AdminOverview> overview(Authentication authentication) {
        return ResponseEntity.ok(adminService.overview(authentication.getName()));
    }

    /** Reads AI provider settings with the key masked (admin only). */
    @GetMapping("/ai-settings")
    @Operation(summary = "Read AI settings, key masked (admin only)")
    public ResponseEntity<AdminAiSettingsResponse> aiSettings(Authentication authentication) {
        return ResponseEntity.ok(adminService.getAiSettings(authentication.getName()));
    }

    /** Updates AI provider settings (admin only, restart to apply). */
    @PutMapping("/ai-settings")
    @Operation(summary = "Update AI settings (admin only)")
    public ResponseEntity<AdminAiSettingsResponse> updateAiSettings(
            Authentication authentication,
            @Valid @RequestBody AdminAiSettingsUpdateRequest request) {
        return ResponseEntity.ok(adminService.updateAiSettings(authentication.getName(), request));
    }
}
