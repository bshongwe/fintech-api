package com.fintech.admin.application;

import com.fintech.admin.domain.SystemAlert;
import com.fintech.admin.domain.AlertSeverity;
import com.fintech.admin.domain.AlertStatus;
import com.fintech.admin.domain.AlertCategory;
import com.fintech.admin.infrastructure.SystemAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * System Alert Management Service
 * 
 * Handles system alert creation, processing, acknowledgment, and resolution.
 * Integrates with monitoring systems and provides real-time alert capabilities.
 */
@Service
@Transactional
public class SystemAlertService {
    
    private static final Logger logger = LoggerFactory.getLogger(SystemAlertService.class);
    
    private final SystemAlertRepository systemAlertRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Autowired
    public SystemAlertService(SystemAlertRepository systemAlertRepository,
                             KafkaTemplate<String, Object> kafkaTemplate) {
        this.systemAlertRepository = systemAlertRepository;
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Create new system alert
     */
    public SystemAlert createAlert(CreateAlertRequest request) {
        SystemAlert alert = new SystemAlert(
            request.getTitle(),
            request.getDescription(),
            request.getSeverity(),
            request.getCategory(),
            request.getService()
        );
        
        alert.setMetadata(request.getMetadata());
        alert.setThresholdValue(request.getThresholdValue());
        alert.setActualValue(request.getActualValue());
        
        SystemAlert savedAlert = systemAlertRepository.save(alert);
        
        // Check for similar existing alerts to consolidate
        consolidateSimilarAlerts(savedAlert);
        
        // Publish alert notification
        publishAlertNotification(savedAlert, "ALERT_CREATED");
        
        logger.info("System alert created: {} for service: {}", savedAlert.getTitle(), savedAlert.getService());
        
        return savedAlert;
    }
    
    /**
     * Get alert by ID
     */
    @Cacheable(value = "systemAlerts", key = "#alertId")
    public Optional<SystemAlert> getAlert(UUID alertId) {
        return systemAlertRepository.findById(alertId);
    }
    
    /**
     * Get all active alerts
     */
    public Page<SystemAlert> getActiveAlerts(Pageable pageable) {
        return systemAlertRepository.findActiveAlerts(pageable);
    }
    
    /**
     * Get alerts by service
     */
    public List<SystemAlert> getAlertsByService(String service) {
        return systemAlertRepository.findByService(service);
    }
    
    /**
     * Get alerts by severity
     */
    public List<SystemAlert> getAlertsBySeverity(AlertSeverity severity) {
        return systemAlertRepository.findBySeverity(severity);
    }
    
    /**
     * Get critical alerts
     */
    public List<SystemAlert> getCriticalAlerts() {
        return systemAlertRepository.findCriticalAlerts();
    }
    
    /**
     * Get alerts by status
     */
    public Page<SystemAlert> getAlertsByStatus(AlertStatus status, Pageable pageable) {
        return systemAlertRepository.findByStatus(status, pageable);
    }
    
    /**
     * Acknowledge alert
     */
    @CacheEvict(value = "systemAlerts", key = "#alertId")
    public Optional<SystemAlert> acknowledgeAlert(UUID alertId, UUID adminUserId) {
        Optional<SystemAlert> optionalAlert = systemAlertRepository.findById(alertId);
        
        if (optionalAlert.isPresent()) {
            SystemAlert alert = optionalAlert.get();
            alert.acknowledge(adminUserId);
            
            SystemAlert savedAlert = systemAlertRepository.save(alert);
            
            // Publish notification
            publishAlertNotification(savedAlert, "ALERT_ACKNOWLEDGED");
            
            logger.info("Alert acknowledged: {} by admin: {}", alertId, adminUserId);
            
            return Optional.of(savedAlert);
        }
        
        return Optional.empty();
    }
    
    /**
     * Resolve alert
     */
    @CacheEvict(value = "systemAlerts", key = "#alertId")
    public Optional<SystemAlert> resolveAlert(UUID alertId, UUID adminUserId, String resolutionNotes) {
        Optional<SystemAlert> optionalAlert = systemAlertRepository.findById(alertId);
        
        if (optionalAlert.isPresent()) {
            SystemAlert alert = optionalAlert.get();
            alert.resolve(adminUserId, resolutionNotes);
            
            SystemAlert savedAlert = systemAlertRepository.save(alert);
            
            // Publish notification
            publishAlertNotification(savedAlert, "ALERT_RESOLVED");
            
            logger.info("Alert resolved: {} by admin: {}", alertId, adminUserId);
            
            return Optional.of(savedAlert);
        }
        
        return Optional.empty();
    }
    
    /**
     * Get alert statistics
     */
    public AlertStatistics getAlertStatistics() {
        long totalAlerts = systemAlertRepository.count();
        long openAlerts = systemAlertRepository.countByStatus(AlertStatus.OPEN);
        long acknowledgedAlerts = systemAlertRepository.countByStatus(AlertStatus.ACKNOWLEDGED);
        long resolvedAlerts = systemAlertRepository.countByStatus(AlertStatus.RESOLVED);
        long criticalAlerts = systemAlertRepository.countBySeverity(AlertSeverity.CRITICAL);
        long highAlerts = systemAlertRepository.countBySeverity(AlertSeverity.HIGH);
        
        return new AlertStatistics(totalAlerts, openAlerts, acknowledgedAlerts, 
                                 resolvedAlerts, criticalAlerts, highAlerts);
    }
    
    /**
     * Get alert statistics by service
     */
    public List<ServiceAlertStats> getAlertStatsByService(LocalDateTime since) {
        List<Object[]> stats = systemAlertRepository.getAlertStatisticsByService(since);
        
        return stats.stream()
            .collect(Collectors.groupingBy(
                stat -> (String) stat[0], // service name
                Collectors.summingLong(stat -> (Long) stat[2]) // count
            ))
            .entrySet().stream()
            .map(entry -> new ServiceAlertStats(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
    }
    
    /**
     * Get daily alert trends
     */
    public List<DailyAlertCount> getDailyAlertTrends(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        List<Object[]> dailyCounts = systemAlertRepository.getDailyAlertCounts(startDate);
        
        return dailyCounts.stream()
            .map(count -> new DailyAlertCount(
                ((java.sql.Date) count[0]).toLocalDate(),
                (Long) count[1]
            ))
            .collect(Collectors.toList());
    }
    
    /**
     * Get average resolution time by category
     */
    public List<CategoryResolutionTime> getResolutionTimesByCategory(int days) {
        LocalDateTime startTime = LocalDateTime.now().minusDays(days);
        List<Object[]> resolutionTimes = systemAlertRepository.getAverageResolutionTimeByCategory(startTime);
        
        return resolutionTimes.stream()
            .map(time -> new CategoryResolutionTime(
                (AlertCategory) time[0],
                ((Double) time[1]).longValue() // minutes
            ))
            .collect(Collectors.toList());
    }
    
    /**
     * Listen for system events and create alerts
     */
    @KafkaListener(topics = "system-events")
    public void handleSystemEvent(SystemEventMessage event) {
        try {
            if (shouldCreateAlert(event)) {
                CreateAlertRequest request = mapEventToAlert(event);
                createAlert(request);
            }
        } catch (Exception e) {
            logger.error("Error processing system event: {}", event, e);
        }
    }
    
    /**
     * Listen for fraud events and create security alerts
     */
    @KafkaListener(topics = "fraud-events")
    public void handleFraudEvent(FraudEventMessage event) {
        try {
            CreateAlertRequest request = new CreateAlertRequest();
            request.setTitle("Fraud Detection Alert");
            request.setDescription("Fraudulent activity detected: " + event.getDescription());
            request.setSeverity(AlertSeverity.HIGH);
            request.setCategory(AlertCategory.FRAUD);
            request.setService("fraud-detection");
            request.getMetadata().put("transactionId", event.getTransactionId());
            request.getMetadata().put("riskScore", event.getRiskScore().toString());
            
            createAlert(request);
        } catch (Exception e) {
            logger.error("Error processing fraud event: {}", event, e);
        }
    }
    
    /**
     * Listen for compliance events and create compliance alerts
     */
    @KafkaListener(topics = "compliance-events")
    public void handleComplianceEvent(ComplianceEventMessage event) {
        try {
            CreateAlertRequest request = new CreateAlertRequest();
            request.setTitle("Compliance Violation");
            request.setDescription("Compliance violation detected: " + event.getViolationType());
            request.setSeverity(AlertSeverity.HIGH);
            request.setCategory(AlertCategory.COMPLIANCE);
            request.setService("compliance");
            request.getMetadata().put("violationType", event.getViolationType());
            request.getMetadata().put("entityId", event.getEntityId());
            
            createAlert(request);
        } catch (Exception e) {
            logger.error("Error processing compliance event: {}", event, e);
        }
    }
    
    private void consolidateSimilarAlerts(SystemAlert newAlert) {
        // Find similar alerts in the last hour
        String titlePattern = "%" + newAlert.getTitle().substring(0, Math.min(newAlert.getTitle().length(), 20)) + "%";
        List<SystemAlert> similarAlerts = systemAlertRepository.findSimilarAlerts(titlePattern, newAlert.getService());
        
        for (SystemAlert existingAlert : similarAlerts) {
            if (existingAlert.getId().equals(newAlert.getId())) continue;
            
            if (existingAlert.isActive() && 
                existingAlert.getLastOccurrence().isAfter(LocalDateTime.now().minusHours(1))) {
                
                existingAlert.incrementOccurrence();
                systemAlertRepository.save(existingAlert);
                
                // Remove the new alert since we consolidated it
                systemAlertRepository.delete(newAlert);
                
                logger.info("Consolidated alert {} into existing alert {}", newAlert.getId(), existingAlert.getId());
                break;
            }
        }
    }
    
    private boolean shouldCreateAlert(SystemEventMessage event) {
        // Define logic for when system events should create alerts
        return event.getSeverity() != null && 
               (event.getSeverity().equals("HIGH") || event.getSeverity().equals("CRITICAL"));
    }
    
    private CreateAlertRequest mapEventToAlert(SystemEventMessage event) {
        CreateAlertRequest request = new CreateAlertRequest();
        request.setTitle(event.getTitle());
        request.setDescription(event.getDescription());
        request.setSeverity(AlertSeverity.valueOf(event.getSeverity()));
        request.setCategory(AlertCategory.SYSTEM_HEALTH);
        request.setService(event.getService());
        request.setMetadata(event.getMetadata());
        
        return request;
    }
    
    private void publishAlertNotification(SystemAlert alert, String action) {
        try {
            AlertNotification notification = new AlertNotification(
                alert.getId(),
                alert.getTitle(),
                alert.getDescription(),
                alert.getSeverity(),
                alert.getService(),
                action,
                LocalDateTime.now()
            );
            
            kafkaTemplate.send("alert-notifications", notification);
        } catch (Exception e) {
            logger.error("Failed to publish alert notification", e);
        }
    }
    
    // Request and Response DTOs
    public static class CreateAlertRequest {
        private String title;
        private String description;
        private AlertSeverity severity;
        private AlertCategory category;
        private String service;
        private Map<String, String> metadata = Map.of();
        private BigDecimal thresholdValue;
        private BigDecimal actualValue;
        
        // Getters and setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public AlertSeverity getSeverity() { return severity; }
        public void setSeverity(AlertSeverity severity) { this.severity = severity; }
        
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
    }
    
    public static class AlertStatistics {
        private final long totalAlerts;
        private final long openAlerts;
        private final long acknowledgedAlerts;
        private final long resolvedAlerts;
        private final long criticalAlerts;
        private final long highAlerts;
        
        public AlertStatistics(long totalAlerts, long openAlerts, long acknowledgedAlerts, 
                             long resolvedAlerts, long criticalAlerts, long highAlerts) {
            this.totalAlerts = totalAlerts;
            this.openAlerts = openAlerts;
            this.acknowledgedAlerts = acknowledgedAlerts;
            this.resolvedAlerts = resolvedAlerts;
            this.criticalAlerts = criticalAlerts;
            this.highAlerts = highAlerts;
        }
        
        // Getters
        public long getTotalAlerts() { return totalAlerts; }
        public long getOpenAlerts() { return openAlerts; }
        public long getAcknowledgedAlerts() { return acknowledgedAlerts; }
        public long getResolvedAlerts() { return resolvedAlerts; }
        public long getCriticalAlerts() { return criticalAlerts; }
        public long getHighAlerts() { return highAlerts; }
    }
    
    public static class ServiceAlertStats {
        private final String service;
        private final long alertCount;
        
        public ServiceAlertStats(String service, long alertCount) {
            this.service = service;
            this.alertCount = alertCount;
        }
        
        public String getService() { return service; }
        public long getAlertCount() { return alertCount; }
    }
    
    public static class DailyAlertCount {
        private final java.time.LocalDate date;
        private final long count;
        
        public DailyAlertCount(java.time.LocalDate date, long count) {
            this.date = date;
            this.count = count;
        }
        
        public java.time.LocalDate getDate() { return date; }
        public long getCount() { return count; }
    }
    
    public static class CategoryResolutionTime {
        private final AlertCategory category;
        private final long averageMinutes;
        
        public CategoryResolutionTime(AlertCategory category, long averageMinutes) {
            this.category = category;
            this.averageMinutes = averageMinutes;
        }
        
        public AlertCategory getCategory() { return category; }
        public long getAverageMinutes() { return averageMinutes; }
    }
    
    // Event Message DTOs
    public static class SystemEventMessage {
        private String title;
        private String description;
        private String severity;
        private String service;
        private Map<String, String> metadata;
        
        // Getters and setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        
        public String getService() { return service; }
        public void setService(String service) { this.service = service; }
        
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    }
    
    public static class FraudEventMessage {
        private String transactionId;
        private String description;
        private BigDecimal riskScore;
        
        // Getters and setters
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public BigDecimal getRiskScore() { return riskScore; }
        public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }
    }
    
    public static class ComplianceEventMessage {
        private String violationType;
        private String entityId;
        
        // Getters and setters
        public String getViolationType() { return violationType; }
        public void setViolationType(String violationType) { this.violationType = violationType; }
        
        public String getEntityId() { return entityId; }
        public void setEntityId(String entityId) { this.entityId = entityId; }
    }
    
    // Notification DTO
    public static class AlertNotification {
        private final UUID alertId;
        private final String title;
        private final String description;
        private final AlertSeverity severity;
        private final String service;
        private final String action;
        private final LocalDateTime timestamp;
        
        public AlertNotification(UUID alertId, String title, String description, 
                               AlertSeverity severity, String service, String action, LocalDateTime timestamp) {
            this.alertId = alertId;
            this.title = title;
            this.description = description;
            this.severity = severity;
            this.service = service;
            this.action = action;
            this.timestamp = timestamp;
        }
        
        // Getters
        public UUID getAlertId() { return alertId; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public AlertSeverity getSeverity() { return severity; }
        public String getService() { return service; }
        public String getAction() { return action; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}
