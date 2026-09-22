package com.labelmate.labelmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelmate.labelmate.dto.RegisterRequest;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.RegistrationOtp;
import com.labelmate.labelmate.repository.RegistrationOtpRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private RegistrationOtpRepository otps;

    @Mock
    private UserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    private OtpService otpService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        org.mockito.Mockito.lenient().when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        otpService = new OtpService(otps, users, passwordEncoder, mailSenderProvider, 600, 60, 5,
                "LabelMate <no-reply@labelmate.local>");
    }

    @Test
    void shouldStorePendingOtpWhenRequestIsValid() {
        RegisterRequest request = new RegisterRequest("Asha", "asha@example.com", "secret123");
        when(users.existsByEmail("asha@example.com")).thenReturn(false);
        when(otps.findTopByEmailOrderByCreatedAtDesc("asha@example.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-secret");

        long expiry = otpService.requestOtp(request);

        assertEquals(600, expiry);
        ArgumentCaptor<RegistrationOtp> saved = ArgumentCaptor.forClass(RegistrationOtp.class);
        verify(otps).save(saved.capture());
        assertEquals("asha@example.com", saved.getValue().getEmail());
        assertEquals("Asha", saved.getValue().getUsername());
        assertEquals("hashed-secret", saved.getValue().getPasswordHash());
        assertNotNull(saved.getValue().getCodeHash());
        assertEquals(64, saved.getValue().getCodeHash().length());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldRejectOtpRequestWhenEmailExists() {
        RegisterRequest request = new RegisterRequest("Asha", "asha@example.com", "secret123");
        when(users.existsByEmail("asha@example.com")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> otpService.requestOtp(request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(otps, never()).save(any(RegistrationOtp.class));
    }

    @Test
    void shouldRejectOtpRequestDuringCooldown() {
        RegisterRequest request = new RegisterRequest("Asha", "asha@example.com", "secret123");
        when(users.existsByEmail("asha@example.com")).thenReturn(false);
        RegistrationOtp recent = new RegistrationOtp("asha@example.com", "Asha", "hash",
                "codehash".repeat(8).substring(0, 64),
                LocalDateTime.now().plusMinutes(10), LocalDateTime.now());
        when(otps.findTopByEmailOrderByCreatedAtDesc("asha@example.com"))
                .thenReturn(Optional.of(recent));

        ApiException ex = assertThrows(ApiException.class, () -> otpService.requestOtp(request));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
    }

    @Test
    void shouldAcceptCorrectCode() {
        String code = "123456";
        String codeHash = OtpService.sha256Hex(code);
        RegistrationOtp pending = new RegistrationOtp("asha@example.com", "Asha", "hashed",
                codeHash, LocalDateTime.now().plusMinutes(10), LocalDateTime.now());
        when(otps.findTopByEmailOrderByCreatedAtDesc("asha@example.com"))
                .thenReturn(Optional.of(pending));

        RegistrationOtp result = otpService.consumeVerifiedOtp("asha@example.com", code);

        assertEquals("asha@example.com", result.getEmail());
    }

    @Test
    void shouldRejectWrongCodeAndCountAttempt() {
        RegistrationOtp pending = new RegistrationOtp("asha@example.com", "Asha", "hashed",
                OtpService.sha256Hex("123456"),
                LocalDateTime.now().plusMinutes(10), LocalDateTime.now());
        when(otps.findTopByEmailOrderByCreatedAtDesc("asha@example.com"))
                .thenReturn(Optional.of(pending));

        ApiException ex = assertThrows(ApiException.class,
                () -> otpService.consumeVerifiedOtp("asha@example.com", "654321"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(1, pending.getAttempts());
        verify(otps).save(pending);
    }

    @Test
    void shouldRejectExpiredCode() {
        RegistrationOtp expired = new RegistrationOtp("asha@example.com", "Asha", "hashed",
                OtpService.sha256Hex("123456"),
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().minusMinutes(11));
        when(otps.findTopByEmailOrderByCreatedAtDesc("asha@example.com"))
                .thenReturn(Optional.of(expired));

        ApiException ex = assertThrows(ApiException.class,
                () -> otpService.consumeVerifiedOtp("asha@example.com", "123456"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().toLowerCase().contains("expired"));
    }

    @Test
    void shouldRejectWhenNoOtpRequested() {
        when(otps.findTopByEmailOrderByCreatedAtDesc("ghost@example.com"))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> otpService.consumeVerifiedOtp("ghost@example.com", "123456"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }
}
