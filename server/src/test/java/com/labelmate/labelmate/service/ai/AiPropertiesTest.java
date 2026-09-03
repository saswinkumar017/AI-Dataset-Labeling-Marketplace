package com.labelmate.labelmate.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.labelmate.labelmate.config.AiConfig;
import com.labelmate.labelmate.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AiConfig.class);

    @Test
    void shouldBeDisabledByDefault() {
        runner.run(context -> {
            AiProperties properties = context.getBean(AiProperties.class);
            assertFalse(properties.isEnabled());
            assertTrue(properties.getMaxPromptChars() > 0);
            assertTrue(properties.getRequestTimeoutSeconds() > 0);
        });
    }

    @Test
    void shouldBindApplicationLevelSettings() {
        runner.withPropertyValues(
                        "app.ai.enabled=true",
                        "app.ai.model=openrouter/test-model",
                        "app.ai.max-prompt-chars=1234",
                        "app.ai.request-timeout-seconds=7")
                .run(context -> {
                    AiProperties properties = context.getBean(AiProperties.class);
                    assertTrue(properties.isEnabled());
                    assertEquals("openrouter/test-model", properties.getModel());
                    assertEquals(1234, properties.getMaxPromptChars());
                    assertEquals(7, properties.getRequestTimeoutSeconds());
                });
    }

    @Test
    void shouldWireNoOpClientWhenDisabled() {
        runner.run(context -> {
            AiClient client = context.getBean(AiClient.class);
            assertTrue(client instanceof NoOpAiClient);
        });
    }

    @Test
    void shouldWireSpringClientWhenEnabled() {
        runner.withPropertyValues("app.ai.enabled=true")
                .run(context -> {
                    AiClient client = context.getBean(AiClient.class);
                    assertTrue(client instanceof SpringAiClient);
                });
    }
}
