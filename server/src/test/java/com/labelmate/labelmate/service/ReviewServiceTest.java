package com.labelmate.labelmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelmate.labelmate.dto.ReviewRequest;
import com.labelmate.labelmate.dto.ReviewResponse;
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
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviews;

    @Mock
    private AnnotationRepository annotations;

    @Mock
    private TaskRepository tasks;

    @Mock
    private ProjectRepository projects;

    @Mock
    private UserRepository users;

    @InjectMocks
    private ReviewService reviewService;

    private User user(String email, long id, Role role) throws Exception {
        User user = new User(email, "User", "hashed", role, LocalDateTime.now());
        setId(user, id);
        return user;
    }

    private Annotation annotation(User owner, User annotator, TaskStatus status) throws Exception {
        Dataset dataset = new Dataset(owner, "Reviews", DatasetStatus.READY, LocalDateTime.now());
        Project project = new Project(dataset, owner, "Sentiment v1", ProjectStatus.DRAFT, LocalDateTime.now());
        setId(project, 10L);
        Task task = new Task(project, dataset, status, LocalDateTime.now());
        setId(task, 20L);
        Annotation annotation = new Annotation(task, annotator, AnnotationSource.HUMAN, LocalDateTime.now());
        annotation.setContent("Positive");
        setId(annotation, 30L);
        return annotation;
    }

    private void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @Test
    void shouldApproveAnnotationWhenReviewerIsAdmin() throws Exception {
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        User admin = user("admin@example.com", 2L, Role.ADMIN);
        Annotation annotation = annotation(owner, owner, TaskStatus.SUBMITTED);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(annotations.findById(30L)).thenReturn(Optional.of(annotation));
        when(reviews.existsByAnnotationId(30L)).thenReturn(false);
        when(reviews.save(any(com.labelmate.labelmate.model.Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponse response = reviewService.submit(
                30L, new ReviewRequest("APPROVED", "Looks good"), "admin@example.com");

        assertEquals(ReviewDecision.APPROVED, response.decision());
        assertEquals(30L, response.annotationId());
        assertEquals(20L, response.taskId());
        assertEquals(10L, response.projectId());

        ArgumentCaptor<com.labelmate.labelmate.model.Review> saved =
                ArgumentCaptor.forClass(com.labelmate.labelmate.model.Review.class);
        verify(reviews).save(saved.capture());
        assertEquals(ReviewDecision.APPROVED, saved.getValue().getDecision());
        assertEquals(admin, saved.getValue().getReviewer());

        assertEquals(AnnotationSource.HUMAN_APPROVED, annotation.getSource());
        assertEquals(TaskStatus.APPROVED, annotation.getTask().getStatus());
        verify(annotations).save(annotation);
        verify(tasks).save(annotation.getTask());
    }

    @Test
    void shouldRejectAnnotationAndReopenTask() throws Exception {
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        User admin = user("admin@example.com", 2L, Role.ADMIN);
        Annotation annotation = annotation(owner, owner, TaskStatus.SUBMITTED);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(annotations.findById(30L)).thenReturn(Optional.of(annotation));
        when(reviews.existsByAnnotationId(30L)).thenReturn(false);
        when(reviews.save(any(com.labelmate.labelmate.model.Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponse response = reviewService.submit(
                30L, new ReviewRequest("REJECTED", "Wrong sentiment"), "admin@example.com");

        assertEquals(ReviewDecision.REJECTED, response.decision());
        assertEquals(AnnotationSource.HUMAN, annotation.getSource());
        assertEquals(TaskStatus.REJECTED, annotation.getTask().getStatus());
        verify(tasks).save(annotation.getTask());
        verify(annotations, never()).save(any(Annotation.class));
    }

    @Test
    void shouldForbidSelfReview() throws Exception {
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        Annotation annotation = annotation(owner, owner, TaskStatus.SUBMITTED);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(annotations.findById(30L)).thenReturn(Optional.of(annotation));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> reviewService.submit(30L, new ReviewRequest("APPROVED", null), "owner@example.com"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(reviews, never()).save(any(com.labelmate.labelmate.model.Review.class));
    }

    @Test
    void shouldRejectDuplicateReview() throws Exception {
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        User admin = user("admin@example.com", 2L, Role.ADMIN);
        Annotation annotation = annotation(owner, owner, TaskStatus.SUBMITTED);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(annotations.findById(30L)).thenReturn(Optional.of(annotation));
        when(reviews.existsByAnnotationId(30L)).thenReturn(true);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> reviewService.submit(30L, new ReviewRequest("APPROVED", null), "admin@example.com"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(reviews, never()).save(any(com.labelmate.labelmate.model.Review.class));
    }

    @Test
    void shouldReturnNotFoundWhenAnnotationDoesNotExist() throws Exception {
        User admin = user("admin@example.com", 2L, Role.ADMIN);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(annotations.findById(99L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> reviewService.submit(99L, new ReviewRequest("APPROVED", null), "admin@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void shouldReturnNotFoundWhenStrangerReviews() throws Exception {
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        User stranger = user("stranger@example.com", 3L, Role.ANNOTATOR);
        Annotation annotation = annotation(owner, owner, TaskStatus.SUBMITTED);
        when(users.findByEmail("stranger@example.com")).thenReturn(Optional.of(stranger));
        when(annotations.findById(30L)).thenReturn(Optional.of(annotation));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> reviewService.submit(30L, new ReviewRequest("APPROVED", null), "stranger@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(reviews, never()).save(any(com.labelmate.labelmate.model.Review.class));
    }

    @Test
    void shouldRejectAccessWhenUserIsUnknown() throws Exception {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> reviewService.submit(30L, new ReviewRequest("APPROVED", null), "ghost@example.com"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void shouldRejectInvalidDecision() throws Exception {
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        User admin = user("admin@example.com", 2L, Role.ADMIN);
        Annotation annotation = annotation(owner, owner, TaskStatus.SUBMITTED);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(annotations.findById(30L)).thenReturn(Optional.of(annotation));
        when(reviews.existsByAnnotationId(30L)).thenReturn(false);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> reviewService.submit(30L, new ReviewRequest("MAYBE", null), "admin@example.com"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(reviews, never()).save(any(com.labelmate.labelmate.model.Review.class));
    }

    @Test
    void shouldListReviewsForOwnedProject() throws Exception {
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        User admin = user("admin@example.com", 2L, Role.ADMIN);
        Annotation annotation = annotation(owner, owner, TaskStatus.APPROVED);
        com.labelmate.labelmate.model.Review review = new com.labelmate.labelmate.model.Review(
                annotation, admin, ReviewDecision.APPROVED, LocalDateTime.now());
        Project project = annotation.getTask().getProject();
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projects.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(project));
        when(reviews.findByProjectIdOrderByReviewedAtDesc(10L)).thenReturn(List.of(review));

        List<ReviewResponse> result = reviewService.listByProject(10L, "owner@example.com");

        assertEquals(1, result.size());
        assertEquals(ReviewDecision.APPROVED, result.get(0).decision());
        assertEquals(30L, result.get(0).annotationId());
    }

    @Test
    void shouldRejectProjectListingForStranger() throws Exception {
        User stranger = user("stranger@example.com", 3L, Role.ANNOTATOR);
        when(users.findByEmail("stranger@example.com")).thenReturn(Optional.of(stranger));
        when(projects.findByIdAndOwnerId(10L, 3L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class, () -> reviewService.listByProject(10L, "stranger@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void shouldListReviewsForVisibleAnnotation() throws Exception {
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        User admin = user("admin@example.com", 2L, Role.ADMIN);
        Annotation annotation = annotation(owner, owner, TaskStatus.APPROVED);
        com.labelmate.labelmate.model.Review review = new com.labelmate.labelmate.model.Review(
                annotation, admin, ReviewDecision.APPROVED, LocalDateTime.now());
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(annotations.findById(30L)).thenReturn(Optional.of(annotation));
        when(reviews.findByAnnotationIdOrderByReviewedAtDesc(30L)).thenReturn(List.of(review));

        List<ReviewResponse> result = reviewService.listByAnnotation(30L, "admin@example.com");

        assertEquals(1, result.size());
        assertEquals(ReviewDecision.APPROVED, result.get(0).decision());
    }
}
