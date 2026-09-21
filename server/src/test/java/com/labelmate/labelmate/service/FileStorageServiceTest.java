package com.labelmate.labelmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.labelmate.labelmate.dto.FileUploadResponse;
import com.labelmate.labelmate.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageServiceTest {

    @TempDir
    private Path tempDir;

    private FileStorageService storage() {
        return new FileStorageService(tempDir.toString(), 5);
    }

    private MockMultipartFile csv(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldStoreFileAndReportMetadata() throws Exception {
        FileUploadResponse response = storage().store(csv("reviews.csv", "text,label\nHi,Positive\n"));

        assertTrue(response.fileName().endsWith("_reviews.csv"));
        assertEquals("uploads/" + response.fileName(), response.filePath());
        assertTrue(response.fileSizeBytes() > 0);
        assertTrue(response.checksumSha256().matches("[0-9a-f]{64}"));
        assertTrue(Files.exists(tempDir.resolve(response.fileName())));
    }

    @Test
    void shouldRejectEmptyFile() {
        ApiException ex = assertThrows(
                ApiException.class, () -> storage().store(csv("empty.csv", "")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void shouldRejectNonDatasetExtensions() {
        ApiException exe = assertThrows(
                ApiException.class, () -> storage().store(csv("run.exe", "data")));
        ApiException noExtension = assertThrows(
                ApiException.class, () -> storage().store(csv("README", "data")));

        assertEquals(HttpStatus.BAD_REQUEST, exe.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, noExtension.getStatus());
    }

    @Test
    void shouldNeutralizePathTraversalNames() throws Exception {
        FileUploadResponse response = storage().store(csv("../../etc/passwd.csv", "a,b\n"));

        assertTrue(response.fileName().matches("\\d+_passwd\\.csv"));
        assertTrue(Files.exists(tempDir.resolve(response.fileName())));
        assertTrue(Files.notExists(tempDir.resolve("etc").resolve("passwd.csv")));
    }
}
