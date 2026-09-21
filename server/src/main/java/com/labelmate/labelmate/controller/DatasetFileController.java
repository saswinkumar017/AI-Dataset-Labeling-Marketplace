package com.labelmate.labelmate.controller;

import com.labelmate.labelmate.dto.FileUploadResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * HTTP boundary for dataset file uploads.
 *
 * <p>Stores the raw bytes and returns the metadata the dataset form
 * persists; validation and storage rules live in {@link FileStorageService}.
 */
@RestController
@RequestMapping("/api/datasets")
@Tag(name = "Dataset files", description = "Raw dataset file storage")
public class DatasetFileController {

    private final FileStorageService fileStorageService;

    public DatasetFileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /** Stores one dataset file and returns its metadata. */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @Operation(summary = "Upload a dataset file")
    public ResponseEntity<FileUploadResponse> upload(@RequestParam(value = "file", required = false) MultipartFile file) {
        if (file == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "file is required");
        }
        FileUploadResponse stored = fileStorageService.store(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(stored);
    }
}
