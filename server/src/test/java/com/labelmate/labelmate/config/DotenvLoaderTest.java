package com.labelmate.labelmate.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class DotenvLoaderTest {

    @TempDir
    private Path tempDir;

    private Path dotenv(String body) throws Exception {
        Path file = tempDir.resolve(".env");
        Files.writeString(file, body, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void shouldLoadPlainValuesAndSkipCommentsAndBlanks() throws Exception {
        Path file = dotenv("# a comment\n\nJWT_SECRET=abc123\nEMPTY=\n");

        MockEnvironment environment = new MockEnvironment();
        new DotenvLoader(file).postProcessEnvironment(environment, new SpringApplication());

        assertTrue(environment.getPropertySources().contains(DotenvLoader.SOURCE_NAME));
        assertEquals("abc123", environment.getProperty("JWT_SECRET"));
        assertEquals("", environment.getProperty("EMPTY"));
    }

    @Test
    void shouldSupportExportPrefixAndMatchingQuotes() throws Exception {
        Path file = dotenv("export QUOTED=\"hello world\"\nSINGLE='a b'\nMISMATCH=\"oops'\n");

        MockEnvironment environment = new MockEnvironment();
        new DotenvLoader(file).postProcessEnvironment(environment, new SpringApplication());

        assertEquals("hello world", environment.getProperty("QUOTED"));
        assertEquals("a b", environment.getProperty("SINGLE"));
        assertEquals("\"oops'", environment.getProperty("MISMATCH"));
    }

    @Test
    void shouldSkipLinesWithoutUsableKeys() throws Exception {
        Path file = dotenv("JUST_A_FLAG\n=novalue\n   \nOK=yes\n");

        MockEnvironment environment = new MockEnvironment();
        new DotenvLoader(file).postProcessEnvironment(environment, new SpringApplication());

        assertFalse(environment.containsProperty("JUST_A_FLAG"));
        assertEquals("yes", environment.getProperty("OK"));
    }

    @Test
    void shouldYieldToHigherPrecedenceSources() throws Exception {
        Path file = dotenv("SHARED=from-file\nONLY_FILE=yes\n");

        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources()
                .addFirst(new org.springframework.core.env.MapPropertySource(
                        "test", Map.of("SHARED", "from-test")));
        new DotenvLoader(file).postProcessEnvironment(environment, new SpringApplication());

        assertEquals("from-test", environment.getProperty("SHARED"));
        assertEquals("yes", environment.getProperty("ONLY_FILE"));
    }

    @Test
    void shouldSkipSilentlyWhenFileIsAbsent() {
        MockEnvironment environment = new MockEnvironment();

        new DotenvLoader(tempDir.resolve("missing.env"))
                .postProcessEnvironment(environment, new SpringApplication());

        assertFalse(environment.getPropertySources().contains(DotenvLoader.SOURCE_NAME));
    }
}
