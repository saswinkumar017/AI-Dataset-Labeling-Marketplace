package com.labelmate.labelmate.service.ai;

/**
 * Controlled application-level AI failure.
 *
 * <p>Callers translate this into user-facing behavior: manual annotation
 * always remains available, so every reason maps to "AI unavailable, keep
 * working manually" rather than a broken workflow.
 */
public class AiException extends RuntimeException {

    public enum Reason {
        /** AI is switched off or has no usable credentials/model. */
        NOT_CONFIGURED,
        /** Provider call failed (network, auth, rate limit, 5xx...). */
        PROVIDER_ERROR,
        /** Provider call exceeded the configured request timeout. */
        TIMEOUT,
        /** Provider answered, but the output was empty or unusable. */
        INVALID_RESPONSE
    }

    private final Reason reason;

    public AiException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AiException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
