package com.labelmate.labelmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelmate.labelmate.dto.TaskResponse;
import com.labelmate.labelmate.model.Dataset;
import com.labelmate.labelmate.model.DatasetItem;
import com.labelmate.labelmate.model.DatasetStatus;
import com.labelmate.labelmate.model.Project;
import com.labelmate.labelmate.model.ProjectStatus;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.Task;
import com.labelmate.labelmate.model.TaskStatus;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.DatasetItemRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TaskGenerationServiceTest {

    @Mock
    private TaskRepository tasks;

    @Mock
    private ProjectRepository projects;

    @Mock
    private UserRepository users;

    @Mock
    private AnnotationRepository annotations;

    @Mock
    private DatasetItemRepository datasetItems;

    @Mock
    private DatasetItemService datasetItemService;

    @InjectMocks
    private TaskService taskService;

    private User user(String email, long id) throws Exception {
        User user = new User(email, "User", "hashed", Role.ANNOTATOR, LocalDateTime.now());
        setId(user, id);
        return user;
    }

    private Project project(User owner) throws Exception {
        Dataset dataset = new Dataset(owner, "Reviews", DatasetStatus.READY, LocalDateTime.now());
        setId(dataset, 7L);
        Project project = new Project(dataset, owner, "Sentiment v1", ProjectStatus.DRAFT, LocalDateTime.now());
        setId(project, 10L);
        return project;
    }

    private DatasetItem item(Dataset dataset, long id, String content) throws Exception {
        DatasetItem item = new DatasetItem(dataset, content, LocalDateTime.now());
        setId(item, id);
        return item;
    }

    private void setId(Object entity, long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldGenerateInOneIdQueryInsteadOfOneExistsPerItem() throws Exception {
        User owner = user("owner@example.com", 1L);
        Project project = project(owner);
        DatasetItem first = item(project.getDataset(), 101L, "First.");
        DatasetItem second = item(project.getDataset(), 102L, "Second.");
        DatasetItem third = item(project.getDataset(), 103L, "Third.");

        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projects.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(project));
        when(tasks.findByProjectIdOrderByItemIndexAscIdAsc(10L)).thenReturn(List.of());
        // One batch id query for the whole project — never one EXISTS per item,
        // which is what stalled real-size datasets past client timeouts.
        when(tasks.findDatasetItemIdsByProjectId(10L)).thenReturn(List.of(101L));
        when(datasetItems.findByDatasetId(any(Long.class), any(Pageable.class)))
                .thenReturn(new PageImpl(List.of(first, second, third)));
        when(datasetItemService.renderItemForLabeling(any(DatasetItem.class)))
                .thenAnswer(invocation -> ((DatasetItem) invocation.getArgument(0)).getContent());
        when(tasks.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<TaskResponse> created = taskService.generateTasks(10L, "owner@example.com");

        assertEquals(2, created.size());
        assertEquals(102L, created.get(0).item().id());
        assertEquals(0, created.get(0).itemIndex());
        assertEquals(1, created.get(1).itemIndex());
        verify(tasks, times(1)).findDatasetItemIdsByProjectId(10L);
        verify(tasks, times(1)).saveAll(any());

        ArgumentCaptor<List<Task>> batch = ArgumentCaptor.forClass(List.class);
        verify(tasks).saveAll(batch.capture());
        assertEquals(2, batch.getValue().size());
        verify(tasks, never()).findByProjectIdAndStatusOrderByItemIndexAscIdAsc(any(), any());
    }
}
