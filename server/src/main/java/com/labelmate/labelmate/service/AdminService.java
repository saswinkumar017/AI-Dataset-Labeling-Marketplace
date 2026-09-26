package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.AdminAiSettingsResponse;
import com.labelmate.labelmate.dto.AdminAiSettingsUpdateRequest;
import com.labelmate.labelmate.dto.AdminOverview;
import com.labelmate.labelmate.dto.UserResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.ReviewDecision;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.AiSuggestionRepository;
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.DatasetRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.ReviewRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.config.AiProperties;
import com.labelmate.labelmate.model.AppSetting;
import com.labelmate.labelmate.service.ai.AiClient;
import com.labelmate.labelmate.service.ai.AiSettingsResolver;
import com.labelmate.labelmate.service.ai.SpringAiClient;
import com.labelmate.labelmate.repository.AppSettingRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform administration.
 *
 * <p>Every method re-checks the caller's persisted role server-side: admin
 * pages are never gated by a request flag, a URL, or frontend state.
 * Non-admin callers receive 403 (they are authenticated, just not
 * administrators); strangers to the system never reach here because
 * authentication runs first. Role changes happen only here — public
 * registration always creates ANNOTATOR.
 */
@Service
public class AdminService {

    private final UserRepository users;
    private final DatasetRepository datasets;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final AnnotationRepository annotations;
    private final ReviewRepository reviews;
    private final AiSuggestionRepository suggestions;
    private final AppSettingRepository settings;
    private final AiProperties aiProperties;
    private final AiClient aiClient;

    public AdminService(
            UserRepository users,
            DatasetRepository datasets,
            ProjectRepository projects,
            TaskRepository tasks,
            AnnotationRepository annotations,
            ReviewRepository reviews,
            AiSuggestionRepository suggestions,
            AppSettingRepository settings,
            AiProperties aiProperties,
            AiClient aiClient) {
        this.users = users;
        this.datasets = datasets;
        this.projects = projects;
        this.tasks = tasks;
        this.annotations = annotations;
        this.reviews = reviews;
        this.suggestions = suggestions;
        this.settings = settings;
        this.aiProperties = aiProperties;
        this.aiClient = aiClient;
    }

    /**
     * Lists every account (identity and role only — never credentials).
     */
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(String callerEmail) {
        requireAdmin(callerEmail);
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    /**
     * Platform-wide workflow totals. Counts only — no user content, and no
     * fabricated metrics: each number is a direct repository count.
     */
    @Transactional(readOnly = true)
    public AdminOverview overview(String callerEmail) {
        requireAdmin(callerEmail);
        long approved = 0;
        long rejected = 0;
        for (com.labelmate.labelmate.model.Review review : reviews.findAll()) {
            if (review.getDecision() == ReviewDecision.APPROVED) {
                approved++;
            } else {
                rejected++;
            }
        }
        return new AdminOverview(
                users.count(),
                datasets.count(),
                projects.count(),
                tasks.count(),
                annotations.count(),
                approved + rejected,
                approved,
                rejected,
                suggestions.count());
    }

    private User requireAdmin(String callerEmail) {
        User caller = users.findByEmail(callerEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
        if (caller.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return caller;
    }

    /**
     * Changes a user's role. Only an existing admin can do this; public
     * registration never assigns ADMIN. Guards: no self-change, and the last
     * remaining admin cannot be demoted.
     */
    @Transactional
    public UserResponse updateUserRole(String callerEmail, Long targetId, Role role) {
        User caller = requireAdmin(callerEmail);
        if (role == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Role is required");
        }
        User target = users.findById(targetId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (target.getId().equals(caller.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot change your own role");
        }
        if (target.getRole() == Role.ADMIN && role != Role.ADMIN) {
            long adminCount = users.findAll().stream()
                    .filter(u -> u.getRole() == Role.ADMIN)
                    .count();
            if (adminCount <= 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot demote the last admin");
            }
        }
        target.setRole(role);
        return UserResponse.from(users.save(target));
    }

    /** AI settings for the admin panel — key is masked, never returned raw. */
    @Transactional(readOnly = true)
    public AdminAiSettingsResponse getAiSettings(String callerEmail) {
        requireAdmin(callerEmail);
        String key = setting(AiSettingsResolver.KEY_API_KEY, "");
        boolean enabled = Boolean.parseBoolean(
                setting(AiSettingsResolver.KEY_ENABLED, String.valueOf(aiProperties.isEnabled())));
        boolean beanActive = aiClient instanceof SpringAiClient;
        return new AdminAiSettingsResponse(
                AiSettingsResolver.mask(key),
                !key.isBlank(),
                setting(AiSettingsResolver.KEY_MODEL, aiProperties.getModel()),
                setting(AiSettingsResolver.KEY_BASE_URL, aiProperties.getBaseUrl()),
                enabled,
                setting(AiSettingsResolver.KEY_CHAT_MODEL, "openai"),
                beanActive,
                beanActive && enabled && !key.isBlank());
    }

    /**
     * Stores AI settings. A blank or masked (****) apiKey keeps the stored
     * key. Takes effect on backend restart (env still wins when set).
     */
    @Transactional
    public AdminAiSettingsResponse updateAiSettings(String callerEmail, AdminAiSettingsUpdateRequest request) {
        requireAdmin(callerEmail);
        if (request.apiKey() != null && !request.apiKey().isBlank() && !request.apiKey().startsWith("****")) {
            store(AiSettingsResolver.KEY_API_KEY, request.apiKey().trim());
        }
        if (request.model() != null && !request.model().isBlank()) {
            store(AiSettingsResolver.KEY_MODEL, request.model().trim());
        }
        if (request.baseUrl() != null && !request.baseUrl().isBlank()) {
            store(AiSettingsResolver.KEY_BASE_URL, request.baseUrl().trim());
        }
        if (request.enabled() != null) {
            store(AiSettingsResolver.KEY_ENABLED, String.valueOf(request.enabled()));
        }
        if (request.chatModel() != null && !request.chatModel().isBlank()) {
            store(AiSettingsResolver.KEY_CHAT_MODEL, request.chatModel().trim());
        }
        return getAiSettings(callerEmail);
    }

    private String setting(String key, String fallback) {
        return settings.findById(key).map(AppSetting::getValue).orElse(fallback);
    }

    private void store(String key, String value) {
        AppSetting row = settings.findById(key).orElseGet(() -> new AppSetting(key, value, java.time.LocalDateTime.now()));
        row.setValue(value);
        row.setUpdatedAt(java.time.LocalDateTime.now());
        settings.save(row);
    }
}
