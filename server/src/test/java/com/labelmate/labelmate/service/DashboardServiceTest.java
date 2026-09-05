package com.labelmate.labelmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.labelmate.labelmate.dto.DashboardSummary;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Annotation;
import com.labelmate.labelmate.model.AnnotationSource;
import com.labelmate.labelmate.model.Dataset;
import com.labelmate.labelmate.model.DatasetStatus;
import com.labelmate.labelmate.model.Project;
import com.labelmate.labelmate.model.ProjectStatus;
import com.labelmate.labelmate.model.ReviewDecision;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.Task;
import com.labelmate.labelmate.model.TaskStatus;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.DatasetRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DatasetRepository datasets;

    @Mock
    private ProjectRepository projects;

    @Mock
    private TaskRepository tasks;

    @Mock
    private AnnotationRepository annotations;

    @Mock
    private ReviewRepository reviews;

    @Mock
    private UserRepository users;

    @InjectMocks
    private DashboardService dashboardService;

    private User user(String email, long id) throws Exception {
        User user = new User(email, "User", "hashed", Role.ANNOTATOR, LocalDateTime.now());
        setId(user, id);
        return user;
    }

    private void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @Test
    void shouldSummarizeOwnedWorkflow() throws Exception {
        User owner = user("owner@example.com", 1L);
        Dataset dataset = new Dataset(owner, "Reviews", DatasetStatus.READY, LocalDateTime.now());
        Project project = new Project(dataset, owner, "Sentiment v1", ProjectStatus.DRAFT, LocalDateTime.now());
        setId(project, 10L);
        Task pending = new Task(project, dataset, TaskStatus.PENDING, LocalDateTime.now());
        Task submitted = new Task(project, dataset, TaskStatus.SUBMITTED, LocalDateTime.now());
        setId(submitted, 20L);
        Annotation annotation = new Annotation(submitted, owner, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent("Positive");
        setId(annotation, 30L);
        com.labelmate.labelmate.model.Review review = new com.labelmate.labelmate.model.Review(
                annotation, owner, ReviewDecision.APPROVED, LocalDateTime.now());

        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(datasets.findByOwnerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(dataset));
        when(projects.findByOwnerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(project));
        when(tasks.findByProjectIdOrderByItemIndexAscIdAsc(10L)).thenReturn(List.of(pending, submitted));
        when(annotations.findByProjectIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(annotation));
        when(reviews.findByProjectIdOrderByReviewedAtDesc(10L)).thenReturn(List.of(review));

        DashboardSummary summary = dashboardService.summarize("owner@example.com");

        assertEquals(1, summary.datasetCount());
        assertEquals(1, summary.projectCount());
        assertEquals(2, summary.taskCount());
        assertEquals(1, summary.tasksPending());
        assertEquals(1, summary.tasksSubmitted());
        assertEquals(1, summary.annotationCount());
        assertEquals(1, summary.reviewsApproved());
        assertEquals(0, summary.pendingReviews());
    }

    @Test
    void shouldCountUnreviewedAnnotationsAsPending() throws Exception {
        User owner = user("owner@example.com", 1L);
        Dataset dataset = new Dataset(owner, "Reviews", DatasetStatus.READY, LocalDateTime.now());
        Project project = new Project(dataset, owner, "Sentiment v1", ProjectStatus.DRAFT, LocalDateTime.now());
        setId(project, 10L);
        Task submitted = new Task(project, dataset, TaskStatus.SUBMITTED, LocalDateTime.now());
        Annotation annotation = new Annotation(submitted, owner, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent("Positive");
        setId(annotation, 30L);

        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(datasets.findByOwnerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(dataset));
        when(projects.findByOwnerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(project));
        when(tasks.findByProjectIdOrderByItemIndexAscIdAsc(10L)).thenReturn(List.of(submitted));
        when(annotations.findByProjectIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(annotation));
        when(reviews.findByProjectIdOrderByReviewedAtDesc(10L)).thenReturn(List.of());

        DashboardSummary summary = dashboardService.summarize("owner@example.com");

        assertEquals(1, summary.pendingReviews());
        assertEquals(1, summary.tasksSubmitted());
    }

    @Test
    void shouldReturnZerosWhenUserHasNoProjects() throws Exception {
        User owner = user("owner@example.com", 1L);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(datasets.findByOwnerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(projects.findByOwnerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        DashboardSummary summary = dashboardService.summarize("owner@example.com");

        assertEquals(0, summary.datasetCount());
        assertEquals(0, summary.projectCount());
        assertEquals(0, summary.taskCount());
        assertEquals(0, summary.pendingReviews());
    }

    @Test
    void shouldRejectAccessWhenUserIsUnknown() throws Exception {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class, () -> dashboardService.summarize("ghost@example.com"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }
    @Test
    void shouldCountRejectedWork() throws Exception {
        User owner = user("owner@example.com", 1L);
        Dataset dataset = new Dataset(owner, "Reviews", DatasetStatus.READY, LocalDateTime.now());
        Project project = new Project(dataset, owner, "Sentiment v1", ProjectStatus.DRAFT, LocalDateTime.now());
        setId(project, 10L);
        Task rejected = new Task(project, dataset, TaskStatus.REJECTED, LocalDateTime.now());
        Annotation annotation = new Annotation(rejected, owner, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent("Positive");
        setId(annotation, 30L);
        com.labelmate.labelmate.model.Review review = new com.labelmate.labelmate.model.Review(
                annotation, owner, ReviewDecision.REJECTED, LocalDateTime.now());

        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(datasets.findByOwnerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(dataset));
        when(projects.findByOwnerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(project));
        when(tasks.findByProjectIdOrderByItemIndexAscIdAsc(10L)).thenReturn(List.of(rejected));
        when(annotations.findByProjectIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(annotation));
        when(reviews.findByProjectIdOrderByReviewedAtDesc(10L)).thenReturn(List.of(review));

        DashboardSummary summary = dashboardService.summarize("owner@example.com");

        assertEquals(1, summary.tasksRejected());
        assertEquals(1, summary.reviewsRejected());
        assertEquals(0, summary.pendingReviews());
        assertEquals(0, summary.tasksApproved());
    }

}
