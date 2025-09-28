package com.fintech.admin.infrastructure;

import com.fintech.admin.domain.SystemMetrics;
import com.fintech.admin.domain.MetricType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * System Metrics Repository
 * 
 * Provides data access operations for system metrics storage and aggregation.
 */
@Repository
public interface SystemMetricsRepository extends JpaRepository<SystemMetrics, UUID> {
    
    /**
     * Find metrics by service
     */
    List<SystemMetrics> findByService(String service);
    
    /**
     * Find metrics by service and metric name
     */
    List<SystemMetrics> findByServiceAndMetricName(String service, String metricName);
    
    /**
     * Find metrics by service, metric name, and time range
     */
    @Query("SELECT m FROM SystemMetrics m WHERE m.service = :service AND m.metricName = :metricName " +
           "AND m.timestamp BETWEEN :startTime AND :endTime ORDER BY m.timestamp")
    List<SystemMetrics> findByServiceAndMetricNameAndTimestampBetween(
        @Param("service") String service,
        @Param("metricName") String metricName,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Find latest metric value for service and metric name
     */
    @Query("SELECT m FROM SystemMetrics m WHERE m.service = :service AND m.metricName = :metricName " +
           "ORDER BY m.timestamp DESC LIMIT 1")
    Optional<SystemMetrics> findLatestMetric(@Param("service") String service, @Param("metricName") String metricName);
    
    /**
     * Find metrics by type
     */
    List<SystemMetrics> findByMetricType(MetricType metricType);
    
    /**
     * Find metrics within time range
     */
    @Query("SELECT m FROM SystemMetrics m WHERE m.timestamp BETWEEN :startTime AND :endTime ORDER BY m.timestamp")
    List<SystemMetrics> findMetricsBetween(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Find metrics with pagination
     */
    Page<SystemMetrics> findByServiceOrderByTimestampDesc(String service, Pageable pageable);
    
    /**
     * Get average metric value over time period
     */
    @Query("SELECT AVG(m.metricValue) FROM SystemMetrics m WHERE m.service = :service " +
           "AND m.metricName = :metricName AND m.timestamp BETWEEN :startTime AND :endTime")
    BigDecimal getAverageMetricValue(
        @Param("service") String service,
        @Param("metricName") String metricName,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Get maximum metric value over time period
     */
    @Query("SELECT MAX(m.metricValue) FROM SystemMetrics m WHERE m.service = :service " +
           "AND m.metricName = :metricName AND m.timestamp BETWEEN :startTime AND :endTime")
    BigDecimal getMaxMetricValue(
        @Param("service") String service,
        @Param("metricName") String metricName,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Get minimum metric value over time period
     */
    @Query("SELECT MIN(m.metricValue) FROM SystemMetrics m WHERE m.service = :service " +
           "AND m.metricName = :metricName AND m.timestamp BETWEEN :startTime AND :endTime")
    BigDecimal getMinMetricValue(
        @Param("service") String service,
        @Param("metricName") String metricName,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Get sum of metric values over time period
     */
    @Query("SELECT SUM(m.metricValue) FROM SystemMetrics m WHERE m.service = :service " +
           "AND m.metricName = :metricName AND m.timestamp BETWEEN :startTime AND :endTime")
    BigDecimal getSumMetricValue(
        @Param("service") String service,
        @Param("metricName") String metricName,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Get distinct services
     */
    @Query("SELECT DISTINCT m.service FROM SystemMetrics m")
    List<String> findDistinctServices();
    
    /**
     * Get distinct metric names for service
     */
    @Query("SELECT DISTINCT m.metricName FROM SystemMetrics m WHERE m.service = :service")
    List<String> findDistinctMetricNamesByService(@Param("service") String service);
    
    /**
     * Get hourly aggregated metrics
     */
    @Query("SELECT DATE_FORMAT(m.timestamp, '%Y-%m-%d %H:00:00'), AVG(m.metricValue), MAX(m.metricValue), MIN(m.metricValue) " +
           "FROM SystemMetrics m WHERE m.service = :service AND m.metricName = :metricName " +
           "AND m.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY DATE_FORMAT(m.timestamp, '%Y-%m-%d %H:00:00') " +
           "ORDER BY DATE_FORMAT(m.timestamp, '%Y-%m-%d %H:00:00')")
    List<Object[]> getHourlyAggregatedMetrics(
        @Param("service") String service,
        @Param("metricName") String metricName,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Get daily aggregated metrics
     */
    @Query("SELECT DATE(m.timestamp), AVG(m.metricValue), MAX(m.metricValue), MIN(m.metricValue), COUNT(m) " +
           "FROM SystemMetrics m WHERE m.service = :service AND m.metricName = :metricName " +
           "AND m.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY DATE(m.timestamp) " +
           "ORDER BY DATE(m.timestamp)")
    List<Object[]> getDailyAggregatedMetrics(
        @Param("service") String service,
        @Param("metricName") String metricName,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Get service health overview
     */
    @Query("SELECT m.service, m.metricName, m.metricValue, m.timestamp FROM SystemMetrics m " +
           "WHERE m.timestamp >= :sinceTime AND m.metricName IN :healthMetrics " +
           "ORDER BY m.service, m.metricName, m.timestamp DESC")
    List<SystemMetrics> getServiceHealthOverview(
        @Param("sinceTime") LocalDateTime sinceTime,
        @Param("healthMetrics") List<String> healthMetrics
    );
    
    /**
     * Delete old metrics (data retention)
     */
    void deleteByTimestampBefore(LocalDateTime cutoffTime);
    
    /**
     * Count metrics by service
     */
    long countByService(String service);
    
    /**
     * Count total metrics within time range
     */
    long countByTimestampBetween(LocalDateTime startTime, LocalDateTime endTime);
}
