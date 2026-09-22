package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.DatasetItemsRequest;
import com.labelmate.labelmate.dto.DatasetRequest;
import com.labelmate.labelmate.dto.DatasetResponse;
import com.labelmate.labelmate.dto.DatasetTableRequest;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Dataset;
import com.labelmate.labelmate.model.DatasetStatus;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.DatasetItemRepository;
import com.labelmate.labelmate.repository.DatasetRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasetService {

    private static final int MAX_TEXT_LINES_PER_FILE = 5000;

    private final DatasetRepository datasets;
    private final UserRepository users;
    private final DatasetItemRepository datasetItems;
    private final DatasetItemService itemService;
    private final CsvTableParser csvParser;
    private final FileStorageService fileStorage;
    private final ProjectService projectService;

    public DatasetService(
            DatasetRepository datasets,
            UserRepository users,
            DatasetItemRepository datasetItems,
            DatasetItemService itemService,
            CsvTableParser csvParser,
            FileStorageService fileStorage,
            ProjectService projectService) {
        this.datasets = datasets;
        this.users = users;
        this.datasetItems = datasetItems;
        this.itemService = itemService;
        this.csvParser = csvParser;
        this.fileStorage = fileStorage;
        this.projectService = projectService;
    }

    /**
     * Creates a dataset, then ingests its attached upload (if any) into
     * items so a project created on it immediately has a labeling queue.
     * Atomic: an unparsable attachment fails the whole request instead of
     * leaving a dataset that looks ready but has no data.
     */
    @Transactional
    public DatasetResponse create(DatasetRequest request, String ownerEmail) {
        User owner = loadOwner(ownerEmail);
        Dataset dataset = new Dataset(owner, request.name().trim(), DatasetStatus.READY, LocalDateTime.now());
        applyFields(dataset, request);
        Dataset saved = datasets.save(dataset);
        ingestAttachedFile(saved, ownerEmail);
        return DatasetResponse.from(saved, datasetItems.countByDatasetId(saved.getId()));
    }

    public List<DatasetResponse> listMine(String ownerEmail) {
        User owner = loadOwner(ownerEmail);
        return datasets.findByOwnerIdOrderByCreatedAtDesc(owner.getId()).stream()
                .map(dataset -> DatasetResponse.from(dataset, datasetItems.countByDatasetId(dataset.getId())))
                .toList();
    }

    public DatasetResponse getByIdForOwner(Long id, String ownerEmail) {
        Dataset dataset = loadOwned(id, ownerEmail);
        return DatasetResponse.from(dataset, datasetItems.countByDatasetId(dataset.getId()));
    }

    /**
     * Updates metadata only. Swapping the attached file does not re-ingest:
     * re-parsing would duplicate or orphan items, so further data always
     * goes through the explicit ingest endpoints instead.
     */
    public DatasetResponse update(Long id, DatasetRequest request, String ownerEmail) {
        Dataset dataset = loadOwned(id, ownerEmail);
        dataset.setName(request.name().trim());
        applyFields(dataset, request);
        dataset.setUpdatedAt(LocalDateTime.now());
        Dataset saved = datasets.save(dataset);
        return DatasetResponse.from(saved, datasetItems.countByDatasetId(saved.getId()));
    }

    /**
     * Deletes a dataset only when it belongs to the calling user.
     *
     * <p>Deletion cascades through everything derived from the data —
     * projects (with their tasks, annotations, and reviews) first, then
     * items, then the dataset itself — because none of it is reachable or
     * meaningful without the dataset, and orphaned rows would corrupt task
     * queues and exports. The UI confirms the scope before calling.
     */
    @Transactional
    public void delete(Long id, String ownerEmail) {
        Dataset dataset = loadOwned(id, ownerEmail);
        projectService.deleteProjectsOfDataset(dataset.getId(), ownerEmail);
        datasetItems.deleteAll(datasetItems.findByDatasetIdOrderByIdAsc(dataset.getId()));
        datasets.delete(dataset);
    }

    /**
     * Turns the uploaded attachment into dataset items: {@code .csv} files
     * go through the quote-aware table parser, {@code .txt} files become one
     * item per non-blank line. Anything else (or a path with no stored file
     * behind it) is a metadata-only record and is left alone.
     */
    private void ingestAttachedFile(Dataset dataset, String ownerEmail) {
        String filePath = dataset.getFilePath();
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        String lower = filePath.toLowerCase(Locale.ROOT);
        boolean csv = lower.endsWith(".csv");
        boolean txt = lower.endsWith(".txt");
        if (!csv && !txt) {
            return;
        }
        Optional<Path> stored = fileStorage.findStoredFile(filePath);
        if (stored.isEmpty()) {
            return;
        }
        if (csv) {
            CsvTableParser.ParsedTable parsed;
            try (InputStream in = Files.newInputStream(stored.get())) {
                String filename = stored.get().getFileName().toString();
                parsed = csvParser.parse(in, filename);
            } catch (ApiException ex) {
                throw ex;
            } catch (IOException ex) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "could not read the uploaded file");
            }
            itemService.addTableRows(
                    dataset.getId(),
                    new DatasetTableRequest(parsed.getColumns(), parsed.getRows()),
                    ownerEmail);
        } else {
            itemService.addItems(dataset.getId(), new DatasetItemsRequest(readTextLines(stored.get())), ownerEmail);
        }
    }

    private List<String> readTextLines(Path file) {
        String raw;
        try {
            raw = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "could not read the uploaded file");
        }
        if (raw.startsWith("\uFEFF")) {
            raw = raw.substring(1);
        }
        List<String> lines = new ArrayList<>();
        for (String line : raw.split("\\R")) {
            String text = line.strip();
            if (!text.isEmpty()) {
                lines.add(text);
            }
        }
        if (lines.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The uploaded file has no usable lines");
        }
        if (lines.size() > MAX_TEXT_LINES_PER_FILE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Too many lines (max " + MAX_TEXT_LINES_PER_FILE + " per file; split and ingest in parts)");
        }
        return lines;
    }

    private void applyFields(Dataset dataset, DatasetRequest request) {
        dataset.setDescription(request.description());
        dataset.setFileName(request.fileName());
        String filePath = request.filePath();
        rejectUnsafePath(filePath);
        dataset.setFilePath(filePath);
        dataset.setFileSizeBytes(request.fileSizeBytes());
        dataset.setChecksumSha256(request.checksumSha256());
    }

    private void rejectUnsafePath(String filePath) {
        if (filePath == null) {
            return;
        }
        if (filePath.contains("..") || filePath.startsWith("/") || filePath.startsWith("\\")
                || filePath.contains("\0")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "filePath must be a relative path without parent references");
        }
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
