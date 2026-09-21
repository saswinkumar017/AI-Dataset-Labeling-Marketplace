package com.labelmate.labelmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelmate.labelmate.dto.AnnotationResponse;
import com.labelmate.labelmate.dto.AutoLabelRequest;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Annotation;
import com.labelmate.labelmate.model.AnnotationSource;
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
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.LabelRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import com.labelmate.labelmate.service.ai.LabelSuggestionService;
import com.labelmate.labelmate.service.ai.SuggestionResult;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AutoLabelServiceTest {

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

    @Mock
    private AnnotationService annotationService;

    @InjectMocks
    private AiSuggestionService aiSuggestionService;

    private User user(String email, long id) throws Exception {
        User user = new User(email, "User", "hashed", Role.ANNOTATOR, LocalDateTime.now());
        setId(user, id);
        return user;
    }

    private Task taskWithProject(User owner, TaskStatus status) throws Exception {
        Dataset dataset = new Dataset(owner, "Reviews", DatasetStatus.READY, LocalDateTime.now());
        Project project = new Project(dataset, owner, "Sentiment v1", ProjectStatus.DRAFT, LocalDateTime.now());
        setId(project, 10L);
        project.setInstructions("Pick the sentiment.");
        Task task = new Task(project, dataset, status, LocalDateTime.now());
        task.setItemData("I love it.");
        setId(task, 5L);
        return task;
    }

    private Label label(Project project, String name) throws Exception {
        Label label = new Label(project, name, LocalDateTime.now());
        setId(label, 7L);
        return label;
    }

    private void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @Test
    void shouldPersistAiAnnotationFromProjectScheme() throws Exception {
        User owner = user("owner@example.com", 1L);
        Task task = taskWithProject(owner, TaskStatus.PENDING);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(tasks.findById(5L)).thenReturn(Optional.of(task));
        when(labels.findByProjectIdOrderByNameAsc(10L))
                .thenReturn(List.of(label(task.getProject(), "Negative"), label(task.getProject(), "Positive")));
        when(suggestionService.suggest(eq("I love it."), any(), eq("Pick the sentiment.")))
                .thenReturn(new SuggestionResult("Positive", new BigDecimal("82"), "test-model"));
        AnnotationResponse saved = AnnotationResponse.from(
                new Annotation(task, owner, AnnotationSource.AI, LocalDateTime.now()));
        when(annotationService.createAi(eq(5L), eq("Positive"), any(), eq("owner@example.com")))
                .thenReturn(saved);

        AnnotationResponse response =
                aiSuggestionService.autoLabel(5L, new AutoLabelRequest(null), "owner@example.com");

        assertEquals(saved, response);
        verify(annotationService)
                .createAi(5L, "Positive", new BigDecimal("82"), "owner@example.com");
    }

    @Test
    void shouldPreferExplicitConfidenceOverride() throws Exception {
        User owner = user("owner@example.com", 1L);
        Task task = taskWithProject(owner, TaskStatus.PENDING);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(tasks.findById(5L)).thenReturn(Optional.of(task));
        when(labels.findByProjectIdOrderByNameAsc(10L))
                .thenReturn(List.of(label(task.getProject(), "Positive")));
        when(suggestionService.suggest(any(), any(), any()))
                .thenReturn(new SuggestionResult("Positive", new BigDecimal("82"), "test-model"));
        when(annotationService.createAi(eq(5L), eq("Positive"), any(), eq("owner@example.com")))
                .thenReturn(AnnotationResponse.from(
                        new Annotation(task, owner, AnnotationSource.AI, LocalDateTime.now())));

        aiSuggestionService.autoLabel(5L, new AutoLabelRequest(new BigDecimal("50")), "owner@example.com");

        verify(annotationService).createAi(5L, "Positive", new BigDecimal("50"), "owner@example.com");
    }

    @Test
    void shouldRejectAutoLabelForStrangers() throws Exception {
        User owner = user("owner@example.com", 1L);
        User stranger = user("zed@example.com", 3L);
        Task task = taskWithProject(owner, TaskStatus.PENDING);
        when(users.findByEmail("zed@example.com")).thenReturn(Optional.of(stranger));
        when(tasks.findById(5L)).thenReturn(Optional.of(task));

        ApiException ex = assertThrows(ApiException.class,
                () -> aiSuggestionService.autoLabel(5L, new AutoLabelRequest(null), "zed@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void shouldRejectAutoLabelWithoutLabels() throws Exception {
        User owner = user("owner@example.com", 1L);
        Task task = taskWithProject(owner, TaskStatus.PENDING);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(tasks.findById(5L)).thenReturn(Optional.of(task));
        when(labels.findByProjectIdOrderByNameAsc(10L)).thenReturn(List.of());

        ApiException ex = assertThrows(ApiException.class,
                () -> aiSuggestionService.autoLabel(5L, new AutoLabelRequest(null), "owner@example.com"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }
}
