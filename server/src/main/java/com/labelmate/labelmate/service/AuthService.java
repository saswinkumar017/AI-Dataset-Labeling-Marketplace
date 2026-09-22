package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.AuthResponse;
import com.labelmate.labelmate.dto.LoginRequest;
import com.labelmate.labelmate.dto.OtpSendResponse;
import com.labelmate.labelmate.dto.OtpVerifyRequest;
import com.labelmate.labelmate.dto.RegisterRequest;
import com.labelmate.labelmate.dto.UserResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.RegistrationOtp;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.UserRepository;
import com.labelmate.labelmate.security.JwtService;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final OtpService otpService;

    public AuthService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            OtpService otpService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.otpService = otpService;
    }

    public UserResponse register(RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        User user = new User(
                request.email(),
                request.username(),
                passwordEncoder.encode(request.password()),
                Role.ANNOTATOR,
                LocalDateTime.now());
        User saved = users.save(user);
        return UserResponse.from(saved);
    }

    public OtpSendResponse requestRegistrationOtp(RegisterRequest request) {
        long expiresInSeconds = otpService.requestOtp(request);
        return OtpSendResponse.sent(expiresInSeconds);
    }

    public UserResponse verifyRegistrationOtp(OtpVerifyRequest request) {
        RegistrationOtp pending = otpService.consumeVerifiedOtp(request.email(), request.code());
        if (users.existsByEmail(pending.getEmail())) {
            otpService.clearForEmail(pending.getEmail());
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        User user = new User(
                pending.getEmail(),
                pending.getUsername(),
                pending.getPasswordHash(),
                Role.ANNOTATOR,
                LocalDateTime.now());
        User saved = users.save(user);
        otpService.clearForEmail(pending.getEmail());
        return UserResponse.from(saved);
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        User user = users.findByEmail(request.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        String token = jwtService.generateToken(user);
        return AuthResponse.bearer(token, UserResponse.from(user));
    }

    public UserResponse findByEmail(String email) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        return UserResponse.from(user);
    }
}
