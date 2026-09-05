package com.labelmate.labelmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelmate.labelmate.dto.ExportRow;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private TaskRepository tasks;

    @Mock
    private AnnotationRepository annotations;

    @Mock
    private ReviewRepository reviews;

    @Mock
    private ProjectRepository projects;

    @Mock
    private UserRepository users;

    @InjectMocks
    private ExportService exportService;

    private User user(String email, long id, Role role) throws Exception {
        User user = new User(email, "User", "hashed", role, LocalDateTime.now());
        setId(user, id);
        return user;
    }

    private Project project(User owner) throws Exception {
        Dataset dataset = new Dataset(owner, "Reviews", DatasetStatus.READY, LocalDateTime.now());
        Project project = new Project(dataset, owner, "Sentiment v1", ProjectStatus.DRAFT, LocalDateTime.now());
        setId(project, 10L);
        return project;
    }

    private Task task(Project project, String item, TaskStatus status, int index) throws Exception {
        Task task = new Task(project, project.getDataset(), status, LocalDateTime.now());
        task.setItemData(item);
        task.setItemIndex(index);
        setId(task, 20L + index);
        return task;
    }

    private Annotation annotation(Task task, User annotator, AnnotationSource source, String label)
            throws Exception {
        Annotation annotation = new Annotation(task, annotator, source, LocalDateTime.now());
        annotation.setContent(label);
        return annotation;
    }

    private com.labelmate.labelmate.model.Review review(
            Annotation annotation, User reviewer, ReviewDecision decision) {
        return new com.labelmate.labelmate.model.Review(
                annotation, reviewer, decision, LocalDateTime.now());
    }

    private void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @Test
    void shouldExportLatestApprovedAnnotationPerTask() throws Exception {
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        User admin = user("admin@example.com", 2L, Role.ADMIN);
        Project project = project(owner);
        Task task = task(project, "I love it.", TaskStatus.APPROVED, 0);
        Annotation older = annotation(task, owner, AnnotationSource.HUMAN_APPROVED, "Positive");
        setId(older, 30L);
        Annotation newer = annotation(task, owner, AnnotationSource.HUMAN, "Negative");
        setId(newer, 31L);
        com.labelmate.labelmate.model.Review approval = review(older, admin, ReviewDecision.APPROVED);

        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(tasks.findByProjectIdOrderByItemIndexAscIdAsc(10L)).thenReturn(List.of(task));
        when(annotations.findByTaskIdOrderByCreatedAtDesc(task.getId()))
                .thenReturn(List.of(newer, older));
        when(reviews.findByAnnotationIdOrderByReviewedAtDesc(30L)).thenReturn(List.of(approval));

        List<ExportRow> rows = exportService.exportProject(10L, "owner@example.com");

        assertEquals(1, rows.size());
        assertEquals("Positive", rows.get(0).label());
        assertEquals("I love it.", rows.get(0).itemData());
        assertEquals("APPROVED", rows.get(0).taskStatus());
    }

    @Test
    void shouldSkipTasksWithoutApprovedWork() throws Exception {
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        Project project = project(owner);
        Task submitted = task(project, "Meh.", TaskStatus.SUBMITTED, 0);
        Task untouched = task(project, "Hello.", TaskStatus.PENDING, 1);
        Annotation draft = annotation(submitted, owner, AnnotationSource.HUMAN, "Neutral");

        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(tasks.findByProjectIdOrderByItemIndexAscIdAsc(10L)).thenReturn(List.of(submitted, untouched));
        when(annotations.findByTaskIdOrderByCreatedAtDesc(submitted.getId())).thenReturn(List.of(draft));
        when(annotations.findByTaskIdOrderByCreatedAtDesc(untouched.getId())).thenReturn(List.of());

        List<ExportRow> rows = exportService.exportProject(10L, "owner@example.com");

        assertTrue(rows.isEmpty());
    }

    @Test
    void shouldEscapeCsvFields() throws Exception {
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        User admin = user("admin@example.com", 2L, Role.ADMIN);
        Project project = project(owner);
        Task task = task(project, "Said \"great\",\nleft.", TaskStatus.APPROVED, 0);
        Annotation approved = annotation(task, owner, AnnotationSource.HUMAN_APPROVED, "Positive");
        setId(approved, 30L);

        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(tasks.findByProjectIdOrderByItemIndexAscIdAsc(10L)).thenReturn(List.of(task));
        when(annotations.findByTaskIdOrderByCreatedAtDesc(task.getId())).thenReturn(List.of(approved));
        when(reviews.findByAnnotationIdOrderByReviewedAtDesc(30L)).thenReturn(List.of());

        String csv = exportService.exportProjectCsv(10L, "owner@example.com");

        assertTrue(csv.startsWith("item_index,item_data,label,task_status,reviewed_at\n"));
        assertTrue(csv.contains("\"Said \"\"great\"\",\nleft.\""));
    }

    @Test
    void shouldHideForeignProjects() throws Exception {
        User owner = user("owner@example.com", 1L, Role.ANNOTATOR);
        User stranger = user("stranger@example.com", 3L, Role.ANNOTATOR);
        Project project = project(owner);
        when(users.findByEmail("stranger@example.com")).thenReturn(Optional.of(stranger));
        when(projects.findById(10L)).thenReturn(Optional.of(project));

        ApiException ex = assertThrows(
                ApiException.class, () -> exportService.exportProject(10L, "stranger@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(tasks, never()).findByProjectIdOrderByItemIndexAscIdAsc(any());
    }

    @Test
    void shouldRejectAccessWhenUserIsUnknown() throws Exception {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class, () -> exportService.exportProject(10L, "ghost@example.com"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }
}
