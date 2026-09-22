package com.labelmate.labelmate.controller;

import com.labelmate.labelmate.dto.AuthResponse;
import com.labelmate.labelmate.dto.LoginRequest;
import com.labelmate.labelmate.dto.OtpSendResponse;
import com.labelmate.labelmate.dto.OtpVerifyRequest;
import com.labelmate.labelmate.dto.RegisterRequest;
import com.labelmate.labelmate.dto.UserResponse;
import com.labelmate.labelmate.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Registration and login")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/register/request-otp")
    @Operation(summary = "Step 1 of registration: validate details and email a 6-digit code")
    public ResponseEntity<OtpSendResponse> requestOtp(@Valid @RequestBody RegisterRequest request) {
        OtpSendResponse response = authService.requestRegistrationOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register/verify-otp")
    @Operation(summary = "Step 2 of registration: verify the code and create the user")
    public ResponseEntity<UserResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        UserResponse user = authService.verifyRegistrationOtp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive a JWT")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Return the authenticated user")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        UserResponse user = authService.findByEmail(authentication.getName());
        return ResponseEntity.ok(user);
    }
}
