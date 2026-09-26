package com.labelmate.labelmate.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.labelmate.labelmate.config.AiProperties;
import com.labelmate.labelmate.model.AppSetting;
import com.labelmate.labelmate.repository.AppSettingRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiSettingsResolverTest {

    @Mock
    private AppSettingRepository settings;

    private AiSettingsResolver resolver(String startupKey) {
        AiProperties properties = new AiProperties();
        return new AiSettingsResolver(settings, properties, startupKey);
    }

    private AppSetting row(String key, String value) {
        return new AppSetting(key, value, LocalDateTime.now());
    }

    @Test
    void shouldFallBackToStartupConfigWhenDbIsEmpty() {
        AiSettingsResolver resolver = resolver("env-key");

        assertEquals("env-key", resolver.apiKey());
        assertEquals("openrouter/auto", resolver.model());
    }

    @Test
    void shouldPreferPanelValuesOverStartupConfig() {
        when(settings.findById(AiSettingsResolver.KEY_API_KEY))
                .thenReturn(Optional.of(row(AiSettingsResolver.KEY_API_KEY, "panel-key")));
        when(settings.findById(AiSettingsResolver.KEY_MODEL))
                .thenReturn(Optional.of(row(AiSettingsResolver.KEY_MODEL, "panel/model")));
        AiSettingsResolver resolver = resolver("env-key");

        assertEquals("panel-key", resolver.apiKey());
        assertEquals("panel/model", resolver.model());
    }

    @Test
    void shouldFallBackWhenTableIsMissing() {
        when(settings.findById(AiSettingsResolver.KEY_API_KEY)).thenThrow(new RuntimeException("no table"));
        AiSettingsResolver resolver = resolver("env-key");

        assertEquals("env-key", resolver.apiKey());
        assertTrue(AiSettingsResolver.mask("panel-key").startsWith("****"));
    }
}
