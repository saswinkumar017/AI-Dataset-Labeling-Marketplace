package com.labelmate.labelmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelmate.labelmate.dto.AnnotationRequest;
import com.labelmate.labelmate.dto.AnnotationResponse;
import com.labelmate.labelmate.dto.AnnotationUpdateRequest;
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
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.LabelRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.ReviewRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.lang.reflect.Field;
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
class AnnotationServiceTest {

    @Mock
    private AnnotationRepository annotations;

    @Mock
    private TaskRepository tasks;

    @Mock
    private LabelRepository labels;

    @Mock
    private ProjectRepository projects;

    @Mock
    private ReviewRepository reviews;

    @Mock
    private UserRepository users;

    @InjectMocks
    private AnnotationService annotationService;

    private User user(String email, long id) throws Exception {
        User user = new User(email, "User", "hashed", Role.ANNOTATOR, LocalDateTime.now());
        setId(user, id);
        return user;
    }

    private Task task(User owner, TaskStatus status) throws Exception {
        Dataset dataset = new Dataset(owner, "Reviews", DatasetStatus.READY, LocalDateTime.now());
        Project project = new Project(dataset, owner, "Sentiment v1", ProjectStatus.DRAFT, LocalDateTime.now());
        setId(project, 10L);
        Task task = new Task(project, dataset, status, LocalDateTime.now());
        setId(task, 20L);
        return task;
    }

    private void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @Test
    void shouldCreateAnnotationWhenRequestIsValid() throws Exception {
        User owner = user("owner@example.com", 1L);
        Task task = task(owner, TaskStatus.PENDING);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(tasks.findById(20L)).thenReturn(Optional.of(task));
        when(labels.findByProjectIdAndName(10L, "Positive")).thenReturn(Optional.empty());
        when(annotations.save(any(Annotation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AnnotationResponse response =
                annotationService.create(new AnnotationRequest(20L, "Positive"), "owner@example.com");

        assertEquals("Positive", response.label());
        assertEquals(AnnotationSource.HUMAN, response.source());
        assertEquals(20L, response.taskId());
        assertEquals(10L, response.projectId());

        ArgumentCaptor<Annotation> saved = ArgumentCaptor.forClass(Annotation.class);
        verify(annotations).save(saved.capture());
        assertEquals("Positive", saved.getValue().getContent());
        assertEquals(owner, saved.getValue().getAnnotator());
        verify(tasks).save(task);
        assertEquals(TaskStatus.SUBMITTED, task.getStatus());
    }

    @Test
    void shouldReturnNotFoundWhenTaskDoesNotExist() throws Exception {
        User owner = user("owner@example.com", 1L);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(tasks.findById(99L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> annotationService.create(new AnnotationRequest(99L, "Positive"), "owner@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(annotations, never()).save(any(Annotation.class));
    }

    @Test
    void shouldRejectCreationWhenTaskBelongsToAnotherUser() throws Exception {
        User owner = user("owner@example.com", 1L);
        User other = user("other@example.com", 2L);
        Task foreign = task(other, TaskStatus.PENDING);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(tasks.findById(20L)).thenReturn(Optional.of(foreign));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> annotationService.create(new AnnotationRequest(20L, "Positive"), "owner@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(annotations, never()).save(any(Annotation.class));
    }

    @Test
    void shouldRejectAccessWhenUserIsUnknown() throws Exception {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> annotationService.create(new AnnotationRequest(20L, "Positive"), "ghost@example.com"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void shouldReturnAnnotationWhenOwnerMatches() throws Exception {
        User owner = user("owner@example.com", 1L);
        Task task = task(owner, TaskStatus.SUBMITTED);
        Annotation annotation = new Annotation(task, owner, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent("Positive");
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(annotations.findById(5L)).thenReturn(Optional.of(annotation));

        AnnotationResponse response = annotationService.getByIdForUser(5L, "owner@example.com");

        assertEquals("Positive", response.label());
        assertEquals(AnnotationSource.HUMAN, response.source());
    }

    @Test
    void shouldReturnNotFoundWhenAnnotationDoesNotExist() throws Exception {
        User owner = user("owner@example.com", 1L);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(annotations.findById(99L)).thenReturn(Optional.empty());

        ApiException ex =
                assertThrows(ApiException.class, () -> annotationService.getByIdForUser(99L, "owner@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void shouldRejectGetWhenAnnotationBelongsToAnotherUser() throws Exception {
        User owner = user("owner@example.com", 1L);
        User other = user("other@example.com", 2L);
        Task foreign = task(other, TaskStatus.SUBMITTED);
        Annotation annotation = new Annotation(foreign, other, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent("Positive");
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(annotations.findById(5L)).thenReturn(Optional.of(annotation));

        ApiException ex =
                assertThrows(ApiException.class, () -> annotationService.getByIdForUser(5L, "owner@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void shouldRejectCreationWhenTaskIsAlreadyApproved() throws Exception {
        User owner = user("owner@example.com", 1L);
        Task task = task(owner, TaskStatus.APPROVED);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(tasks.findById(20L)).thenReturn(Optional.of(task));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> annotationService.create(new AnnotationRequest(20L, "Positive"), "owner@example.com"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(annotations, never()).save(any(Annotation.class));
    }

    @Test
    void shouldUpdateAnnotationWhenOwnerMatches() throws Exception {
        User owner = user("owner@example.com", 1L);
        Task task = task(owner, TaskStatus.SUBMITTED);
        Annotation annotation = new Annotation(task, owner, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent("Positive");
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(annotations.findById(5L)).thenReturn(Optional.of(annotation));
        when(labels.findByProjectIdAndName(10L, "Negative")).thenReturn(Optional.empty());
        when(annotations.save(any(Annotation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AnnotationResponse response =
                annotationService.update(5L, new AnnotationUpdateRequest("Negative"), "owner@example.com");

        assertEquals("Negative", response.label());
        verify(annotations).save(annotation);
    }

    @Test
    void shouldRejectUpdateWhenTaskIsAlreadyApproved() throws Exception {
        User owner = user("owner@example.com", 1L);
        Task task = task(owner, TaskStatus.APPROVED);
        Annotation annotation = new Annotation(task, owner, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent("Positive");
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(annotations.findById(5L)).thenReturn(Optional.of(annotation));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> annotationService.update(5L, new AnnotationUpdateRequest("Negative"), "owner@example.com"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(annotations, never()).save(any(Annotation.class));
    }

    @Test
    void shouldRejectUpdateWhenAnnotationBelongsToAnotherUser() throws Exception {
        User owner = user("owner@example.com", 1L);
        User other = user("other@example.com", 2L);
        Task foreign = task(other, TaskStatus.SUBMITTED);
        Annotation annotation = new Annotation(foreign, other, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent("Positive");
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(annotations.findById(5L)).thenReturn(Optional.of(annotation));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> annotationService.update(5L, new AnnotationUpdateRequest("Negative"), "owner@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(annotations, never()).save(any(Annotation.class));
    }

    @Test
    void shouldDeleteAnnotationAndReopenTaskWhenItWasTheLastOne() throws Exception {
        User owner = user("owner@example.com", 1L);
        Task task = task(owner, TaskStatus.SUBMITTED);
        Annotation annotation = new Annotation(task, owner, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent("Positive");
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(annotations.findById(5L)).thenReturn(Optional.of(annotation));
        when(annotations.findByTaskIdOrderByCreatedAtDesc(20L)).thenReturn(List.of());

        annotationService.delete(5L, "owner@example.com");

        verify(annotations).delete(annotation);
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
        verify(tasks).save(task);
    }

    @Test
    void shouldRejectDeleteWhenAnnotationHasBeenReviewed() throws Exception {
        User owner = user("owner@example.com", 1L);
        Task task = task(owner, TaskStatus.SUBMITTED);
        Annotation annotation = new Annotation(task, owner, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent("Positive");
        setId(annotation, 5L);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(annotations.findById(5L)).thenReturn(Optional.of(annotation));
        when(reviews.existsByAnnotationId(5L)).thenReturn(true);

        ApiException ex = assertThrows(
                ApiException.class, () -> annotationService.delete(5L, "owner@example.com"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(annotations, never()).delete(any(Annotation.class));
    }

    @Test
    void shouldRejectDeleteWhenAnnotationBelongsToAnotherUser() throws Exception {
        User owner = user("owner@example.com", 1L);
        User other = user("other@example.com", 2L);
        Task foreign = task(other, TaskStatus.SUBMITTED);
        Annotation annotation = new Annotation(foreign, other, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent("Positive");
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(annotations.findById(5L)).thenReturn(Optional.of(annotation));

        ApiException ex = assertThrows(
                ApiException.class, () -> annotationService.delete(5L, "owner@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(annotations, never()).delete(any(Annotation.class));
    }

    @Test
    void shouldListAnnotationsForOwnedProject() throws Exception {
        User owner = user("owner@example.com", 1L);
        Task task = task(owner, TaskStatus.SUBMITTED);
        Project project = task.getProject();
        Annotation annotation = new Annotation(task, owner, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent("Positive");
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projects.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(project));
        when(annotations.findByProjectIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(annotation));

        List<AnnotationResponse> result = annotationService.listByProject(10L, "owner@example.com");

        assertEquals(1, result.size());
        assertEquals("Positive", result.get(0).label());
        verify(annotations).findByProjectIdOrderByCreatedAtDesc(10L);
    }

    @Test
    void shouldRejectProjectListingWhenProjectBelongsToAnotherUser() throws Exception {
        User owner = user("owner@example.com", 1L);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projects.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class, () -> annotationService.listByProject(10L, "owner@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(annotations, never()).findByProjectIdOrderByCreatedAtDesc(any());
    }

    @Test
    void shouldAllowAdminToReadProjectAnnotations() throws Exception {
        User owner = user("owner@example.com", 1L);
        User admin = user("admin@example.com", 2L);
        admin.setRole(Role.ADMIN);
        Task task = task(owner, TaskStatus.SUBMITTED);
        Project project = task.getProject();
        Annotation annotation = new Annotation(task, owner, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent("Positive");
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(annotations.findByProjectIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(annotation));

        List<AnnotationResponse> result = annotationService.listByProject(10L, "admin@example.com");

        assertEquals(1, result.size());
        assertEquals("Positive", result.get(0).label());
    }

    @Test
    void shouldRejectAnnotationCreationByAdminOnForeignProject() throws Exception {
        User owner = user("owner@example.com", 1L);
        User admin = user("admin@example.com", 2L);
        admin.setRole(Role.ADMIN);
        Task task = task(owner, TaskStatus.PENDING);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(tasks.findById(20L)).thenReturn(Optional.of(task));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> annotationService.create(new AnnotationRequest(20L, "Admin label"), "admin@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(annotations, never()).save(any(Annotation.class));
    }

    @Test
    void shouldListAnnotationsForOwnedTask() throws Exception {
        User owner = user("owner@example.com", 1L);
        Task task = task(owner, TaskStatus.SUBMITTED);
        Annotation annotation = new Annotation(task, owner, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent("Positive");
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(tasks.findById(20L)).thenReturn(Optional.of(task));
        when(annotations.findByTaskIdOrderByCreatedAtDesc(20L)).thenReturn(List.of(annotation));

        List<AnnotationResponse> result = annotationService.listByTask(20L, "owner@example.com");

        assertEquals(1, result.size());
        assertEquals("Positive", result.get(0).label());
        verify(annotations).findByTaskIdOrderByCreatedAtDesc(20L);
    }
    @Test
    void shouldLinkExistingLabelOnCreate() throws Exception {
        User owner = user("owner@example.com", 1L);
        Task task = task(owner, TaskStatus.PENDING);
        Label positive = new Label(task.getProject(), "Positive", LocalDateTime.now());
        setId(positive, 50L);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(tasks.findById(20L)).thenReturn(Optional.of(task));
        when(labels.findByProjectIdAndName(10L, "Positive")).thenReturn(Optional.of(positive));
        when(annotations.save(any(Annotation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AnnotationResponse response =
                annotationService.create(new AnnotationRequest(20L, "Positive"), "owner@example.com");

        assertEquals("Positive", response.label());
        assertEquals(50L, response.labelId());
    }

    @Test
    void shouldRelinkLabelOnUpdate() throws Exception {
        User owner = user("owner@example.com", 1L);
        Task task = task(owner, TaskStatus.SUBMITTED);
        Label oldLabel = new Label(task.getProject(), "Old", LocalDateTime.now());
        Label newLabel = new Label(task.getProject(), "New", LocalDateTime.now());
        setId(newLabel, 51L);
        Annotation annotation = new Annotation(task, owner, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent("Old");
        annotation.setLabel(oldLabel);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(annotations.findById(5L)).thenReturn(Optional.of(annotation));
        when(labels.findByProjectIdAndName(10L, "New")).thenReturn(Optional.of(newLabel));
        when(annotations.save(any(Annotation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AnnotationResponse response =
                annotationService.update(5L, new AnnotationUpdateRequest("New"), "owner@example.com");

        assertEquals("New", response.label());
        assertEquals(51L, response.labelId());
    }

    @Test
    void shouldKeepTaskSubmittedWhenSiblingsRemainOnDelete() throws Exception {
        User owner = user("owner@example.com", 1L);
        Task task = task(owner, TaskStatus.SUBMITTED);
        Annotation annotation = new Annotation(task, owner, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent("Positive");
        Annotation sibling = new Annotation(task, owner, AnnotationSource.HUMAN, LocalDateTime.now());
        sibling.setContent("Negative");
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(annotations.findById(5L)).thenReturn(Optional.of(annotation));
        when(reviews.existsByAnnotationId(any())).thenReturn(false);
        when(annotations.findByTaskIdOrderByCreatedAtDesc(20L)).thenReturn(List.of(sibling));

        annotationService.delete(5L, "owner@example.com");

        verify(annotations).delete(annotation);
        assertEquals(TaskStatus.SUBMITTED, task.getStatus());
        verify(tasks, never()).save(any(Task.class));
    }

}
