package com.labelmate.labelmate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Single key-value row for admin-managed settings (AI provider key, model,
 * base URL, enabled flag). Applied on backend restart; never logged.
 */
@Entity
@Table(name = "app_settings")
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 128)
    private String key;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected AppSetting() {
    }

    public AppSetting(String key, String value, LocalDateTime updatedAt) {
        this.key = key;
        this.value = value;
        this.updatedAt = updatedAt;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
