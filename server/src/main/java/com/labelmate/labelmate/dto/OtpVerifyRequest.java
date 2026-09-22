package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email,

        @NotBlank(message = "code is required")
        @Pattern(regexp = "\\d{6}", message = "code must be a 6-digit number")
        String code) {
}
