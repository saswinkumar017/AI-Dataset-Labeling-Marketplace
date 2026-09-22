package com.labelmate.labelmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelmate.labelmate.dto.AuthResponse;
import com.labelmate.labelmate.dto.LoginRequest;
import com.labelmate.labelmate.dto.RegisterRequest;
import com.labelmate.labelmate.dto.UserResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.UserRepository;
import com.labelmate.labelmate.security.JwtService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private OtpService otpService;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldRegisterUserWhenRequestIsValid() {
        RegisterRequest request = new RegisterRequest("Asha", "asha@example.com", "Secret123!");
        when(users.existsByEmail("asha@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("hashed-secret");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("asha@example.com", response.email());
        assertEquals("Asha", response.username());
        assertEquals(Role.ANNOTATOR, response.role());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals("hashed-secret", saved.getValue().getPasswordHash());
        assertNotEquals("Secret123!", saved.getValue().getPasswordHash());
    }

    @Test
    void shouldRejectRegistrationWhenEmailExists() {
        RegisterRequest request = new RegisterRequest("Asha", "asha@example.com", "Secret123!");
        when(users.existsByEmail("asha@example.com")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> authService.register(request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(users, never()).save(any(User.class));
    }

    @Test
    void shouldAuthenticateWhenCredentialsAreValid() {
        User user = new User("asha@example.com", "Asha", "hashed-secret", Role.ANNOTATOR, LocalDateTime.now());
        when(users.findByEmail("asha@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("test-token");

        AuthResponse response = authService.login(new LoginRequest("asha@example.com", "Secret123!"));

        assertNotNull(response);
        assertEquals("test-token", response.token());
        assertEquals("Bearer", response.tokenType());
        assertEquals("asha@example.com", response.user().email());
        verify(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void shouldRejectLoginWhenCredentialsAreInvalid() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> authService.login(new LoginRequest("asha@example.com", "wrong")));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void shouldStoreHashedPassword() {
        RegisterRequest request = new RegisterRequest("Asha", "asha@example.com", "Secret123!");
        when(users.existsByEmail("asha@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("bcrypt-hashed-value");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertNotEquals("Secret123!", saved.getValue().getPasswordHash());
        assertEquals("bcrypt-hashed-value", saved.getValue().getPasswordHash());
    }
    @Test
    void shouldReturnUserWhenEmailExists() {
        User user = new User("asha@example.com", "Asha", "hashed", Role.ANNOTATOR, LocalDateTime.now());
        when(users.findByEmail("asha@example.com")).thenReturn(Optional.of(user));

        UserResponse response = authService.findByEmail("asha@example.com");

        assertEquals("asha@example.com", response.email());
        assertEquals(Role.ANNOTATOR, response.role());
    }

    @Test
    void shouldReturnNotFoundWhenUserIsUnknown() {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> authService.findByEmail("ghost@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

}
