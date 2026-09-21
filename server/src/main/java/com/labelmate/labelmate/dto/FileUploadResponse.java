package com.labelmate.labelmate.dto;

public record FileUploadResponse(
        String fileName,
        String filePath,
        Long fileSizeBytes,
        String checksumSha256) {
}
