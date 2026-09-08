package com.labelmate.labelmate.controller;

import com.labelmate.labelmate.dto.AdminOverview;
import com.labelmate.labelmate.dto.UserResponse;
import com.labelmate.labelmate.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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

    /** Returns platform-wide workflow totals. */
    @GetMapping("/overview")
    @Operation(summary = "Platform overview (admin only)")
    public ResponseEntity<AdminOverview> overview(Authentication authentication) {
        return ResponseEntity.ok(adminService.overview(authentication.getName()));
    }
}
