package com.labelmate.labelmate.config;

import com.labelmate.labelmate.service.ai.AiClient;
import com.labelmate.labelmate.service.ai.NoOpAiClient;
import com.labelmate.labelmate.service.ai.SpringAiClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the {@link AiClient} implementation.
 *
 * <p>Bean methods (rather than conditions on scanned components) make the
 * choice deterministic: exactly one client exists. The Spring AI client is
 * only considered when {@code app.ai.enabled=true}; otherwise the no-op
 * client is wired and the application runs with zero provider requirements.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    @ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
    public AiClient springAiClient(
            ObjectProvider<ChatClient.Builder> builders,
            AiProperties properties,
            @Value("${spring.ai.openai.api-key:}") String apiKey) {
        return new SpringAiClient(builders, properties, apiKey);
    }

    @Bean
    @ConditionalOnMissingBean(AiClient.class)
    public AiClient noOpAiClient() {
        return new NoOpAiClient();
    }
}
