package com.labelmate.labelmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelmate.labelmate.dto.TaskRequest;
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
import com.labelmate.labelmate.repository.ProjectRepository;
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
class TaskServiceTest {

    @Mock
    private TaskRepository tasks;

    @Mock
    private ProjectRepository projects;

    @Mock
    private UserRepository users;

    @InjectMocks
    private TaskService taskService;

    private User user(String email, long id) throws Exception {
        User user = new User(email, "User", "hashed", Role.ANNOTATOR, LocalDateTime.now());
        setId(user, id);
        return user;
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
    void shouldCreateTaskWhenProjectBelongsToCaller() throws Exception {
        User owner = user("owner@example.com", 1L);
        Project project = project(owner);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projects.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(project));
        when(tasks.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response =
                taskService.create(10L, new TaskRequest("I love it.", 0), "owner@example.com");

        assertEquals(TaskStatus.PENDING, response.status());
        assertEquals("I love it.", response.itemData());
        assertEquals(10L, response.projectId());

        ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
        verify(tasks).save(saved.capture());
        assertEquals(project, saved.getValue().getProject());
    }

    @Test
    void shouldRejectCreationWhenProjectBelongsToAnotherUser() throws Exception {
        User owner = user("owner@example.com", 1L);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projects.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> taskService.create(10L, new TaskRequest("Hi", 0), "owner@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(tasks, never()).save(any(Task.class));
    }

    @Test
    void shouldRejectAccessWhenUserIsUnknown() throws Exception {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> taskService.list(10L, null, "ghost@example.com"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void shouldListTasksForOwnedProject() throws Exception {
        User owner = user("owner@example.com", 1L);
        Project project = project(owner);
        Task task = new Task(project, project.getDataset(), TaskStatus.PENDING, LocalDateTime.now());
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projects.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(project));
        when(tasks.findByProjectIdOrderByItemIndexAscIdAsc(10L)).thenReturn(List.of(task));

        List<TaskResponse> result = taskService.list(10L, null, "owner@example.com");

        assertEquals(1, result.size());
        assertEquals(TaskStatus.PENDING, result.get(0).status());
    }

    @Test
    void shouldFilterTasksByStatus() throws Exception {
        User owner = user("owner@example.com", 1L);
        Project project = project(owner);
        Task task = new Task(project, project.getDataset(), TaskStatus.PENDING, LocalDateTime.now());
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projects.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(project));
        when(tasks.findByProjectIdAndStatusOrderByItemIndexAscIdAsc(10L, TaskStatus.PENDING))
                .thenReturn(List.of(task));

        List<TaskResponse> result = taskService.list(10L, TaskStatus.PENDING, "owner@example.com");

        assertEquals(1, result.size());
        verify(tasks).findByProjectIdAndStatusOrderByItemIndexAscIdAsc(10L, TaskStatus.PENDING);
    }

    @Test
    void shouldAllowAdminToListAnyProjectQueue() throws Exception {
        User owner = user("owner@example.com", 1L);
        User admin = user("admin@example.com", 2L);
        admin.setRole(Role.ADMIN);
        Project project = project(owner);
        Task task = new Task(project, project.getDataset(), TaskStatus.PENDING, LocalDateTime.now());
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(tasks.findByProjectIdOrderByItemIndexAscIdAsc(10L)).thenReturn(List.of(task));

        List<TaskResponse> result = taskService.list(10L, null, "admin@example.com");

        assertEquals(1, result.size());
    }

    @Test
    void shouldRejectListingWhenProjectBelongsToAnotherUser() throws Exception {
        User owner = user("owner@example.com", 1L);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projects.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class, () -> taskService.list(10L, null, "owner@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}
