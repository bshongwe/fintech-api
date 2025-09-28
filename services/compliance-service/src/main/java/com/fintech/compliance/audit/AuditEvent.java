package com.fintech.compliance.audit;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

/**
 * Immutable audit event for compliance tracking.
 * Supports PCI DSS, PSD2, and regulatory audit requirements.
 */
@Entity
@Table(name = "audit_events", indexes = {
    @Index(name = "idx_event_type", columnList = "eventType"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_resource_id", columnList = "resourceId")
})
public class AuditEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String eventType;
    
    @Column(nullable = false)
    private Instant timestamp;
    
    @Column
    private String userId;
    
    @Column
    private String sessionId;
    
    @Column
    private String resourceId;
    
    @Column
    private String resourceType;
    
    @Column
    @Enumerated(EnumType.STRING)
    private AuditLevel level;
    
    @Column(length = 1000)
    private String description;
    
    @Column
    private String sourceIp;
    
    @Column
    private String userAgent;
    
    @Column(columnDefinition = "TEXT")
    @JsonProperty("metadata")
    private String metadataJson;
    
    @Column
    private String outcome; // SUCCESS, FAILURE, PENDING
    
    @Column
    private String riskScore;
    
    @Column
    private String complianceFlags; // PCI_RELEVANT, PSD2_RELEVANT, etc.
    
    // Constructors
    protected AuditEvent() {}
    
    public AuditEvent(String eventType, String userId, String resourceId, 
                     String resourceType, AuditLevel level, String description) {
        this.eventType = eventType;
        this.userId = userId;
        this.resourceId = resourceId;
        this.resourceType = resourceType;
        this.level = level;
        this.description = description;
        this.timestamp = Instant.now();
        this.outcome = "SUCCESS";
    }
    
    // Getters (no setters - immutable after creation)
    public String getId() { return id; }
    public String getEventType() { return eventType; }
    public Instant getTimestamp() { return timestamp; }
    public String getUserId() { return userId; }
    public String getSessionId() { return sessionId; }
    public String getResourceId() { return resourceId; }
    public String getResourceType() { return resourceType; }
    public AuditLevel getLevel() { return level; }
    public String getDescription() { return description; }
    public String getSourceIp() { return sourceIp; }
    public String getUserAgent() { return userAgent; }
    public String getMetadataJson() { return metadataJson; }
    public String getOutcome() { return outcome; }
    public String getRiskScore() { return riskScore; }
    public String getComplianceFlags() { return complianceFlags; }
    
    public enum AuditLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
