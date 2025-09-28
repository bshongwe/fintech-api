package com.fintech.admin.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * System Metrics Entity
 * 
 * Stores aggregated system metrics for monitoring dashboards.
 * Supports both real-time and historical metric tracking.
 */
@Entity
@Table(name = "system_metrics", indexes = {
    @Index(name = "idx_metrics_service", columnList = "service"),
    @Index(name = "idx_metrics_name", columnList = "metric_name"),
    @Index(name = "idx_metrics_timestamp", columnList = "timestamp"),
    @Index(name = "idx_metrics_service_name_timestamp", columnList = "service, metric_name, timestamp")
})
@EntityListeners(AuditingEntityListener.class)
public class SystemMetrics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, length = 50)
    @NotNull(message = "Service is required")
    private String service;
    
    @Column(name = "metric_name", nullable = false, length = 100)
    @NotNull(message = "Metric name is required")
    private String metricName;
    
    @Column(name = "metric_value", precision = 19, scale = 4)
    private BigDecimal metricValue;
    
    @Column(name = "metric_unit", length = 20)
    private String metricUnit;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false)
    @NotNull(message = "Metric type is required")
    private MetricType metricType;
    
    @ElementCollection
    @CollectionTable(name = "metric_tags", joinColumns = @JoinColumn(name = "metric_id"))
    @MapKeyColumn(name = "tag_key")
    @Column(name = "tag_value")
    private Map<String, String> tags = new HashMap<>();
    
    @Column(nullable = false)
    @NotNull(message = "Timestamp is required")
    private LocalDateTime timestamp;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public SystemMetrics() {}
    
    public SystemMetrics(String service, String metricName, BigDecimal metricValue, 
                        String metricUnit, MetricType metricType) {
        this.service = service;
        this.metricName = metricName;
        this.metricValue = metricValue;
        this.metricUnit = metricUnit;
        this.metricType = metricType;
        this.timestamp = LocalDateTime.now();
    }
    
    // Business Methods
    public void addTag(String key, String value) {
        this.tags.put(key, value);
    }
    
    public String getTag(String key) {
        return this.tags.get(key);
    }
    
    public boolean hasTag(String key) {
        return this.tags.containsKey(key);
    }
    
    public String getFormattedValue() {
        if (metricUnit != null) {
            return metricValue + " " + metricUnit;
        }
        return metricValue.toString();
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getService() { return service; }
    public void setService(String service) { this.service = service; }
    
    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }
    
    public BigDecimal getMetricValue() { return metricValue; }
    public void setMetricValue(BigDecimal metricValue) { this.metricValue = metricValue; }
    
    public String getMetricUnit() { return metricUnit; }
    public void setMetricUnit(String metricUnit) { this.metricUnit = metricUnit; }
    
    public MetricType getMetricType() { return metricType; }
    public void setMetricType(MetricType metricType) { this.metricType = metricType; }
    
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

enum MetricType {
    COUNTER,
    GAUGE,
    HISTOGRAM,
    TIMER
}
