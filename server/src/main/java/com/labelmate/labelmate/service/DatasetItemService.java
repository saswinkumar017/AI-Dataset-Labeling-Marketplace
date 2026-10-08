package com.labelmate.labelmate.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelmate.labelmate.dto.DatasetItemResponse;
import com.labelmate.labelmate.dto.DatasetItemsRequest;
import com.labelmate.labelmate.dto.DatasetSchemaRequest;
import com.labelmate.labelmate.dto.DatasetTableRequest;
import com.labelmate.labelmate.dto.FeatureDef;
import com.labelmate.labelmate.dto.TableIngestResult;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.Dataset;
import com.labelmate.labelmate.model.DatasetItem;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.DatasetItemRepository;
import com.labelmate.labelmate.repository.DatasetRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dataset content: the raw items that later become annotation tasks.
 *
 * <p>A dataset mixes three ingest paths — unstructured text, tabular rows,
 * and images — but every path ends in the same {@link DatasetItem} rows, so
 * task generation, the workspace, and export never care how an item arrived.
 * All writes are scoped to the dataset owner (admins may read); item ids
 * cannot be used to reach another owner's data.
 */
@Service
public class DatasetItemService {

    static final int MAX_TABLE_COLUMNS = 100;
    static final int MAX_TABLE_ROWS_PER_REQUEST = 5000;
    static final int MAX_CELL_CHARS = 8000;
    static final int MAX_ITEM_CHARS = 100_000;
    private static final int WRITE_CHUNK = 1000;
    private static final int SUMMARY_CHARS = 8000;

    private final DatasetItemRepository items;
    private final DatasetRepository datasets;
    private final UserRepository users;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatasetItemService(
            DatasetItemRepository items, DatasetRepository datasets, UserRepository users) {
        this.items = items;
        this.datasets = datasets;
        this.users = users;
    }

    /**
     * Appends unstructured text items. Blank entries are skipped; an item
     * past {@value #MAX_ITEM_CHARS} characters is rejected with a clear error
     * instead of failing obscurely at the database layer.
     */
    @Transactional
    public List<DatasetItemResponse> addItems(Long datasetId, DatasetItemsRequest request, String userEmail) {
        Dataset dataset = loadOwnedDataset(datasetId, userEmail);
        List<DatasetItem> batch = new ArrayList<>();
        for (String raw : request.contents()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String content = raw.strip();
            if (content.length() > MAX_ITEM_CHARS) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "An item exceeds the " + MAX_ITEM_CHARS + "-character limit; split it into smaller items");
            }
            batch.add(new DatasetItem(dataset, content, LocalDateTime.now()));
        }
        if (batch.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No usable items provided");
        }
        touchDatasetType(dataset, "TEXT");
        return items.saveAll(batch).stream().map(DatasetItemResponse::from).toList();
    }

    /**
     * Bulk tabular ingest. New column names merge into the dataset header
     * preserving order; rows insert in chunks so large uploads do not issue
     * one round trip per row. Repeatable: the frontend chunks huge CSVs into
     * ~1000-row requests against this same path.
     */
    @Transactional
    public TableIngestResult addTableRows(Long datasetId, DatasetTableRequest request, String userEmail) {
        Dataset dataset = loadOwnedDataset(datasetId, userEmail);
        List<String> requested = new ArrayList<>();
        for (String column : request.columns()) {
            if (column == null || column.isBlank() || requested.contains(column.strip())) {
                continue;
            }
            requested.add(column.strip());
        }
        if (requested.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No usable column names provided");
        }
        if (requested.size() > MAX_TABLE_COLUMNS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Too many columns (max " + MAX_TABLE_COLUMNS + ")");
        }
        List<String> merged = new ArrayList<>(parseColumns(dataset.getColumnsJson()));
        for (String column : requested) {
            if (!merged.contains(column)) {
                merged.add(column);
            }
        }
        if (merged.size() > MAX_TABLE_COLUMNS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Too many columns (max " + MAX_TABLE_COLUMNS + ")");
        }
        dataset.setColumnsJson(writeJson(merged));
        mergeFeatureDefs(dataset, merged);
        if ((dataset.getDatasetType() == null || dataset.getDatasetType().isBlank()) && merged.size() > 1) {
            dataset.setDatasetType("TABULAR");
        }
        datasets.save(dataset);

        List<DatasetItem> batch = new ArrayList<>(WRITE_CHUNK);
        long saved = 0;
        for (Map<String, String> row : request.rows()) {
            if (row == null) {
                continue;
            }
            Map<String, String> clean = new LinkedHashMap<>();
            boolean allBlank = true;
            for (String column : merged) {
                String value = row.getOrDefault(column, "");
                value = value == null ? "" : value.strip();
                if (value.length() > MAX_CELL_CHARS) {
                    value = value.substring(0, MAX_CELL_CHARS);
                }
                if (!value.isEmpty()) {
                    allBlank = false;
                }
                clean.put(column, value);
            }
            if (allBlank) {
                continue;
            }
            DatasetItem item = new DatasetItem(dataset, buildContentSummary(clean, merged), LocalDateTime.now());
            item.setRowDataJson(writeJson(clean));
            batch.add(item);
            if (batch.size() >= WRITE_CHUNK) {
                items.saveAll(batch);
                saved += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            items.saveAll(batch);
            saved += batch.size();
        }
        if (saved == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No usable rows provided");
        }
        return new TableIngestResult(datasetId, List.copyOf(merged), saved, items.countByDatasetId(dataset.getId()));
    }

    /**
     * Appends image items from already-stored uploads. The dataset is
     * validated before anything is stored on disk, so a wrong id can never
     * orphan files. Content is the caption, falling back to the original
     * filename so every item stays human-readable.
     */
    @Transactional
    public List<DatasetItemResponse> addImageItems(
            Long datasetId, List<FileStorageService.StoredImage> stored, List<String> captions, String userEmail) {
        Dataset dataset = loadOwnedDataset(datasetId, userEmail);
        if (stored == null || stored.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No image files provided");
        }
        List<DatasetItem> batch = new ArrayList<>();
        for (int i = 0; i < stored.size(); i++) {
            FileStorageService.StoredImage image = stored.get(i);
            String caption = captions != null && i < captions.size() ? captions.get(i).trim() : "";
            String content = caption.isEmpty() ? image.originalName() : caption;
            if (content.isBlank()) {
                content = "image-item";
            }
            DatasetItem item = new DatasetItem(dataset, content, LocalDateTime.now());
            item.setImageUrl(image.imageUrl());
            item.setMediaType(image.mediaType());
            batch.add(item);
        }
        touchDatasetType(dataset, "IMAGE");
        return items.saveAll(batch).stream().map(DatasetItemResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DatasetItemResponse> list(Long datasetId, String userEmail) {
        Dataset dataset = loadReadableDataset(datasetId, loadUser(userEmail));
        return items.findByDatasetIdOrderByIdAsc(dataset.getId()).stream()
                .map(DatasetItemResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<DatasetItemResponse> listPaged(Long datasetId, int page, int size, String userEmail) {
        Dataset dataset = loadReadableDataset(datasetId, loadUser(userEmail));
        int safeSize = Math.min(Math.max(size, 1), 500);
        int safePage = Math.max(page, 0);
        return items.findByDatasetId(
                        dataset.getId(), PageRequest.of(safePage, safeSize, Sort.by("id").ascending()))
                .map(DatasetItemResponse::from);
    }

    @Transactional(readOnly = true)
    public List<String> columns(Long datasetId, String userEmail) {
        Dataset dataset = loadReadableDataset(datasetId, loadUser(userEmail));
        return parseColumns(dataset.getColumnsJson());
    }

    /**
     * Replaces the dataset's typed feature schema, kind, and ground-truth
     * column. New feature names join the dataset header (preserving order)
     * so a multi-feature schema can be declared before any rows arrive; the
     * label column must name an existing column (or be blank to clear it).
     * Labeling reads hide the label column, never storage or exports.
     */
    @Transactional
    public List<FeatureDef> updateSchema(Long datasetId, DatasetSchemaRequest request, String userEmail) {
        Dataset dataset = loadOwnedDataset(datasetId, userEmail);
        List<FeatureDef> cleaned = cleanFeatures(request == null ? null : request.features());
        List<String> merged = new ArrayList<>(parseColumns(dataset.getColumnsJson()));
        for (FeatureDef feature : cleaned) {
            if (!containsIgnoreCase(merged, feature.name())) {
                if (merged.size() >= MAX_TABLE_COLUMNS) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Too many columns (max " + MAX_TABLE_COLUMNS + ")");
                }
                merged.add(feature.name().strip());
            }
        }
        String labelColumn = request == null || request.labelColumn() == null
                ? null
                : request.labelColumn().strip();
        if (labelColumn != null && labelColumn.isEmpty()) {
            labelColumn = null;
        }
        if (labelColumn != null && !merged.isEmpty() && !containsIgnoreCase(merged, labelColumn)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "labelColumn must name an existing column: " + String.join(", ", merged));
        }
        dataset.setColumnsJson(merged.isEmpty() ? null : writeJson(merged));
        dataset.setFeaturesJson(cleaned.isEmpty() ? null : writeJson(cleaned));
        dataset.setLabelColumn(labelColumn);
        if (request != null && request.datasetType() != null && !request.datasetType().isBlank()) {
            dataset.setDatasetType(request.datasetType().strip().toUpperCase());
        } else if ((dataset.getDatasetType() == null || dataset.getDatasetType().isBlank())
                && merged.size() > 1) {
            dataset.setDatasetType("TABULAR");
        }
        datasets.save(dataset);
        return List.copyOf(cleaned);
    }

    /**
     * Adds one named feature column (with a type) to a dataset header without
     * ingesting rows — the entry point for building multi-feature text
     * datasets column by column from the UI.
     */
    @Transactional
    public List<FeatureDef> addFeatureColumn(Long datasetId, FeatureDef feature, String userEmail) {
        if (feature == null || feature.name() == null || feature.name().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "feature name is required");
        }
        String name = feature.name().strip();
        Dataset dataset = loadOwnedDataset(datasetId, userEmail);
        List<String> merged = new ArrayList<>(parseColumns(dataset.getColumnsJson()));
        if (!containsIgnoreCase(merged, name)) {
            if (merged.size() >= MAX_TABLE_COLUMNS) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Too many columns (max " + MAX_TABLE_COLUMNS + ")");
            }
            merged.add(name);
        }
        dataset.setColumnsJson(writeJson(merged));
        List<FeatureDef> defs = new ArrayList<>(parseFeatures(dataset.getFeaturesJson()));
        defs.removeIf(existing -> existing.name().equalsIgnoreCase(name));
        defs.add(new FeatureDef(name, feature.normalizedType()));
        dataset.setFeaturesJson(writeJson(defs));
        if ((dataset.getDatasetType() == null || dataset.getDatasetType().isBlank()) && merged.size() > 1) {
            dataset.setDatasetType("TABULAR");
        }
        datasets.save(dataset);
        return List.copyOf(defs);
    }

    @Transactional(readOnly = true)
    public List<FeatureDef> features(Long datasetId, String userEmail) {
        Dataset dataset = loadReadableDataset(datasetId, loadUser(userEmail));
        return parseFeatures(dataset.getFeaturesJson());
    }

    /**
     * Full multi-line rendering of an item for labeling and AI prompts: every
     * non-blank {@code key: value} line of tabular rows, otherwise the plain
     * content. Never null.
     */
    public String renderItemForLabeling(DatasetItem item) {
        if (item == null) {
            return "";
        }
        Map<String, String> row = DatasetItemResponse.parseRowData(item.getRowDataJson());
        if (row.isEmpty()) {
            return item.getContent() == null ? "" : item.getContent();
        }
        StringBuilder rendered = new StringBuilder();
        for (Map.Entry<String, String> entry : row.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            rendered.append(entry.getKey()).append(": ").append(entry.getValue().strip()).append("\n");
        }
        String text = rendered.toString().trim();
        return text.isEmpty() && item.getContent() != null ? item.getContent() : text;
    }

    /** Short human-readable summary kept in {@code content} for tabular rows. */
    private String buildContentSummary(Map<String, String> row, List<String> columns) {
        StringBuilder summary = new StringBuilder();
        for (String column : columns) {
            String value = row.getOrDefault(column, "");
            if (value.isBlank()) {
                continue;
            }
            if (!summary.isEmpty()) {
                summary.append(" | ");
            }
            summary.append(column).append(": ").append(value);
            if (summary.length() >= 1500) {
                break;
            }
        }
        String text = summary.toString().trim();
        if (text.isEmpty()) {
            return "row-item";
        }
        return text.length() > SUMMARY_CHARS ? text.substring(0, SUMMARY_CHARS) : text;
    }

    List<String> parseColumns(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> parsed = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    List<FeatureDef> parseFeatures(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<FeatureDef> parsed = objectMapper.readValue(json, new TypeReference<List<FeatureDef>>() {});
            return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    /** Registers freshly seen columns as untyped TEXT features, preserving order. */
    private void mergeFeatureDefs(Dataset dataset, List<String> merged) {
        List<FeatureDef> defs = new ArrayList<>(parseFeatures(dataset.getFeaturesJson()));
        boolean changed = false;
        for (String column : merged) {
            boolean known = false;
            for (FeatureDef def : defs) {
                if (def.name().equalsIgnoreCase(column)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                defs.add(new FeatureDef(column, "TEXT"));
                changed = true;
            }
        }
        if (changed || !defs.isEmpty()) {
            dataset.setFeaturesJson(writeJson(defs));
        }
    }

    private List<FeatureDef> cleanFeatures(List<FeatureDef> requested) {
        List<FeatureDef> cleaned = new ArrayList<>();
        if (requested == null) {
            return cleaned;
        }
        for (FeatureDef feature : requested) {
            if (feature == null || feature.name() == null || feature.name().isBlank()) {
                continue;
            }
            String name = feature.name().strip();
            boolean duplicate = false;
            for (FeatureDef existing : cleaned) {
                if (existing.name().equalsIgnoreCase(name)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                cleaned.add(new FeatureDef(name, feature.normalizedType()));
            }
        }
        return cleaned;
    }

    private boolean containsIgnoreCase(List<String> names, String candidate) {
        for (String name : names) {
            if (name.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records the dataset kind on first ingest (TEXT/IMAGE) and upgrades to
     * MIXED when a second kind arrives — the UI uses it to pick the right
     * ingest widgets for multi-type datasets.
     */
    private void touchDatasetType(Dataset dataset, String kind) {
        String current = dataset.getDatasetType();
        if (current == null || current.isBlank()) {
            dataset.setDatasetType(kind);
            datasets.save(dataset);
        } else if (!current.equalsIgnoreCase(kind) && !"MIXED".equalsIgnoreCase(current)) {
            dataset.setDatasetType("MIXED");
            datasets.save(dataset);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "JSON encoding failed");
        }
    }

    private User loadUser(String userEmail) {
        return users.findByEmail(userEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    }

    /**
     * Proves the caller owns the dataset without loading any items. Used by
     * upload paths that must validate ownership before touching disk.
     */
    public void requireOwnedDataset(Long datasetId, String userEmail) {
        loadOwnedDataset(datasetId, userEmail);
    }

    private Dataset loadOwnedDataset(Long datasetId, String userEmail) {
        User owner = loadUser(userEmail);
        return datasets.findByIdAndOwnerId(datasetId, owner.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Dataset not found"));
    }

    private Dataset loadReadableDataset(Long datasetId, User user) {
        if (user.getRole() == Role.ADMIN) {
            return datasets.findById(datasetId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Dataset not found"));
        }
        return datasets.findByIdAndOwnerId(datasetId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Dataset not found"));
    }
}
