package com.labelmate.labelmate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "datasets")
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DatasetStatus status;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    /**
     * Broad dataset kind driving the ingest/labeling UI: {@code TEXT} (one
     * free-text feature per item), {@code TABULAR} (a CSV row with several
     * named feature columns), {@code IMAGE} (pictures with optional
     * captions), or {@code MIXED} (a combination added over time). Stored as
     * free text (not an enum) so new kinds never need a migration; null
     * means "unknown yet" and the UI infers it from the items present.
     */
    @Column(name = "dataset_type", length = 20)
    private String datasetType;

    /**
     * Typed feature schema for multi-feature (multi-column) datasets, stored
     * as a JSON array string, e.g.
     * {@code [{"name":"review_title","type":"TEXT"},{"name":"price","type":"NUMBER"}]}.
     * Supported types: TEXT, NUMBER, CATEGORY, BOOLEAN, IMAGE. Null/blank
     * means untyped — every column behaves as TEXT. Entries are advisory
     * (they drive input widgets and validation hints); ingest never rejects
     * data for a type mismatch.
     */
    @Column(name = "features_json", columnDefinition = "TEXT")
    private String featuresJson;

    /**
     * Name of the ground-truth (answer) column, if any — e.g. {@code "rating"}
     * in a {@code [review_title, review_text, rating]} CSV. The column stays
     * in storage and exports, but labeling reads (tasks, AI prompts, the
     * workspace) must hide it so annotators never see the answer before
     * labeling. Null/blank means the dataset carries no ground truth.
     */
    @Column(name = "label_column", length = 100)
    private String labelColumn;

    /**
     * Ordered column names for tabular (multi-column) datasets, stored as a
     * JSON array string, e.g. {@code ["review","rating"]}. Null/blank means a
     * single-content dataset where each item is free text (or an image) in
     * {@code DatasetItem.content}.
     */
    @Column(name = "columns_json", columnDefinition = "TEXT")
    private String columnsJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected Dataset() {
    }

    public Dataset(User owner, String name, DatasetStatus status, LocalDateTime createdAt) {
        this.owner = owner;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DatasetStatus getStatus() {
        return status;
    }

    public void setStatus(DatasetStatus status) {
        this.status = status;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public String getDatasetType() {
        return datasetType;
    }

    public void setDatasetType(String datasetType) {
        this.datasetType = datasetType;
    }

    public String getFeaturesJson() {
        return featuresJson;
    }

    public void setFeaturesJson(String featuresJson) {
        this.featuresJson = featuresJson;
    }

    public String getLabelColumn() {
        return labelColumn;
    }

    public void setLabelColumn(String labelColumn) {
        this.labelColumn = labelColumn;
    }

    public String getColumnsJson() {
        return columnsJson;
    }

    public void setColumnsJson(String columnsJson) {
        this.columnsJson = columnsJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
