package com.labelmate.labelmate.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelmate.labelmate.config.AiProperties;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabelSuggestionServiceTest {

    @Mock
    private AiClient aiClient;

    private final AiProperties properties = new AiProperties();

    private LabelSuggestionService suggestionService;

    @BeforeEach
    void setUp() {
        properties.setMaxPromptChars(4000);
        suggestionService = new LabelSuggestionService(aiClient, properties);
    }

    private List<String> scheme() {
        return List.of("Positive", "Negative", "Neutral");
    }

    @Test
    void shouldReturnLabelAndConfidence() {
        when(aiClient.complete(anyString(), anyString())).thenReturn("Positive\n87");

        SuggestionResult result = suggestionService.suggest("I love it.", scheme(), "Pick sentiment.");

        assertEquals("Positive", result.suggestedLabel());
        assertEquals(new BigDecimal("87.00"), result.confidence());
        assertEquals(properties.getModel(), result.model());
    }

    @Test
    void shouldAcceptLabelWithoutConfidenceInsteadOfInventingOne() {
        when(aiClient.complete(anyString(), anyString())).thenReturn("Negative");

        SuggestionResult result = suggestionService.suggest("Awful.", scheme(), null);

        assertEquals("Negative", result.suggestedLabel());
        assertNull(result.confidence());
    }

    @Test
    void shouldMatchLabelsCaseInsensitivelyButReturnCanonicalSpelling() {
        when(aiClient.complete(anyString(), anyString())).thenReturn("neutral\n92.5");

        SuggestionResult result = suggestionService.suggest("It is fine.", scheme(), null);

        assertEquals("Neutral", result.suggestedLabel());
        assertEquals(new BigDecimal("92.50"), result.confidence());
    }

    @Test
    void shouldIgnoreUnusableConfidenceButKeepLabel() {
        when(aiClient.complete(anyString(), anyString())).thenReturn("Positive\nvery sure");

        SuggestionResult result = suggestionService.suggest("I love it.", scheme(), null);

        assertEquals("Positive", result.suggestedLabel());
        assertNull(result.confidence());
    }

    @Test
    void shouldIgnoreOutOfRangeConfidence() {
        when(aiClient.complete(anyString(), anyString())).thenReturn("Positive\n150");

        SuggestionResult result = suggestionService.suggest("I love it.", scheme(), null);

        assertEquals("Positive", result.suggestedLabel());
        assertNull(result.confidence());
    }

    @Test
    void shouldRejectLabelOutsideScheme() {
        when(aiClient.complete(anyString(), anyString())).thenReturn("Mixed\n70");

        AiException ex = assertThrows(
                AiException.class, () -> suggestionService.suggest("It is fine.", scheme(), null));

        assertEquals(AiException.Reason.INVALID_RESPONSE, ex.getReason());
    }

    @Test
    void shouldRejectEmptyModelOutput() {
        when(aiClient.complete(anyString(), anyString())).thenReturn("  ");

        AiException ex = assertThrows(
                AiException.class, () -> suggestionService.suggest("It is fine.", scheme(), null));

        assertEquals(AiException.Reason.INVALID_RESPONSE, ex.getReason());
    }

    @Test
    void shouldPropagateProviderFailureForManualFallback() {
        when(aiClient.complete(anyString(), anyString()))
                .thenThrow(new AiException(AiException.Reason.PROVIDER_ERROR, "down"));

        AiException ex = assertThrows(
                AiException.class, () -> suggestionService.suggest("It is fine.", scheme(), null));

        assertEquals(AiException.Reason.PROVIDER_ERROR, ex.getReason());
    }

    @Test
    void shouldPropagateTimeoutForManualFallback() {
        when(aiClient.complete(anyString(), anyString()))
                .thenThrow(new AiException(AiException.Reason.TIMEOUT, "slow"));

        AiException ex = assertThrows(
                AiException.class, () -> suggestionService.suggest("It is fine.", scheme(), null));

        assertEquals(AiException.Reason.TIMEOUT, ex.getReason());
    }

    @Test
    void shouldPropagateUnavailableForManualFallback() {
        when(aiClient.complete(anyString(), anyString()))
                .thenThrow(new AiException(AiException.Reason.NOT_CONFIGURED, "off"));

        AiException ex = assertThrows(
                AiException.class, () -> suggestionService.suggest("It is fine.", scheme(), null));

        assertEquals(AiException.Reason.NOT_CONFIGURED, ex.getReason());
    }

    @Test
    void shouldRejectBlankItem() {
        assertThrows(
                IllegalArgumentException.class, () -> suggestionService.suggest("  ", scheme(), null));
    }

    @Test
    void shouldRejectEmptyScheme() {
        assertThrows(
                IllegalArgumentException.class,
                () -> suggestionService.suggest("It is fine.", List.of("  "), null));
    }

    @Test
    void shouldTruncateLongItemsButKeepLabels() {
        properties.setMaxPromptChars(10);
        when(aiClient.complete(anyString(), anyString())).thenReturn("Positive");

        SuggestionResult result =
                suggestionService.suggest("I love it very much indeed.", scheme(), null);

        assertEquals("Positive", result.suggestedLabel());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(aiClient).complete(anyString(), prompt.capture());
        assertTrue(prompt.getValue().length() <= 10 + 80);
        assertTrue(prompt.getValue().startsWith("I love it"));

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(aiClient).complete(system.capture(), anyString());
        assertTrue(system.getValue().contains("[Positive]"));
        assertTrue(system.getValue().contains("[Negative]"));
        assertTrue(system.getValue().contains("[Neutral]"));
    }
}
