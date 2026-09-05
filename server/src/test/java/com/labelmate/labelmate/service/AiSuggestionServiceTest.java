package com.labelmate.labelmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelmate.labelmate.dto.SuggestRequest;
import com.labelmate.labelmate.dto.SuggestionResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Dataset;
import com.labelmate.labelmate.model.DatasetStatus;
import com.labelmate.labelmate.model.Label;
import com.labelmate.labelmate.model.Project;
import com.labelmate.labelmate.model.ProjectStatus;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.Task;
import com.labelmate.labelmate.model.TaskStatus;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.AiSuggestionRepository;
import com.labelmate.labelmate.repository.LabelRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import com.labelmate.labelmate.service.ai.AiException;
import com.labelmate.labelmate.service.ai.LabelSuggestionService;
import com.labelmate.labelmate.service.ai.SuggestionResult;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AiSuggestionServiceTest {

    @Mock
    private AiSuggestionRepository suggestions;

    @Mock
    private TaskRepository tasks;

    @Mock
    private LabelRepository labels;

    @Mock
    private ProjectRepository projects;

    @Mock
    private UserRepository users;

    @Mock
    private LabelSuggestionService suggestionService;

    @InjectMocks
    private AiSuggestionService aiSuggestionService;

    private User user(String email, long id, Role role) throws Exception {
        User user = new User(email, "User", "hashed", role, LocalDateTime.now());
        setId(user, id);
        return user;
    }

    private Task task(User owner, String itemData) throws Exception {
        Dataset dataset = new Dataset(owner, "Reviews", DatasetStatus.READY, LocalDateTime.now());
        Project project = new Project(dataset, owner, "Sentiment v1", ProjectStatus.DRAFT, LocalDateTime.now());
        project.setInstructions("Pick sentiment.");
        setId(project, 10L);
        Task task = new Task(project, dataset, TaskStatus.PENDING, LocalDateTime.now());
        task.setItemData(itemData);
        setId(task, 20L);
        return task;
    }

    private void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @Test
    void shouldPersistSuggestionAndLinkKnownLabel() throws Exception {
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        Task task = task(owner, "I love it.");
        Label positive = new Label(task.getProject(), "Positive", LocalDateTime.now());
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(tasks.findById(20L)).thenReturn(Optional.of(task));
        when(suggestionService.suggest("I love it.", List.of("Positive", "Negative"), "Pick sentiment."))
                .thenReturn(new SuggestionResult("Positive", new BigDecimal("87.00"), "openrouter/auto"));
        when(labels.findByProjectIdAndName(10L, "Positive")).thenReturn(Optional.of(positive));
        when(suggestions.save(any(com.labelmate.labelmate.model.AiSuggestion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SuggestionResponse response = aiSuggestionService.suggest(
                20L, new SuggestRequest(List.of("Positive", "Negative")), "owner@example.com");

        assertEquals("Positive", response.suggestedLabel());
        assertEquals(new BigDecimal("87.00"), response.confidence());
        assertEquals(20L, response.taskId());

        ArgumentCaptor<com.labelmate.labelmate.model.AiSuggestion> saved =
                ArgumentCaptor.forClass(com.labelmate.labelmate.model.AiSuggestion.class);
        verify(suggestions).save(saved.capture());
        assertEquals(positive, saved.getValue().getSuggestedLabel());
        assertEquals("Positive", saved.getValue().getSuggestedLabelName());
        assertEquals("openrouter/auto", saved.getValue().getModel());
    }

    @Test
    void shouldStoreNameWithoutLinkWhenLabelIsUnknown() throws Exception {
        User admin = user("admin@example.com", 2L, Role.ADMIN);
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        Task task = task(owner, "Meh.");
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(tasks.findById(20L)).thenReturn(Optional.of(task));
        when(suggestionService.suggest("Meh.", List.of("Positive"), "Pick sentiment."))
                .thenReturn(new SuggestionResult("Positive", null, "openrouter/auto"));
        when(labels.findByProjectIdAndName(10L, "Positive")).thenReturn(Optional.empty());
        when(suggestions.save(any(com.labelmate.labelmate.model.AiSuggestion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SuggestionResponse response = aiSuggestionService.suggest(
                20L, new SuggestRequest(List.of("Positive")), "admin@example.com");

        assertEquals("Positive", response.suggestedLabel());
        assertNull(response.confidence());

        ArgumentCaptor<com.labelmate.labelmate.model.AiSuggestion> saved =
                ArgumentCaptor.forClass(com.labelmate.labelmate.model.AiSuggestion.class);
        verify(suggestions).save(saved.capture());
        assertNull(saved.getValue().getSuggestedLabel());
        assertEquals("Positive", saved.getValue().getSuggestedLabelName());
    }

    @Test
    void shouldRejectTaskWithoutText() throws Exception {
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        Task task = task(owner, "  ");
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(tasks.findById(20L)).thenReturn(Optional.of(task));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> aiSuggestionService.suggest(
                        20L, new SuggestRequest(List.of("Positive")), "owner@example.com"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(suggestions, never()).save(any(com.labelmate.labelmate.model.AiSuggestion.class));
    }

    @Test
    void shouldHideForeignTasks() throws Exception {
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        User stranger = user("stranger@example.com", 3L, Role.ANNOTATOR);
        Task task = task(owner, "I love it.");
        when(users.findByEmail("stranger@example.com")).thenReturn(Optional.of(stranger));
        when(tasks.findById(20L)).thenReturn(Optional.of(task));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> aiSuggestionService.suggest(
                        20L, new SuggestRequest(List.of("Positive")), "stranger@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(suggestions, never()).save(any(com.labelmate.labelmate.model.AiSuggestion.class));
    }

    @Test
    void shouldPropagateAiFailureWithoutPersisting() throws Exception {
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        Task task = task(owner, "I love it.");
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(tasks.findById(20L)).thenReturn(Optional.of(task));
        when(suggestionService.suggest("I love it.", List.of("Positive"), "Pick sentiment."))
                .thenThrow(new AiException(AiException.Reason.PROVIDER_ERROR, "down"));

        AiException ex = assertThrows(
                AiException.class,
                () -> aiSuggestionService.suggest(
                        20L, new SuggestRequest(List.of("Positive")), "owner@example.com"));

        assertEquals(AiException.Reason.PROVIDER_ERROR, ex.getReason());
        verify(suggestions, never()).save(any(com.labelmate.labelmate.model.AiSuggestion.class));
    }

    @Test
    void shouldRejectAccessWhenUserIsUnknown() throws Exception {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> aiSuggestionService.suggest(
                        20L, new SuggestRequest(List.of("Positive")), "ghost@example.com"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }
    @Test
    void shouldSkipBlankCandidateLabels() throws Exception {
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        Task task = task(owner, "I love it.");
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(tasks.findById(20L)).thenReturn(Optional.of(task));
        when(suggestionService.suggest("I love it.", List.of("Positive"), "Pick sentiment."))
                .thenReturn(new SuggestionResult("Positive", null, "openrouter/auto"));
        when(labels.findByProjectIdAndName(10L, "Positive")).thenReturn(Optional.empty());
        when(suggestions.save(any(com.labelmate.labelmate.model.AiSuggestion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SuggestionResponse response = aiSuggestionService.suggest(
                20L, new SuggestRequest(List.of("  ", "Positive", "")), "owner@example.com");

        assertEquals("Positive", response.suggestedLabel());
        verify(suggestionService).suggest("I love it.", List.of("Positive"), "Pick sentiment.");
    }

}
