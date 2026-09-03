package com.labelmate.labelmate.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelmate.labelmate.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class SpringAiClientTest {

    @Mock
    private ObjectProvider<ChatClient.Builder> builders;

    private SpringAiClient client(String apiKey, int timeoutSeconds) {
        AiProperties properties = new AiProperties();
        properties.setRequestTimeoutSeconds(timeoutSeconds);
        return new SpringAiClient(builders, properties, apiKey);
    }

    private ChatClient chatClientReturning(String content) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(content);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        when(builders.getIfAvailable()).thenReturn(builder);
        return chatClient;
    }

    @Test
    void shouldReturnModelOutput() {
        chatClientReturning("Positive");
        assertEquals("Positive", client("secret-key", 30).complete("system", "item"));
    }

    @Test
    void shouldRequireApiKeyBeforeTouchingProvider() {
        SpringAiClient unkeyed = client("  ", 30);
        AiException ex = assertThrows(AiException.class, () -> unkeyed.complete("system", "item"));
        assertEquals(AiException.Reason.NOT_CONFIGURED, ex.getReason());
        verify(builders, never()).getIfAvailable();
    }

    @Test
    void shouldReportMissingChatModel() {
        when(builders.getIfAvailable()).thenReturn(null);
        AiException ex =
                assertThrows(AiException.class, () -> client("secret-key", 30).complete("system", "item"));
        assertEquals(AiException.Reason.NOT_CONFIGURED, ex.getReason());
    }

    @Test
    void shouldMapProviderFailure() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new RuntimeException("connection reset"));
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        when(builders.getIfAvailable()).thenReturn(builder);

        AiException ex =
                assertThrows(AiException.class, () -> client("secret-key", 30).complete("system", "item"));
        assertEquals(AiException.Reason.PROVIDER_ERROR, ex.getReason());
    }

    @Test
    void shouldRejectEmptyOutput() {
        chatClientReturning("   ");
        AiException ex =
                assertThrows(AiException.class, () -> client("secret-key", 30).complete("system", "item"));
        assertEquals(AiException.Reason.INVALID_RESPONSE, ex.getReason());
    }

    @Test
    void shouldRejectEmptyPrompt() {
        AiException ex =
                assertThrows(AiException.class, () -> client("secret-key", 30).complete("system", "  "));
        assertEquals(AiException.Reason.INVALID_RESPONSE, ex.getReason());
        verify(builders, never()).getIfAvailable();
    }

    @Test
    void shouldTimeOutSlowProvider() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenAnswer(invocation -> {
                    Thread.sleep(10_000);
                    return "too late";
                });
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        when(builders.getIfAvailable()).thenReturn(builder);

        AiException ex =
                assertThrows(AiException.class, () -> client("secret-key", 1).complete("system", "item"));
        assertEquals(AiException.Reason.TIMEOUT, ex.getReason());
    }
}
