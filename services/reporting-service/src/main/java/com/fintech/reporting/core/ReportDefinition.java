package com.fintech.reporting.core;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;

/**
 * Report definition and metadata
 * Supports dynamic report generation with caching and scheduling
 */
@Entity
@Table(name = "report_definitions", indexes = {
    @Index(name = "idx_category", columnList = "category"),
    @Index(name = "idx_type", columnList = "type"),
    @Index(name = "idx_active", columnList = "active")
})
public class ReportDefinition {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(nullable = false)
    private String displayName;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ReportCategory category;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ReportType type;
    
    @Column(columnDefinition = "TEXT")
    private String queryTemplate; // SQL template with parameters
    
    @Column(columnDefinition = "TEXT")
    private String parametersSchema; // JSON schema for parameters
    
    @Column(columnDefinition = "TEXT")
    private String columnsDefinition; // JSON definition of columns
    
    @Column
    private String cronSchedule; // For scheduled reports
    
    @Column(nullable = false)
    private boolean active = true;
    
    @Column
    private boolean complianceReport = false; // PCI DSS, PSD2, etc.
    
    @Column
    private String requiredRole; // RBAC for report access
    
    @Column
    private Integer cacheTtlMinutes; // Cache duration
    
    @Column
    private Instant createdAt;
    
    @Column
    private Instant updatedAt;
    
    @Column
    private String createdBy;
    
    public enum ReportCategory {
        COMPLIANCE,
        FRAUD_DETECTION,
        FINANCIAL,
        OPERATIONAL,
        AUDIT,
        RISK_MANAGEMENT,
        PERFORMANCE,
        REGULATORY
    }
    
    public enum ReportType {
        TABULAR,        // Standard table format
        SUMMARY,        // Aggregated summary
        TIME_SERIES,    // Time-based data
        DRILL_DOWN,     // Hierarchical data
        DASHBOARD,      // Key metrics
        EXPORT_ONLY     // Data export without visualization
    }
    
    // Constructors
    protected ReportDefinition() {}
    
    public ReportDefinition(String name, String displayName, ReportCategory category, 
                           ReportType type, String queryTemplate) {
        this.name = name;
        this.displayName = displayName;
        this.category = category;
        this.type = type;
        this.queryTemplate = queryTemplate;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    // Business methods
    public boolean isScheduled() {
        return cronSchedule != null && !cronSchedule.trim().isEmpty();
    }
    
    public boolean isCacheable() {
        return cacheTtlMinutes != null && cacheTtlMinutes > 0;
    }
    
    public boolean requiresCompliance() {
        return complianceReport;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; this.updatedAt = Instant.now(); }
    
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; this.updatedAt = Instant.now(); }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; this.updatedAt = Instant.now(); }
    
    public ReportCategory getCategory() { return category; }
    public void setCategory(ReportCategory category) { this.category = category; this.updatedAt = Instant.now(); }
    
    public ReportType getType() { return type; }
    public void setType(ReportType type) { this.type = type; this.updatedAt = Instant.now(); }
    
    public String getQueryTemplate() { return queryTemplate; }
    public void setQueryTemplate(String queryTemplate) { this.queryTemplate = queryTemplate; this.updatedAt = Instant.now(); }
    
    public String getParametersSchema() { return parametersSchema; }
    public void setParametersSchema(String parametersSchema) { this.parametersSchema = parametersSchema; this.updatedAt = Instant.now(); }
    
    public String getColumnsDefinition() { return columnsDefinition; }
    public void setColumnsDefinition(String columnsDefinition) { this.columnsDefinition = columnsDefinition; this.updatedAt = Instant.now(); }
    
    public String getCronSchedule() { return cronSchedule; }
    public void setCronSchedule(String cronSchedule) { this.cronSchedule = cronSchedule; this.updatedAt = Instant.now(); }
    
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; this.updatedAt = Instant.now(); }
    
    public boolean isComplianceReport() { return complianceReport; }
    public void setComplianceReport(boolean complianceReport) { this.complianceReport = complianceReport; this.updatedAt = Instant.now(); }
    
    public String getRequiredRole() { return requiredRole; }
    public void setRequiredRole(String requiredRole) { this.requiredRole = requiredRole; this.updatedAt = Instant.now(); }
    
    public Integer getCacheTtlMinutes() { return cacheTtlMinutes; }
    public void setCacheTtlMinutes(Integer cacheTtlMinutes) { this.cacheTtlMinutes = cacheTtlMinutes; this.updatedAt = Instant.now(); }
    
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
