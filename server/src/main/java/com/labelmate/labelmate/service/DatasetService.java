package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.DatasetRequest;
import com.labelmate.labelmate.dto.DatasetResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Dataset;
import com.labelmate.labelmate.model.DatasetStatus;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.DatasetRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DatasetService {

    private final DatasetRepository datasets;
    private final UserRepository users;

    public DatasetService(DatasetRepository datasets, UserRepository users) {
        this.datasets = datasets;
        this.users = users;
    }

    public DatasetResponse create(DatasetRequest request, String ownerEmail) {
        User owner = loadOwner(ownerEmail);
        Dataset dataset = new Dataset(owner, request.name(), DatasetStatus.READY, LocalDateTime.now());
        dataset.setDescription(request.description());
        return DatasetResponse.from(datasets.save(dataset));
    }

    public List<DatasetResponse> listMine(String ownerEmail) {
        User owner = loadOwner(ownerEmail);
        return datasets.findByOwnerIdOrderByCreatedAtDesc(owner.getId()).stream()
                .map(DatasetResponse::from)
                .toList();
    }

    public DatasetResponse getByIdForOwner(Long id, String ownerEmail) {
        return DatasetResponse.from(loadOwned(id, ownerEmail));
    }

    public DatasetResponse update(Long id, DatasetRequest request, String ownerEmail) {
        Dataset dataset = loadOwned(id, ownerEmail);
        dataset.setName(request.name());
        dataset.setDescription(request.description());
        dataset.setUpdatedAt(LocalDateTime.now());
        return DatasetResponse.from(datasets.save(dataset));
    }

    public void delete(Long id, String ownerEmail) {
        datasets.delete(loadOwned(id, ownerEmail));
    }

    private User loadOwner(String ownerEmail) {
        return users.findByEmail(ownerEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    }

    private Dataset loadOwned(Long id, String ownerEmail) {
        User owner = loadOwner(ownerEmail);
        return datasets.findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Dataset not found"));
    }
}
