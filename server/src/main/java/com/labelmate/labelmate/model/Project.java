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
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "label_type", length = 50)
    private String labelType;

    /**
     * Dataset column holding the ground-truth answer for this project (e.g.
     * {@code "rating"}). It is hidden from every labeling read — task items,
     * AI prompts, the workspace — so annotators never see the answer before
     * labeling. Null/blank falls back to the dataset's own label column.
     */
    @Column(name = "label_column", length = 100)
    private String labelColumn;

    /**
     * Explicit allowlist of dataset columns shown as labeling features, as a
     * JSON array string (e.g. {@code ["review_title","review_text"]}).
     * Null/blank means "every dataset column except the hidden ones", so
     * single-feature and multi-feature datasets both work with no config.
     */
    @Column(name = "feature_columns_json", columnDefinition = "TEXT")
    private String featureColumnsJson;

    /**
     * Extra dataset columns to hide from labeling reads (in addition to the
     * label column), as a JSON array string. Useful for internal ids or
     * metadata annotators must not see.
     */
    @Column(name = "hidden_columns_json", columnDefinition = "TEXT")
    private String hiddenColumnsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected Project() {
    }

    public Project(Dataset dataset, User owner, String name, ProjectStatus status, LocalDateTime createdAt) {
        this.dataset = dataset;
        this.owner = owner;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Dataset getDataset() {
        return dataset;
    }

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
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

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getLabelType() {
        return labelType;
    }

    public void setLabelType(String labelType) {
        this.labelType = labelType;
    }

    public String getLabelColumn() {
        return labelColumn;
    }

    public void setLabelColumn(String labelColumn) {
        this.labelColumn = labelColumn;
    }

    public String getFeatureColumnsJson() {
        return featureColumnsJson;
    }

    public void setFeatureColumnsJson(String featureColumnsJson) {
        this.featureColumnsJson = featureColumnsJson;
    }

    public String getHiddenColumnsJson() {
        return hiddenColumnsJson;
    }

    public void setHiddenColumnsJson(String hiddenColumnsJson) {
        this.hiddenColumnsJson = hiddenColumnsJson;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
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
