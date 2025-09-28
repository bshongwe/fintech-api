package com.fintech.reporting.core;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Report execution instance and result metadata
 * Tracks report generation history and caching
 */
@Entity
@Table(name = "report_executions", indexes = {
    @Index(name = "idx_definition_id", columnList = "definitionId"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_execution_time", columnList = "executionTime"),
    @Index(name = "idx_requested_by", columnList = "requestedBy")
})
public class ReportExecution {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String definitionId;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ExecutionStatus status;
    
    @Column(columnDefinition = "TEXT")
    private String parameters; // JSON parameters used
    
    @Column
    private String requestedBy;
    
    @Column
    private String sessionId;
    
    @Column
    private Instant requestedAt;
    
    @Column
    private Instant startedAt;
    
    @Column
    private Instant completedAt;
    
    @Column
    private Long executionTimeMs;
    
    @Column
    private Integer rowCount;
    
    @Column
    private Long fileSizeBytes;
    
    @Column
    private String outputFormat; // PDF, EXCEL, CSV, JSON
    
    @Column
    private String storageLocation; // S3 path or file path
    
    @Column
    private String downloadUrl;
    
    @Column
    private Instant expiresAt; // When cached result expires
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(columnDefinition = "TEXT")
    private String executionMetadata; // JSON with additional metadata
    
    public enum ExecutionStatus {
        REQUESTED,
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
        EXPIRED
    }
    
    // Constructors
    protected ReportExecution() {}
    
    public ReportExecution(String definitionId, String requestedBy, String parameters) {
        this.definitionId = definitionId;
        this.requestedBy = requestedBy;
        this.parameters = parameters;
        this.status = ExecutionStatus.REQUESTED;
        this.requestedAt = Instant.now();
    }
    
    // Business methods
    public void markStarted() {
        this.status = ExecutionStatus.RUNNING;
        this.startedAt = Instant.now();
    }
    
    public void markCompleted(int rowCount, long fileSizeBytes, String storageLocation) {
        this.status = ExecutionStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.rowCount = rowCount;
        this.fileSizeBytes = fileSizeBytes;
        this.storageLocation = storageLocation;
        
        if (this.startedAt != null) {
            this.executionTimeMs = java.time.Duration.between(this.startedAt, this.completedAt).toMillis();
        }
    }
    
    public void markFailed(String errorMessage) {
        this.status = ExecutionStatus.FAILED;
        this.completedAt = Instant.now();
        this.errorMessage = errorMessage;
        
        if (this.startedAt != null) {
            this.executionTimeMs = java.time.Duration.between(this.startedAt, this.completedAt).toMillis();
        }
    }
    
    public boolean isCompleted() {
        return status == ExecutionStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return status == ExecutionStatus.FAILED;
    }
    
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
    
    public boolean isInProgress() {
        return status == ExecutionStatus.RUNNING || status == ExecutionStatus.QUEUED;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public String getDefinitionId() { return definitionId; }
    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }
    
    public String getParameters() { return parameters; }
    public String getRequestedBy() { return requestedBy; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Long getExecutionTimeMs() { return executionTimeMs; }
    
    public Integer getRowCount() { return rowCount; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    
    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
    
    public String getStorageLocation() { return storageLocation; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    
    public String getErrorMessage() { return errorMessage; }
    public String getExecutionMetadata() { return executionMetadata; }
    public void setExecutionMetadata(String executionMetadata) { this.executionMetadata = executionMetadata; }
}
