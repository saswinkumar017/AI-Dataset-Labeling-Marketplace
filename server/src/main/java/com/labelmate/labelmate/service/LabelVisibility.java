package com.labelmate.labelmate.service;

import com.labelmate.labelmate.dto.DatasetItemResponse;
import com.labelmate.labelmate.model.Dataset;
import com.labelmate.labelmate.model.DatasetItem;
import com.labelmate.labelmate.model.Project;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ground-truth hiding for labeling reads.
 *
 * <p>Datasets often ship with an answer column (e.g. {@code rating} next to
 * {@code review_text}). Storage and exports keep it, but every labeling
 * read — task items, AI prompts, the workspace — must hide it, otherwise
 * annotators (human or AI) see the correct label before doing any work.
 *
 * <p>Resolution order: the project's own label column wins, falling back to
 * the dataset's; both plus the project's extra hidden columns are always
 * removed. An explicit project feature allowlist further restricts what is
 * shown (empty = every non-hidden column). Matching is case-insensitive so
 * {@code "Rating"} still hides a {@code "rating"} answer column.
 */
public final class LabelVisibility {

    private LabelVisibility() {
    }

    /** All column names that must stay out of labeling reads for this project. */
    public static Set<String> hiddenColumns(Project project, Dataset dataset) {
        Set<String> hiddenLower = new LinkedHashSet<>();
        if (project != null) {
            addLower(hiddenLower, project.getLabelColumn());
            for (String hidden : DatasetItemResponse.parseColumns(project.getHiddenColumnsJson())) {
                addLower(hiddenLower, hidden);
            }
        }
        if (dataset != null) {
            addLower(hiddenLower, dataset.getLabelColumn());
        }
        return hiddenLower;
    }

    /** Visible feature columns in display order for this project. */
    public static List<String> visibleColumns(Project project, Dataset dataset) {
        List<String> datasetColumns = dataset != null
                ? DatasetItemResponse.parseColumns(dataset.getColumnsJson())
                : List.of();
        Set<String> hiddenLower = hiddenColumns(project, dataset);
        List<String> allowlist = project != null
                ? DatasetItemResponse.parseColumns(project.getFeatureColumnsJson())
                : List.of();
        List<String> visible = new ArrayList<>();
        if (!allowlist.isEmpty()) {
            Set<String> allowedLower = new LinkedHashSet<>();
            for (String name : allowlist) {
                if (name != null && !name.isBlank()) {
                    allowedLower.add(name.strip().toLowerCase());
                }
            }
            for (String column : datasetColumns) {
                String lower = column.toLowerCase();
                if (allowedLower.contains(lower) && !hiddenLower.contains(lower)) {
                    visible.add(column);
                }
            }
            // Keep explicitly requested columns even before any data declares them.
            for (String requested : allowlist) {
                String clean = requested == null ? "" : requested.strip();
                if (!clean.isEmpty() && !containsIgnoreCase(visible, clean)
                        && !hiddenLower.contains(clean.toLowerCase())) {
                    visible.add(clean);
                }
            }
            return visible;
        }
        for (String column : datasetColumns) {
            if (!hiddenLower.contains(column.toLowerCase())) {
                visible.add(column);
            }
        }
        return visible;
    }

    /** Row data with every hidden column removed (order-preserving). */
    public static Map<String, String> filterRowData(Map<String, String> rowData, Set<String> hiddenLower) {
        Map<String, String> filtered = new LinkedHashMap<>();
        if (rowData == null) {
            return filtered;
        }
        for (Map.Entry<String, String> entry : rowData.entrySet()) {
            if (entry.getKey() == null || hiddenLower.contains(entry.getKey().toLowerCase())) {
                continue;
            }
            filtered.put(entry.getKey(), entry.getValue());
        }
        return filtered;
    }

    /**
     * Full multi-line rendering of an item for labeling UIs and AI prompts:
     * every <em>visible</em> {@code key: value} line, otherwise the plain
     * content. Never null, never reveals hidden columns.
     */
    public static String renderVisibleItem(DatasetItem item, Project project, Dataset dataset) {
        if (item == null) {
            return "";
        }
        Set<String> hiddenLower = hiddenColumns(project, dataset);
        Map<String, String> row = DatasetItemResponse.parseRowData(item.getRowDataJson());
        Map<String, String> visible = filterRowData(row, hiddenLower);
        List<String> allowlist = project != null
                ? DatasetItemResponse.parseColumns(project.getFeatureColumnsJson())
                : List.of();
        if (!allowlist.isEmpty() && !visible.isEmpty()) {
            Set<String> allowedLower = new LinkedHashSet<>();
            for (String name : allowlist) {
                if (name != null && !name.isBlank()) {
                    allowedLower.add(name.strip().toLowerCase());
                }
            }
            visible.keySet().removeIf(key -> !allowedLower.contains(key.toLowerCase()));
        }
        if (visible.isEmpty()) {
            return item.getContent() == null ? "" : item.getContent();
        }
        StringBuilder rendered = new StringBuilder();
        // Prefer dataset column order so multi-feature rows read naturally.
        List<String> order = dataset != null
                ? DatasetItemResponse.parseColumns(dataset.getColumnsJson())
                : List.of();
        if (!order.isEmpty()) {
            for (String column : order) {
                String value = findIgnoreCase(visible, column);
                if (value == null || value.isBlank()) {
                    continue;
                }
                rendered.append(column).append(": ").append(value.strip()).append("\n");
            }
            for (Map.Entry<String, String> entry : visible.entrySet()) {
                if (!containsIgnoreCase(order, entry.getKey())
                        && entry.getValue() != null && !entry.getValue().isBlank()) {
                    rendered.append(entry.getKey()).append(": ").append(entry.getValue().strip()).append("\n");
                }
            }
        } else {
            for (Map.Entry<String, String> entry : visible.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                rendered.append(entry.getKey()).append(": ").append(entry.getValue().strip()).append("\n");
            }
        }
        String text = rendered.toString().trim();
        return text.isEmpty() && item.getContent() != null ? item.getContent() : text;
    }

    private static void addLower(Set<String> target, String name) {
        if (name != null && !name.isBlank()) {
            target.add(name.strip().toLowerCase());
        }
    }

    private static boolean containsIgnoreCase(List<String> names, String candidate) {
        for (String name : names) {
            if (name.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String findIgnoreCase(Map<String, String> map, String key) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
