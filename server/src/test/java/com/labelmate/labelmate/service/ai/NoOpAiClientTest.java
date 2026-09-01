package com.labelmate.labelmate.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoOpAiClientTest {

    private final NoOpAiClient client = new NoOpAiClient();

    @Test
    void shouldReportNotConfigured() {
        AiException ex = assertThrows(AiException.class, () -> client.complete("system", "item"));
        assertEquals(AiException.Reason.NOT_CONFIGURED, ex.getReason());
    }

    @Test
    void shouldExplainHowToEnable() {
        AiException ex = assertThrows(AiException.class, () -> client.complete("system", "item"));
        assertTrue(ex.getMessage().contains("app.ai.enabled=true"));
    }
}
