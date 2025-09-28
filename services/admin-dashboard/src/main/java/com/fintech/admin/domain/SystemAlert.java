package com.fintech.admin.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * System Alert Entity
 * 
 * Represents system-wide alerts for monitoring, compliance, and operational issues.
 * Supports categorization, severity levels, and automated resolution tracking.
 */
@Entity
@Table(name = "system_alerts", indexes = {
    @Index(name = "idx_alert_severity", columnList = "severity"),
    @Index(name = "idx_alert_status", columnList = "status"),
    @Index(name = "idx_alert_service", columnList = "service"),
    @Index(name = "idx_alert_created_at", columnList = "created_at"),
    @Index(name = "idx_alert_resolved_at", columnList = "resolved_at")
})
@EntityListeners(AuditingEntityListener.class)
public class SystemAlert {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, length = 100)
    @NotBlank(message = "Title is required")
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "Severity is required")
    private AlertSeverity severity;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status = AlertStatus.OPEN;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "Category is required")
    private AlertCategory category;
    
    @Column(nullable = false, length = 50)
    @NotBlank(message = "Service is required")
    private String service; // Which service generated this alert
    
    @ElementCollection
    @CollectionTable(name = "alert_metadata", joinColumns = @JoinColumn(name = "alert_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private Map<String, String> metadata = new HashMap<>();
    
    @Column(name = "threshold_value", precision = 19, scale = 4)
    private BigDecimal thresholdValue;
    
    @Column(name = "actual_value", precision = 19, scale = 4)
    private BigDecimal actualValue;
    
    @Column(name = "occurrence_count")
    private Integer occurrenceCount = 1;
    
    @Column(name = "first_occurrence")
    private LocalDateTime firstOccurrence;
    
    @Column(name = "last_occurrence")
    private LocalDateTime lastOccurrence;
    
    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;
    
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;
    
    @Column(name = "resolved_by")
    private UUID resolvedBy;
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    
    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public SystemAlert() {}
    
    public SystemAlert(String title, String description, AlertSeverity severity, 
                      AlertCategory category, String service) {
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.category = category;
        this.service = service;
        this.firstOccurrence = LocalDateTime.now();
        this.lastOccurrence = LocalDateTime.now();
    }
    
    // Business Methods
    public void acknowledge(UUID adminUserId) {
        this.acknowledgedBy = adminUserId;
        this.acknowledgedAt = LocalDateTime.now();
        if (this.status == AlertStatus.OPEN) {
            this.status = AlertStatus.ACKNOWLEDGED;
        }
    }
    
    public void resolve(UUID adminUserId, String notes) {
        this.resolvedBy = adminUserId;
        this.resolvedAt = LocalDateTime.now();
        this.resolutionNotes = notes;
        this.status = AlertStatus.RESOLVED;
    }
    
    public void incrementOccurrence() {
        this.occurrenceCount++;
        this.lastOccurrence = LocalDateTime.now();
    }
    
    public boolean isActive() {
        return status == AlertStatus.OPEN || status == AlertStatus.ACKNOWLEDGED;
    }
    
    public boolean isCritical() {
        return severity == AlertSeverity.CRITICAL || severity == AlertSeverity.HIGH;
    }
    
    public long getOpenDurationMinutes() {
        LocalDateTime endTime = resolvedAt != null ? resolvedAt : LocalDateTime.now();
        return java.time.Duration.between(createdAt, endTime).toMinutes();
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public AlertSeverity getSeverity() { return severity; }
    public void setSeverity(AlertSeverity severity) { this.severity = severity; }
    
    public AlertStatus getStatus() { return status; }
    public void setStatus(AlertStatus status) { this.status = status; }
    
    public AlertCategory getCategory() { return category; }
    public void setCategory(AlertCategory category) { this.category = category; }
    
    public String getService() { return service; }
    public void setService(String service) { this.service = service; }
    
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    
    public BigDecimal getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(BigDecimal thresholdValue) { this.thresholdValue = thresholdValue; }
    
    public BigDecimal getActualValue() { return actualValue; }
    public void setActualValue(BigDecimal actualValue) { this.actualValue = actualValue; }
    
    public Integer getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(Integer occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    
    public LocalDateTime getFirstOccurrence() { return firstOccurrence; }
    public void setFirstOccurrence(LocalDateTime firstOccurrence) { this.firstOccurrence = firstOccurrence; }
    
    public LocalDateTime getLastOccurrence() { return lastOccurrence; }
    public void setLastOccurrence(LocalDateTime lastOccurrence) { this.lastOccurrence = lastOccurrence; }
    
    public UUID getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(UUID acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    
    public UUID getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(UUID resolvedBy) { this.resolvedBy = resolvedBy; }
    
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    
    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

enum AlertSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED,
    CLOSED
}

enum AlertCategory {
    SYSTEM_HEALTH,
    SECURITY,
    COMPLIANCE,
    FRAUD,
    PERFORMANCE,
    DATA_QUALITY,
    INTEGRATION,
    BUSINESS_RULE
}
