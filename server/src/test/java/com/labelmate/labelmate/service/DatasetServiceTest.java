package com.labelmate.labelmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelmate.labelmate.dto.DatasetRequest;
import com.labelmate.labelmate.dto.DatasetResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Dataset;
import com.labelmate.labelmate.model.DatasetStatus;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.DatasetRepository;
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
class DatasetServiceTest {

    @Mock
    private DatasetRepository datasets;

    @Mock
    private UserRepository users;

    @InjectMocks
    private DatasetService datasetService;

    private User owner() {
        return new User("owner@example.com", "Owner", "hashed", Role.ANNOTATOR, LocalDateTime.now());
    }

    @Test
    void shouldCreateDatasetWhenRequestIsValid() {
        User owner = owner();
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(datasets.save(any(Dataset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DatasetResponse response = datasetService.create(
                new DatasetRequest("Reviews", "Sentiment data"), "owner@example.com");

        assertEquals("Reviews", response.name());
        assertEquals("Sentiment data", response.description());
        assertEquals(DatasetStatus.READY, response.status());

        ArgumentCaptor<Dataset> saved = ArgumentCaptor.forClass(Dataset.class);
        verify(datasets).save(saved.capture());
        assertEquals("Reviews", saved.getValue().getName());
        assertEquals(owner, saved.getValue().getOwner());
    }

    @Test
    void shouldListOnlyMyDatasets() {
        User owner = owner();
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        Dataset one = new Dataset(owner, "One", DatasetStatus.READY, LocalDateTime.now());
        when(datasets.findByOwnerIdOrderByCreatedAtDesc(owner.getId())).thenReturn(List.of(one));

        List<DatasetResponse> result = datasetService.listMine("owner@example.com");

        assertEquals(1, result.size());
        assertEquals("One", result.get(0).name());
        verify(datasets).findByOwnerIdOrderByCreatedAtDesc(owner.getId());
    }

    @Test
    void shouldReturnNotFoundWhenDatasetDoesNotExist() {
        User owner = owner();
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(datasets.findByIdAndOwnerId(99L, owner.getId())).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class, () -> datasetService.getByIdForOwner(99L, "owner@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void shouldRejectAccessWhenDatasetBelongsToAnotherUser() {
        User owner = owner();
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(datasets.findByIdAndOwnerId(1L, owner.getId())).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class, () -> datasetService.delete(1L, "owner@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(datasets, never()).delete(any(Dataset.class));
    }

    @Test
    void shouldUpdateDatasetWhenOwnerMatches() {
        User owner = owner();
        Dataset dataset = new Dataset(owner, "Old", DatasetStatus.READY, LocalDateTime.now());
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(datasets.findByIdAndOwnerId(1L, owner.getId())).thenReturn(Optional.of(dataset));
        when(datasets.save(any(Dataset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DatasetResponse response = datasetService.update(
                1L, new DatasetRequest("New", "Updated"), "owner@example.com");

        assertEquals("New", response.name());
        assertEquals("Updated", response.description());
    }
}
