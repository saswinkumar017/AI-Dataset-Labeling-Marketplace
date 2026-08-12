package com.labelmate.labelmate.controller;

import com.labelmate.labelmate.dto.DatasetRequest;
import com.labelmate.labelmate.dto.DatasetResponse;
import com.labelmate.labelmate.service.DatasetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/datasets")
@Tag(name = "Datasets", description = "Dataset management")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @PostMapping
    @Operation(summary = "Create a dataset")
    public ResponseEntity<DatasetResponse> create(
            @Valid @RequestBody DatasetRequest request, Authentication authentication) {
        DatasetResponse dataset = datasetService.create(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(dataset);
    }

    @GetMapping
    @Operation(summary = "List my datasets")
    public ResponseEntity<List<DatasetResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(datasetService.listMine(authentication.getName()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a dataset by id")
    public ResponseEntity<DatasetResponse> get(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(datasetService.getByIdForOwner(id, authentication.getName()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a dataset")
    public ResponseEntity<DatasetResponse> update(
            @PathVariable Long id, @Valid @RequestBody DatasetRequest request, Authentication authentication) {
        return ResponseEntity.ok(datasetService.update(id, request, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a dataset")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        datasetService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
