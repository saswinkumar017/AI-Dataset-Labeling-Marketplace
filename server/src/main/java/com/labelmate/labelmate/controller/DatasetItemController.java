package com.labelmate.labelmate.controller;

import com.labelmate.labelmate.dto.DatasetItemResponse;
import com.labelmate.labelmate.dto.DatasetItemsRequest;
import com.labelmate.labelmate.dto.DatasetTableRequest;
import com.labelmate.labelmate.dto.TableIngestResult;
import com.labelmate.labelmate.service.CsvTableParser;
import com.labelmate.labelmate.service.DatasetItemService;
import com.labelmate.labelmate.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * HTTP boundary for dataset content: the raw items that later become
 * annotation tasks.
 *
 * <p>Three ingest paths (text, tabular, images) converge on the same item
 * rows. Like the other controllers it only binds and validates the request
 * and forwards the authenticated username; every ownership check lives in
 * {@link DatasetItemService}.
 */
@RestController
@RequestMapping("/api/datasets")
@Tag(name = "Dataset items", description = "Raw data ingest for annotation")
public class DatasetItemController {

    private final DatasetItemService itemService;
    private final CsvTableParser csvParser;
    private final FileStorageService fileStorage;

    public DatasetItemController(
            DatasetItemService itemService, CsvTableParser csvParser, FileStorageService fileStorage) {
        this.itemService = itemService;
        this.csvParser = csvParser;
        this.fileStorage = fileStorage;
    }

    /** Appends unstructured text items to a dataset owned by the caller. */
    @PostMapping("/{datasetId}/items")
    @Operation(summary = "Add text items to a dataset")
    public ResponseEntity<List<DatasetItemResponse>> addItems(
            @PathVariable Long datasetId,
            @Valid @RequestBody DatasetItemsRequest request,
            Authentication authentication) {
        List<DatasetItemResponse> saved = itemService.addItems(datasetId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /** Lists every item of a dataset visible to the caller. */
    @GetMapping("/{datasetId}/items")
    @Operation(summary = "List items of a dataset")
    public ResponseEntity<List<DatasetItemResponse>> list(
            @PathVariable Long datasetId, Authentication authentication) {
        return ResponseEntity.ok(itemService.list(datasetId, authentication.getName()));
    }

    /** Pages through a dataset's items (size clamped to 1–500, id order). */
    @GetMapping("/{datasetId}/items/paged")
    @Operation(summary = "Page through items of a dataset")
    public ResponseEntity<Page<DatasetItemResponse>> listPaged(
            @PathVariable Long datasetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        return ResponseEntity.ok(itemService.listPaged(datasetId, page, size, authentication.getName()));
    }

    /** Returns the ordered column header of a tabular dataset. */
    @GetMapping("/{datasetId}/columns")
    @Operation(summary = "Get column header of a dataset")
    public ResponseEntity<Map<String, Object>> columns(
            @PathVariable Long datasetId, Authentication authentication) {
        List<String> columns = itemService.columns(datasetId, authentication.getName());
        return ResponseEntity.ok(Map.of("datasetId", datasetId, "columns", columns));
    }

    /**
     * Bulk tabular ingest. Send large CSVs in chunks (e.g. 1000 rows per
     * request) so big datasets upload reliably without hitting request-size
     * limits.
     */
    @PostMapping("/{datasetId}/table-rows")
    @Operation(summary = "Add tabular rows to a dataset")
    public ResponseEntity<TableIngestResult> addTableRows(
            @PathVariable Long datasetId,
            @Valid @RequestBody DatasetTableRequest request,
            Authentication authentication) {
        TableIngestResult result = itemService.addTableRows(datasetId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Single-shot CSV upload: parses header + rows server-side
     * (quote-aware) and ingests via the same batch path as
     * {@code /table-rows}.
     */
    @PostMapping(value = "/{datasetId}/table-upload", consumes = "multipart/form-data")
    @Operation(summary = "Upload a CSV table into a dataset")
    public ResponseEntity<TableIngestResult> uploadTable(
            @PathVariable Long datasetId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        CsvTableParser.ParsedTable parsed = csvParser.parse(file);
        DatasetTableRequest request = new DatasetTableRequest(parsed.getColumns(), parsed.getRows());
        TableIngestResult result = itemService.addTableRows(datasetId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Image ingest. The dataset is validated before anything touches disk so
     * a wrong id can never orphan stored files.
     */
    @PostMapping(value = "/{datasetId}/images", consumes = "multipart/form-data")
    @Operation(summary = "Upload labeling images into a dataset")
    public ResponseEntity<List<DatasetItemResponse>> addImages(
            @PathVariable Long datasetId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "captions", required = false) List<String> captions,
            Authentication authentication) {
        // Ownership is proven before any file touches disk, so a wrong id
        // can never orphan stored files or waste an upload round-trip.
        itemService.requireOwnedDataset(datasetId, authentication.getName());
        List<FileStorageService.StoredImage> stored = new ArrayList<>();
        for (MultipartFile file : files) {
            stored.add(fileStorage.storeImage(file));
        }
        List<DatasetItemResponse> saved =
                itemService.addImageItems(datasetId, stored, captions, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
