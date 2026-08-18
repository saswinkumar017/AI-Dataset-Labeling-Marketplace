package com.labelmate.labelmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelmate.labelmate.dto.ProjectRequest;
import com.labelmate.labelmate.dto.ProjectResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Dataset;
import com.labelmate.labelmate.model.DatasetStatus;
import com.labelmate.labelmate.model.Project;
import com.labelmate.labelmate.model.ProjectStatus;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.DatasetRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.UserRepository;
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
class ProjectServiceTest {

    @Mock
    private ProjectRepository projects;

    @Mock
    private DatasetRepository datasets;

    @Mock
    private UserRepository users;

    @InjectMocks
    private ProjectService projectService;

    private User owner() {
        return new User("owner@example.com", "Owner", "hashed", Role.ANNOTATOR, LocalDateTime.now());
    }

    private Dataset dataset(User owner) {
        return new Dataset(owner, "Reviews", DatasetStatus.READY, LocalDateTime.now());
    }

    @Test
    void shouldCreateProjectWhenRequestIsValid() {
        User owner = owner();
        Dataset dataset = dataset(owner);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(datasets.findByIdAndOwnerId(7L, owner.getId())).thenReturn(Optional.of(dataset));
        when(projects.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponse response = projectService.create(
                new ProjectRequest(7L, "Image Classification v1", "Label cats and dogs", "CLASSIFICATION"),
                "owner@example.com");

        assertEquals("Image Classification v1", response.name());
        assertEquals("Label cats and dogs", response.instructions());
        assertEquals("CLASSIFICATION", response.labelType());
        assertEquals(ProjectStatus.DRAFT, response.status());

        ArgumentCaptor<Project> saved = ArgumentCaptor.forClass(Project.class);
        verify(projects).save(saved.capture());
        assertEquals("Image Classification v1", saved.getValue().getName());
        assertEquals(owner, saved.getValue().getOwner());
        assertEquals(dataset, saved.getValue().getDataset());
    }

    @Test
    void shouldListOnlyMyProjects() {
        User owner = owner();
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        Project one = new Project(dataset(owner), owner, "One", ProjectStatus.DRAFT, LocalDateTime.now());
        when(projects.findByOwnerIdOrderByCreatedAtDesc(owner.getId())).thenReturn(List.of(one));

        List<ProjectResponse> result = projectService.listMine("owner@example.com");

        assertEquals(1, result.size());
        assertEquals("One", result.get(0).name());
        verify(projects).findByOwnerIdOrderByCreatedAtDesc(owner.getId());
    }

    @Test
    void shouldReturnNotFoundWhenProjectDoesNotExist() {
        User owner = owner();
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projects.findByIdAndOwnerId(99L, owner.getId())).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class, () -> projectService.getByIdForOwner(99L, "owner@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void shouldRejectAccessWhenProjectBelongsToAnotherUser() {
        User owner = owner();
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projects.findByIdAndOwnerId(1L, owner.getId())).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class, () -> projectService.delete(1L, "owner@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(projects, never()).delete(any(Project.class));
    }

    @Test
    void shouldUpdateProjectWhenOwnerMatches() {
        User owner = owner();
        Dataset dataset = dataset(owner);
        Project project = new Project(dataset, owner, "Old", ProjectStatus.DRAFT, LocalDateTime.now());
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projects.findByIdAndOwnerId(1L, owner.getId())).thenReturn(Optional.of(project));
        when(projects.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponse response = projectService.update(
                1L,
                new ProjectRequest(7L, "New", "Updated instructions", "MULTI_CLASS"),
                "owner@example.com");

        assertEquals("New", response.name());
        assertEquals("Updated instructions", response.instructions());
        assertEquals("MULTI_CLASS", response.labelType());
    }

    @Test
    void shouldKeepDatasetLinkWhenUpdating() {
        User owner = owner();
        Dataset original = dataset(owner);
        Project project = new Project(original, owner, "Old", ProjectStatus.DRAFT, LocalDateTime.now());
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(projects.findByIdAndOwnerId(1L, owner.getId())).thenReturn(Optional.of(project));
        when(projects.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        projectService.update(
                1L, new ProjectRequest(999L, "New", null, null), "owner@example.com");

        ArgumentCaptor<Project> saved = ArgumentCaptor.forClass(Project.class);
        verify(projects).save(saved.capture());
        assertEquals(original, saved.getValue().getDataset());
    }

    @Test
    void shouldRejectCreationWhenDatasetBelongsToAnotherUser() {
        User owner = owner();
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(datasets.findByIdAndOwnerId(7L, owner.getId())).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> projectService.create(
                        new ProjectRequest(7L, "Mine", null, null), "owner@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(projects, never()).save(any(Project.class));
    }
}
