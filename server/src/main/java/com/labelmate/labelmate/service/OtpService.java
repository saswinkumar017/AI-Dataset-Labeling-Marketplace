package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.RegisterRequest;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.RegistrationOtp;
import com.labelmate.labelmate.repository.RegistrationOtpRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates, stores, emails, and verifies 6-digit registration codes.
 *
 * <p>Only the SHA-256 hash of the code is stored in
 * {@code registration_otps} — never the plain code. The plain code
 * travels only by email (and, when no SMTP host is configured, to the
 * application log so local development still works).
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RegistrationOtpRepository otps;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    private final long expirySeconds;
    private final long resendCooldownSeconds;
    private final int maxAttempts;
    private final String mailFrom;

    public OtpService(
            RegistrationOtpRepository otps,
            UserRepository users,
            PasswordEncoder passwordEncoder,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.otp.expiry-seconds:600}") long expirySeconds,
            @Value("${app.otp.resend-cooldown-seconds:60}") long resendCooldownSeconds,
            @Value("${app.otp.max-attempts:5}") int maxAttempts,
            @Value("${app.otp.mail-from:LabelMate <no-reply@labelmate.local>}") String mailFrom) {
        this.otps = otps;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.mailSenderProvider = mailSenderProvider;
        this.expirySeconds = expirySeconds;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxAttempts = maxAttempts;
        this.mailFrom = mailFrom;
    }

    /** Step 1: validate the signup details, store a pending OTP, email the code. */
    @Transactional
    public long requestOtp(RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        LocalDateTime now = LocalDateTime.now();
        Optional<RegistrationOtp> latest = otps.findTopByEmailOrderByCreatedAtDesc(request.email());
        if (latest.isPresent()
                && latest.get().getCreatedAt().plusSeconds(resendCooldownSeconds).isAfter(now)) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting a new code");
        }

        String code = generateCode();
        RegistrationOtp pending = new RegistrationOtp(
                request.email(),
                request.username(),
                passwordEncoder.encode(request.password()),
                sha256Hex(code),
                now.plusSeconds(expirySeconds),
                now);
        otps.deleteByEmail(request.email());
        otps.save(pending);
        sendCodeEmail(request.email(), request.username(), code);
        return expirySeconds;
    }

    /** Step 2: check the code and return the pending record when it matches. */
    @Transactional
    public RegistrationOtp consumeVerifiedOtp(String email, String code) {
        RegistrationOtp pending = otps.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "No verification code was requested for this email"));
        LocalDateTime now = LocalDateTime.now();
        if (pending.isExpired(now)) {
            otps.deleteByEmail(email);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Verification code has expired. Request a new one");
        }
        if (pending.getAttempts() >= maxAttempts) {
            otps.deleteByEmail(email);
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS, "Too many wrong attempts. Request a new code");
        }
        if (!sha256Hex(code).equals(pending.getCodeHash())) {
            pending.setAttempts(pending.getAttempts() + 1);
            otps.save(pending);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid verification code");
        }
        return pending;
    }

    @Transactional
    public void clearForEmail(String email) {
        otps.deleteByEmail(email);
    }

    String generateCode() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private void sendCodeEmail(String to, String username, String code) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            // No mail bean (e.g. starter backed off with no SMTP host):
            // keep the flow usable and log instead. The code hash is still
            // stored; only the plain code goes to the log (never to the API).
            log.info("No SMTP configured — registration OTP for {}: {}", to, code);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject("LabelMate verification code");
        message.setText("Hi " + username + ",\n\nYour LabelMate verification code is: " + code
                + "\nIt expires in 10 minutes. If you did not request this, ignore this email.\n\n— LabelMate");
        try {
            mailSender.send(message);
            log.info("Sent registration OTP to {}", to);
        } catch (MailException | IllegalStateException ex) {
            // No SMTP in local dev / tests: keep the flow usable and log instead.
            // The code hash is still stored; only the plain code goes to the log (never to the API).
            log.warn("SMTP send failed for {} ({}). OTP for local development: {}", to, ex.getMessage(), code);
        }
    }
}
