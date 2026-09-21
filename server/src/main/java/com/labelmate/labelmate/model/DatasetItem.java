package com.labelmate.labelmate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "dataset_items")
public class DatasetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    /**
     * Item payload. For unstructured text this holds the full document; for
     * tabular rows it holds a short human-readable summary while
     * {@link #rowDataJson} keeps every column. TEXT so long documents do not
     * hit VARCHAR limits.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Full multi-column row payload as a JSON object string, e.g.
     * {@code {"review":"...","rating":"5"}}. Null for single-text and image
     * items. {@link #content} always holds a short human-readable summary so
     * older consumers keep working.
     */
    @Column(name = "row_data_json", columnDefinition = "TEXT")
    private String rowDataJson;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Column(name = "media_type", length = 64)
    private String mediaType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected DatasetItem() {
    }

    public DatasetItem(Dataset dataset, String content, LocalDateTime createdAt) {
        this.dataset = dataset;
        this.content = content;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRowDataJson() {
        return rowDataJson;
    }

    public void setRowDataJson(String rowDataJson) {
        this.rowDataJson = rowDataJson;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
