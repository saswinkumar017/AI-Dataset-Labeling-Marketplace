package com.labelmate.labelmate.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Loads the local {@code server/.env} dotenv file into the Spring
 * environment so {@code mvn spring-boot:run} works without exporting
 * variables by hand.
 *
 * <p>Why this class exists: {@code spring.config.import=optional:file:.env}
 * does <em>not</em> work — Spring Boot only loads imports with a known
 * config extension ({@code .properties/.yaml/.yml}), and the extensionless
 * dotenv name matches no loader. With {@code optional:} that skip is
 * silent, and the boot then dies later with a cryptic
 * {@code Could not resolve placeholder 'JWT_SECRET'}. Loading the file here
 * keeps the familiar dotenv convention without new dependencies.
 *
 * <p>Rules, in order:
 * <ul>
 *   <li>Skipped silently when no {@code .env} sits next to the working
 *   directory — containers and CI pass real environment variables instead.
 *   <li>Added <em>last</em>, so OS environment, system properties, CLI
 *   arguments, and {@code application.properties} always win over the file.
 *   <li>Only the simple dotenv shape is supported: {@code KEY=value}
 *   lines, {@code #} comments, an optional {@code export} prefix, and one
 *   pair of matching surrounding quotes. Anything else is taken literally,
 *   exactly like a {@code .properties} file.
 * </ul>
 */
public class DotenvLoader implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "dotenv";
    static final String LOCATION = ".env";

    private static final DeferredLog log = new DeferredLog();

    private final Path location;

    /** Instantiated by Spring via the imports registration file. */
    public DotenvLoader() {
        this(Paths.get(LOCATION));
    }

    DotenvLoader(Path location) {
        this.location = location;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path file = location.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            log.debug("No local .env file at " + file + " — continuing with real environment variables");
            return;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                parseLine(line, values);
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Cannot read local .env file at " + file + " (see server/.env.example)", ex);
        }
        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, values));
        log.info("Loaded " + values.size() + " variables from local .env file at " + file);
    }

    private void parseLine(String line, Map<String, Object> values) {
        String text = line == null ? "" : line.strip();
        if (text.isEmpty() || text.startsWith("#")) {
            return;
        }
        if (text.startsWith("export ")) {
            text = text.substring("export ".length()).strip();
        }
        int separator = text.indexOf('=');
        if (separator <= 0) {
            return;
        }
        String key = text.substring(0, separator).strip();
        String value = text.substring(separator + 1).strip();
        if (key.isEmpty()) {
            return;
        }
        values.put(key, unquote(value));
    }

    private String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
