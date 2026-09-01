package com.labelmate.labelmate.service.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.labelmate.labelmate.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Guards the production default: a fresh boot wires the no-op client, so the
 * application starts with no provider credentials and manual annotation is
 * never blocked by AI configuration.
 */
@SpringBootTest
class AiDisabledByDefaultTest {

    @Autowired
    private AiClient aiClient;

    @Autowired
    private AiProperties aiProperties;

    @Test
    void shouldWireNoOpClientByDefault() {
        assertTrue(aiClient instanceof NoOpAiClient);
        assertFalse(aiProperties.isEnabled());
    }
}
