package com.fintech.admin.infrastructure;

import com.fintech.admin.domain.SystemAlert;
import com.fintech.admin.domain.AlertSeverity;
import com.fintech.admin.domain.AlertStatus;
import com.fintech.admin.domain.AlertCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * System Alert Repository
 * 
 * Provides data access operations for system alert management and monitoring.
 */
@Repository
public interface SystemAlertRepository extends JpaRepository<SystemAlert, UUID> {
    
    /**
     * Find alerts by status
     */
    List<SystemAlert> findByStatus(AlertStatus status);
    
    /**
     * Find alerts by status with pagination
     */
    Page<SystemAlert> findByStatus(AlertStatus status, Pageable pageable);
    
    /**
     * Find alerts by severity
     */
    List<SystemAlert> findBySeverity(AlertSeverity severity);
    
    /**
     * Find alerts by category
     */
    List<SystemAlert> findByCategory(AlertCategory category);
    
    /**
     * Find alerts by service
     */
    List<SystemAlert> findByService(String service);
    
    /**
     * Find active alerts (OPEN or ACKNOWLEDGED)
     */
    @Query("SELECT a FROM SystemAlert a WHERE a.status IN ('OPEN', 'ACKNOWLEDGED')")
    List<SystemAlert> findActiveAlerts();
    
    /**
     * Find active alerts with pagination
     */
    @Query("SELECT a FROM SystemAlert a WHERE a.status IN ('OPEN', 'ACKNOWLEDGED')")
    Page<SystemAlert> findActiveAlerts(Pageable pageable);
    
    /**
     * Find critical alerts
     */
    @Query("SELECT a FROM SystemAlert a WHERE a.severity IN ('HIGH', 'CRITICAL') AND a.status IN ('OPEN', 'ACKNOWLEDGED')")
    List<SystemAlert> findCriticalAlerts();
    
    /**
     * Find alerts by service and status
     */
    List<SystemAlert> findByServiceAndStatus(String service, AlertStatus status);
    
    /**
     * Find alerts created within time range
     */
    @Query("SELECT a FROM SystemAlert a WHERE a.createdAt BETWEEN :startTime AND :endTime")
    List<SystemAlert> findAlertsCreatedBetween(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Find unacknowledged alerts older than threshold
     */
    @Query("SELECT a FROM SystemAlert a WHERE a.status = 'OPEN' AND a.createdAt < :threshold")
    List<SystemAlert> findUnacknowledgedAlertsOlderThan(@Param("threshold") LocalDateTime threshold);
    
    /**
     * Find similar alerts by title pattern
     */
    @Query("SELECT a FROM SystemAlert a WHERE a.title LIKE :titlePattern AND a.service = :service")
    List<SystemAlert> findSimilarAlerts(@Param("titlePattern") String titlePattern, @Param("service") String service);
    
    /**
     * Count alerts by status
     */
    long countByStatus(AlertStatus status);
    
    /**
     * Count alerts by severity
     */
    long countBySeverity(AlertSeverity severity);
    
    /**
     * Count alerts by category
     */
    long countByCategory(AlertCategory category);
    
    /**
     * Count alerts by service
     */
    long countByService(String service);
    
    /**
     * Count active alerts by service
     */
    @Query("SELECT COUNT(a) FROM SystemAlert a WHERE a.service = :service AND a.status IN ('OPEN', 'ACKNOWLEDGED')")
    long countActiveAlertsByService(@Param("service") String service);
    
    /**
     * Get alert statistics by service
     */
    @Query("SELECT a.service, a.severity, COUNT(a) FROM SystemAlert a " +
           "WHERE a.createdAt >= :startTime " +
           "GROUP BY a.service, a.severity")
    List<Object[]> getAlertStatisticsByService(@Param("startTime") LocalDateTime startTime);
    
    /**
     * Get daily alert counts
     */
    @Query("SELECT DATE(a.createdAt), COUNT(a) FROM SystemAlert a " +
           "WHERE a.createdAt >= :startDate " +
           "GROUP BY DATE(a.createdAt) " +
           "ORDER BY DATE(a.createdAt)")
    List<Object[]> getDailyAlertCounts(@Param("startDate") LocalDateTime startDate);
    
    /**
     * Get average resolution time by category
     */
    @Query("SELECT a.category, AVG(TIMESTAMPDIFF(MINUTE, a.createdAt, a.resolvedAt)) " +
           "FROM SystemAlert a " +
           "WHERE a.resolvedAt IS NOT NULL AND a.createdAt >= :startTime " +
           "GROUP BY a.category")
    List<Object[]> getAverageResolutionTimeByCategory(@Param("startTime") LocalDateTime startTime);
    
    /**
     * Acknowledge alert
     */
    @Modifying
    @Query("UPDATE SystemAlert a SET a.acknowledgedBy = :adminUserId, a.acknowledgedAt = :acknowledgedAt, a.status = 'ACKNOWLEDGED' " +
           "WHERE a.id = :alertId AND a.status = 'OPEN'")
    void acknowledgeAlert(@Param("alertId") UUID alertId, 
                         @Param("adminUserId") UUID adminUserId, 
                         @Param("acknowledgedAt") LocalDateTime acknowledgedAt);
    
    /**
     * Resolve alert
     */
    @Modifying
    @Query("UPDATE SystemAlert a SET a.resolvedBy = :adminUserId, a.resolvedAt = :resolvedAt, " +
           "a.resolutionNotes = :notes, a.status = 'RESOLVED' " +
           "WHERE a.id = :alertId")
    void resolveAlert(@Param("alertId") UUID alertId, 
                     @Param("adminUserId") UUID adminUserId, 
                     @Param("resolvedAt") LocalDateTime resolvedAt,
                     @Param("notes") String notes);
    
    /**
     * Update alert occurrence count
     */
    @Modifying
    @Query("UPDATE SystemAlert a SET a.occurrenceCount = a.occurrenceCount + 1, a.lastOccurrence = :lastOccurrence " +
           "WHERE a.id = :alertId")
    void incrementOccurrenceCount(@Param("alertId") UUID alertId, @Param("lastOccurrence") LocalDateTime lastOccurrence);
}
