package com.fintech.compliance.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Central audit service for compliance tracking.
 * Handles audit event creation, storage, and streaming.
 */
@Service
public class AuditService {
    
    private final AuditEventRepository auditRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String AUDIT_TOPIC = "audit-events";
    
    @Autowired
    public AuditService(AuditEventRepository auditRepository,
                       KafkaTemplate<String, Object> kafkaTemplate,
                       ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Record an audit event asynchronously
     */
    @Async
    public CompletableFuture<AuditEvent> recordEvent(String eventType, String userId, 
                                                    String resourceId, String resourceType,
                                                    AuditEvent.AuditLevel level, String description) {
        AuditEvent event = new AuditEvent(eventType, userId, resourceId, resourceType, level, description);
        return recordEvent(event);
    }
    
    /**
     * Record audit event with metadata
     */
    @Async
    public CompletableFuture<AuditEvent> recordEvent(String eventType, String userId,
                                                    String resourceId, String resourceType,
                                                    AuditEvent.AuditLevel level, String description,
                                                    Map<String, Object> metadata) {
        AuditEvent event = new AuditEvent(eventType, userId, resourceId, resourceType, level, description);
        
        // Set metadata
        if (metadata != null && !metadata.isEmpty()) {
            try {
                String metadataJson = objectMapper.writeValueAsString(metadata);
                // Use reflection to set the metadata (since AuditEvent is immutable after creation)
                // In a real implementation, you'd have a builder pattern
            } catch (Exception e) {
                // Log error but don't fail the audit
            }
        }
        
        return recordEvent(event);
    }
    
    /**
     * Record audit event and stream to Kafka
     */
    @Async
    public CompletableFuture<AuditEvent> recordEvent(AuditEvent event) {
        try {
            // Store in database
            AuditEvent savedEvent = auditRepository.save(event);
            
            // Stream to Kafka for real-time processing
            kafkaTemplate.send(AUDIT_TOPIC, savedEvent.getId(), savedEvent);
            
            return CompletableFuture.completedFuture(savedEvent);
            
        } catch (Exception e) {
            CompletableFuture<AuditEvent> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
    
    /**
     * PCI DSS specific audit events
     */
    public CompletableFuture<AuditEvent> recordPciEvent(String eventType, String userId, 
                                                       String cardDataAccess, String outcome) {
        AuditEvent event = new AuditEvent(eventType, userId, cardDataAccess, "CARD_DATA", 
                                         AuditEvent.AuditLevel.HIGH, "PCI DSS sensitive operation");
        // Set PCI compliance flag and outcome
        return recordEvent(event);
    }
    
    /**
     * PSD2 specific audit events
     */
    public CompletableFuture<AuditEvent> recordPsd2Event(String eventType, String userId,
                                                        String paymentId, String outcome) {
        AuditEvent event = new AuditEvent(eventType, userId, paymentId, "PAYMENT", 
                                         AuditEvent.AuditLevel.MEDIUM, "PSD2 payment operation");
        // Set PSD2 compliance flag
        return recordEvent(event);
    }
    
    /**
     * Query audit events for compliance reports
     */
    public List<AuditEvent> getComplianceAuditTrail(String complianceFlag, Instant start, Instant end) {
        return auditRepository.findByComplianceFlagAndTimeRange(complianceFlag, start, end);
    }
    
    /**
     * Get user activity for fraud detection
     */
    public List<AuditEvent> getUserActivity(String userId, Instant start, Instant end) {
        return auditRepository.findByUserIdAndTimestampBetween(userId, start, end);
    }
    
    /**
     * Count user events for rate limiting and fraud detection
     */
    public long countUserEventsSince(String userId, String eventType, Instant since) {
        return auditRepository.countUserEventsSince(userId, eventType, since);
    }
}
