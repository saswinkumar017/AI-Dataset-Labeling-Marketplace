package com.labelmate.labelmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelmate.labelmate.dto.TaskAssignRequest;
import com.labelmate.labelmate.dto.TaskResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Dataset;
import com.labelmate.labelmate.model.DatasetStatus;
import com.labelmate.labelmate.model.Project;
import com.labelmate.labelmate.model.ProjectStatus;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.Task;
import com.labelmate.labelmate.model.TaskStatus;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
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
class TaskAssignmentServiceTest {

    @Mock
    private TaskRepository tasks;

    @Mock
    private ProjectRepository projects;

    @Mock
    private UserRepository users;

    @Mock
    private AnnotationRepository annotations;

    @InjectMocks
    private TaskService taskService;

    private User user(String email, long id) throws Exception {
        User user = new User(email, "User", "hashed", Role.ANNOTATOR, LocalDateTime.now());
        setId(user, id);
        return user;
    }

    private Task task(Project project, TaskStatus status) {
        Task task = new Task(project, project.getDataset(), status, LocalDateTime.now());
        return task;
    }

    private Project project(User owner) throws Exception {
        Dataset dataset = new Dataset(owner, "Reviews", DatasetStatus.READY, LocalDateTime.now());
        Project project = new Project(dataset, owner, "Sentiment v1", ProjectStatus.DRAFT, LocalDateTime.now());
        setId(project, 10L);
        return project;
    }

    private void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @Test
    void shouldAssignPendingTaskToAnnotator() throws Exception {
        User owner = user("owner@example.com", 1L);
        User annotator = user("ben@example.com", 2L);
        Project project = project(owner);
        Task pending = task(project, TaskStatus.PENDING);
        setId(pending, 5L);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(tasks.findById(5L)).thenReturn(Optional.of(pending));
        when(users.findByEmail("ben@example.com")).thenReturn(Optional.of(annotator));
        when(tasks.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response =
                taskService.assign(5L, new TaskAssignRequest("ben@example.com"), "owner@example.com");

        assertEquals(TaskStatus.ASSIGNED, response.status());
        assertEquals("ben@example.com", response.assignedToEmail());
    }

    @Test
    void shouldRejectAssignmentToUnknownUser() throws Exception {
        User owner = user("owner@example.com", 1L);
        Project project = project(owner);
        Task pending = task(project, TaskStatus.PENDING);
        setId(pending, 5L);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(tasks.findById(5L)).thenReturn(Optional.of(pending));
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> taskService.assign(5L, new TaskAssignRequest("ghost@example.com"), "owner@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void shouldRejectAssignmentByNonOwner() throws Exception {
        User owner = user("owner@example.com", 1L);
        User stranger = user("zed@example.com", 3L);
        Project project = project(owner);
        Task pending = task(project, TaskStatus.PENDING);
        setId(pending, 5L);
        when(users.findByEmail("zed@example.com")).thenReturn(Optional.of(stranger));
        when(tasks.findById(5L)).thenReturn(Optional.of(pending));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> taskService.assign(5L, new TaskAssignRequest("ben@example.com"), "zed@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void shouldStartAssignedTaskAsAssignee() throws Exception {
        User owner = user("owner@example.com", 1L);
        User annotator = user("ben@example.com", 2L);
        Project project = project(owner);
        Task assigned = task(project, TaskStatus.ASSIGNED);
        assigned.setAssignedTo(annotator);
        setId(assigned, 5L);
        when(users.findByEmail("ben@example.com")).thenReturn(Optional.of(annotator));
        when(tasks.findById(5L)).thenReturn(Optional.of(assigned));
        when(tasks.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.start(5L, "ben@example.com");

        assertEquals(TaskStatus.IN_PROGRESS, response.status());
    }

    @Test
    void shouldRejectSubmitWithoutAnnotations() throws Exception {
        User owner = user("owner@example.com", 1L);
        User annotator = user("ben@example.com", 2L);
        Project project = project(owner);
        Task inProgress = task(project, TaskStatus.IN_PROGRESS);
        inProgress.setAssignedTo(annotator);
        setId(inProgress, 5L);
        when(users.findByEmail("ben@example.com")).thenReturn(Optional.of(annotator));
        when(tasks.findById(5L)).thenReturn(Optional.of(inProgress));
        when(annotations.findByTaskIdOrderByCreatedAtDesc(5L)).thenReturn(List.of());

        ApiException ex = assertThrows(ApiException.class, () -> taskService.submit(5L, "ben@example.com"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void shouldListOnlyAssignedTasks() throws Exception {
        User annotator = user("ben@example.com", 2L);
        User owner = user("owner@example.com", 1L);
        Project project = project(owner);
        Task assigned = task(project, TaskStatus.ASSIGNED);
        setId(assigned, 5L);
        when(users.findByEmail("ben@example.com")).thenReturn(Optional.of(annotator));
        when(tasks.findByAssignedToIdOrderByIdAsc(2L)).thenReturn(List.of(assigned));

        List<TaskResponse> result = taskService.listAssigned("ben@example.com");

        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).id());
        verify(tasks).findByAssignedToIdOrderByIdAsc(2L);
    }
}
