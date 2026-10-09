package com.labelmate.labelmate.service.ai;

import com.labelmate.labelmate.config.AiProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns one dataset item into a structured label suggestion.
 *
 * <p>The model only ever sees what it needs: the project's label scheme,
 * the annotator instructions, and the single item text (truncated to a
 * configured bound). Its free-text answer is parsed strictly — the first
 * line must name exactly one of the given labels, otherwise the whole
 * suggestion is rejected instead of guessing. Confidence is accepted only
 * when the model supplies a usable 0–100 number; it is never fabricated.
 * Every provider problem surfaces as {@link AiException} so callers can
 * fall back to manual annotation.
 */
@Service
public class LabelSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(LabelSuggestionService.class);

    private final AiClient aiClient;
    private final AiProperties properties;
    private final AiSettingsResolver resolver;

    public LabelSuggestionService(AiClient aiClient, AiProperties properties) {
        this(aiClient, properties, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LabelSuggestionService(AiClient aiClient, AiProperties properties, AiSettingsResolver resolver) {
        this.aiClient = aiClient;
        this.properties = properties;
        this.resolver = resolver;
    }

    /**
     * Suggests one label for a single item.
     *
     * @param itemText the dataset item to classify, never blank
     * @param labels the project's allowed labels, never empty
     * @param instructions annotator instructions, may be null
     * @return the structured suggestion; confidence null when unusable
     * @throws IllegalArgumentException for blank items or empty label schemes
     * @throws AiException for every AI-side failure (callers fall back to manual)
     */
    public SuggestionResult suggest(String itemText, List<String> labels, String instructions) {
        if (itemText == null || itemText.isBlank()) {
            throw new IllegalArgumentException("itemText must not be blank");
        }
        List<String> scheme = labels == null
                ? List.of()
                : labels.stream().filter(label -> label != null && !label.isBlank()).toList();
        if (scheme.isEmpty()) {
            throw new IllegalArgumentException("labels must not be empty");
        }
        String output = aiClient.complete(buildSystemPrompt(scheme, instructions), fit(itemText));
        return parse(output, scheme);
    }

    private String buildSystemPrompt(List<String> scheme, String instructions) {
        StringBuilder prompt = new StringBuilder(
                "You classify one dataset item. Reply with exactly two lines and nothing else: "
                        + "line 1 is the single best label copied exactly as written below "
                        + "(plain text, no brackets, no quotes, no numbering), "
                        + "line 2 is your confidence as a number from 0 to 100 "
                        + "(write ONLY the number; omit line 2 if you cannot judge confidence).");
        if (instructions != null && !instructions.isBlank()) {
            prompt.append(" Project guidance: ").append(instructions.strip());
        }
        prompt.append(" Labels: ");
        for (int i = 0; i < scheme.size(); i++) {
            if (i > 0) {
                prompt.append(", ");
            }
            prompt.append(scheme.get(i).strip());
        }
        return prompt.toString();
    }

    private String fit(String itemText) {
        String text = itemText.strip();
        int max = Math.max(1, properties.getMaxPromptChars());
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n[truncated for AI; the annotator still sees the full item]";
    }

    private SuggestionResult parse(String output, List<String> scheme) {
        if (output == null || output.isBlank()) {
            throw new AiException(AiException.Reason.INVALID_RESPONSE, "AI returned an empty response");
        }
        String[] lines = output.strip().split("\\R");
        // Strict path: the first line names exactly one of the given labels.
        String matched = matchScheme(lines[0].strip(), scheme);
        String confidenceLine = lines.length > 1 ? lines[1] : null;
        if (matched == null) {
            // Tolerant path: some models (notably auto-routed reasoning
            // models) wrap the answer in prose instead of the bare two-line
            // format — accept a line that IS exactly one scheme label.
            // Only whole-line exact matches count, never substrings, so a
            // sentence merely mentioning a label is still rejected.
            for (int i = 1; i < lines.length; i++) {
                String candidate = matchScheme(lines[i].strip(), scheme);
                if (candidate != null) {
                    matched = candidate;
                    confidenceLine = i + 1 < lines.length ? lines[i + 1] : null;
                    break;
                }
            }
        }
        if (matched == null) {
            throw new AiException(
                    AiException.Reason.INVALID_RESPONSE,
                    "AI suggested a label outside the project scheme");
        }
        BigDecimal confidence = confidenceLine != null ? parseConfidence(confidenceLine) : null;
        log.info("AI suggestion parsed (confidence {})", confidence == null ? "absent" : confidence);
        return new SuggestionResult(matched, confidence, resolver != null ? resolver.model() : properties.getModel());
    }

    private String matchScheme(String candidate, List<String> scheme) {
        String clean = unwrap(candidate);
        return scheme.stream()
                .filter(label -> label.equalsIgnoreCase(clean))
                .findFirst()
                .orElse(null);
    }

    /**
     * Removes decorative wrapping models add around the label
     * ({@code [High]}, {@code "High"}, {@code **High**}, {@code 1. High}):
     * the scheme match stays exact on the inner text, so a sentence merely
     * mentioning a label still never matches.
     */
    private String unwrap(String candidate) {
        String text = candidate == null ? "" : candidate.strip();
        boolean changed = true;
        while (changed && text.length() >= 2) {
            changed = false;
            if ((text.startsWith("[") && text.endsWith("]")
                    || text.startsWith("\"") && text.endsWith("\"")
                    || text.startsWith("'") && text.endsWith("'")
                    || text.startsWith("**") && text.endsWith("**") && text.length() >= 4)) {
                text = text.substring(text.startsWith("**") ? 2 : 1,
                        text.length() - (text.endsWith("**") ? 2 : 1)).strip();
                changed = true;
            }
        }
        return text.replaceFirst("^[0-9]+[.)]\\s+", "");
    }

    private BigDecimal parseConfidence(String line) {
        String number = line.strip().replace("%", "").strip();
        try {
            BigDecimal value = new BigDecimal(number).setScale(2, RoundingMode.HALF_UP);
            if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
