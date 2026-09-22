package com.labelmate.labelmate.dto;

public record OtpSendResponse(String message, long expiresInSeconds) {

    public static OtpSendResponse sent(long expiresInSeconds) {
        return new OtpSendResponse(
                "Verification code sent to your email. It expires in 10 minutes.", expiresInSeconds);
    }
}
