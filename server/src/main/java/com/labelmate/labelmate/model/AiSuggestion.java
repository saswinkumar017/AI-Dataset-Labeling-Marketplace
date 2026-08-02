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
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_suggestions")
public class AiSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_label_id")
    private Label suggestedLabel;

    @Column(name = "suggested_label_name", length = 100)
    private String suggestedLabelName;

    @Column(precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(length = 100)
    private String model;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AiSuggestion() {
    }

    public AiSuggestion(Task task, LocalDateTime createdAt) {
        this.task = task;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public Label getSuggestedLabel() {
        return suggestedLabel;
    }

    public void setSuggestedLabel(Label suggestedLabel) {
        this.suggestedLabel = suggestedLabel;
    }

    public String getSuggestedLabelName() {
        return suggestedLabelName;
    }

    public void setSuggestedLabelName(String suggestedLabelName) {
        this.suggestedLabelName = suggestedLabelName;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
