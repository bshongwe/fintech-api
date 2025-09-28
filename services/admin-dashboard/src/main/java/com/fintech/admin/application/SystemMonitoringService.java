package com.fintech.admin.application;

import com.fintech.admin.domain.SystemMetrics;
import com.fintech.admin.domain.MetricType;
import com.fintech.admin.infrastructure.SystemMetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * System Monitoring Service
 * 
 * Collects, processes, and aggregates system metrics from all services.
 * Provides health monitoring, performance tracking, and operational insights.
 */
@Service
@Transactional
public class SystemMonitoringService {
    
    private static final Logger logger = LoggerFactory.getLogger(SystemMonitoringService.class);
    
    private final SystemMetricsRepository systemMetricsRepository;
    private final SystemAlertService systemAlertService;
    private final WebClient.Builder webClientBuilder;
    
    // Service endpoints for health checks
    private final Map<String, String> serviceEndpoints = Map.of(
        "account-service", "http://account-service:8080/actuator/health",
        "auth-service", "http://auth-service:8080/actuator/health",
        "payment-service", "http://payment-service:8080/actuator/health",
        "ledger-service", "http://ledger-service:8080/actuator/health",
        "notification-service", "http://notification-service:8080/actuator/health",
        "fraud-detection", "http://fraud-detection:8080/actuator/health",
        "compliance-service", "http://compliance-service:8080/actuator/health",
        "reporting-service", "http://reporting-service:8080/actuator/health"
    );
    
    @Autowired
    public SystemMonitoringService(SystemMetricsRepository systemMetricsRepository,
                                  SystemAlertService systemAlertService,
                                  WebClient.Builder webClientBuilder) {
        this.systemMetricsRepository = systemMetricsRepository;
        this.systemAlertService = systemAlertService;
        this.webClientBuilder = webClientBuilder;
    }
    
    /**
     * Record system metric
     */
    public SystemMetrics recordMetric(String service, String metricName, BigDecimal value, 
                                    String unit, MetricType type, Map<String, String> tags) {
        SystemMetrics metric = new SystemMetrics(service, metricName, value, unit, type);
        
        if (tags != null) {
            metric.setTags(tags);
        }
        
        SystemMetrics savedMetric = systemMetricsRepository.save(metric);
        
        // Check if metric value triggers an alert
        checkMetricThresholds(savedMetric);
        
        return savedMetric;
    }
    
    /**
     * Get latest metrics for service
     */
    @Cacheable(value = "serviceMetrics", key = "#service")
    public List<ServiceMetricSummary> getServiceMetrics(String service) {
        List<String> metricNames = systemMetricsRepository.findDistinctMetricNamesByService(service);
        
        return metricNames.stream()
            .map(metricName -> {
                Optional<SystemMetrics> latest = systemMetricsRepository.findLatestMetric(service, metricName);
                if (latest.isPresent()) {
                    SystemMetrics metric = latest.get();
                    return new ServiceMetricSummary(
                        service,
                        metricName,
                        metric.getMetricValue(),
                        metric.getMetricUnit(),
                        metric.getTimestamp()
                    );
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Get system health overview
     */
    @Cacheable(value = "systemHealth", key = "'overview'")
    public SystemHealthOverview getSystemHealthOverview() {
        LocalDateTime since = LocalDateTime.now().minusMinutes(5);
        List<String> healthMetrics = Arrays.asList(
            "health.status", "cpu.usage", "memory.usage", "disk.usage", 
            "response.time", "error.rate", "active.connections"
        );
        
        List<SystemMetrics> recentMetrics = systemMetricsRepository.getServiceHealthOverview(since, healthMetrics);
        
        Map<String, ServiceHealth> serviceHealthMap = new HashMap<>();
        
        for (SystemMetrics metric : recentMetrics) {
            ServiceHealth health = serviceHealthMap.computeIfAbsent(
                metric.getService(), 
                s -> new ServiceHealth(s)
            );
            
            health.addMetric(metric.getMetricName(), metric.getMetricValue(), metric.getTimestamp());
        }
        
        return new SystemHealthOverview(
            serviceHealthMap.values().stream().collect(Collectors.toList()),
            LocalDateTime.now()
        );
    }
    
    /**
     * Get metrics trend data
     */
    public List<MetricTrendData> getMetricTrends(String service, String metricName, int hours) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);
        
        List<Object[]> hourlyData = systemMetricsRepository.getHourlyAggregatedMetrics(
            service, metricName, startTime, endTime
        );
        
        return hourlyData.stream()
            .map(data -> new MetricTrendData(
                LocalDateTime.parse((String) data[0]),
                (BigDecimal) data[1], // average
                (BigDecimal) data[2], // max
                (BigDecimal) data[3]  // min
            ))
            .collect(Collectors.toList());
    }
    
    /**
     * Get performance summary
     */
    public PerformanceSummary getPerformanceSummary(String service, int hours) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);
        
        BigDecimal avgResponseTime = systemMetricsRepository.getAverageMetricValue(
            service, "response.time", startTime, endTime
        );
        
        BigDecimal maxResponseTime = systemMetricsRepository.getMaxMetricValue(
            service, "response.time", startTime, endTime
        );
        
        BigDecimal avgCpuUsage = systemMetricsRepository.getAverageMetricValue(
            service, "cpu.usage", startTime, endTime
        );
        
        BigDecimal avgMemoryUsage = systemMetricsRepository.getAverageMetricValue(
            service, "memory.usage", startTime, endTime
        );
        
        BigDecimal errorRate = systemMetricsRepository.getAverageMetricValue(
            service, "error.rate", startTime, endTime
        );
        
        return new PerformanceSummary(
            service,
            avgResponseTime != null ? avgResponseTime : BigDecimal.ZERO,
            maxResponseTime != null ? maxResponseTime : BigDecimal.ZERO,
            avgCpuUsage != null ? avgCpuUsage : BigDecimal.ZERO,
            avgMemoryUsage != null ? avgMemoryUsage : BigDecimal.ZERO,
            errorRate != null ? errorRate : BigDecimal.ZERO
        );
    }
    
    /**
     * Scheduled health checks for all services
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void performHealthChecks() {
        logger.debug("Performing scheduled health checks");
        
        serviceEndpoints.forEach((service, endpoint) -> {
            checkServiceHealth(service, endpoint);
        });
    }
    
    /**
     * Scheduled metric aggregation and cleanup
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void performMetricMaintenance() {
        logger.info("Performing metric maintenance");
        
        // Delete metrics older than 90 days
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(90);
        systemMetricsRepository.deleteByTimestampBefore(cutoffTime);
        
        logger.info("Completed metric maintenance");
    }
    
    /**
     * Listen for metric events from services
     */
    @KafkaListener(topics = "service-metrics")
    public void handleMetricEvent(MetricEventMessage event) {
        try {
            Map<String, String> tags = new HashMap<>();
            tags.put("source", "kafka");
            
            recordMetric(
                event.getService(),
                event.getMetricName(),
                event.getValue(),
                event.getUnit(),
                MetricType.valueOf(event.getType().toUpperCase()),
                tags
            );
        } catch (Exception e) {
            logger.error("Error processing metric event: {}", event, e);
        }
    }
    
    private void checkServiceHealth(String service, String endpoint) {
        try {
            WebClient webClient = webClientBuilder.build();
            
            long startTime = System.currentTimeMillis();
            
            webClient.get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(Map.class)
                .subscribe(
                    response -> {
                        long responseTime = System.currentTimeMillis() - startTime;
                        
                        // Record health status
                        String status = (String) response.get("status");
                        recordMetric(service, "health.status", 
                                   "UP".equals(status) ? BigDecimal.ONE : BigDecimal.ZERO,
                                   "status", MetricType.GAUGE, null);
                        
                        // Record response time
                        recordMetric(service, "response.time", 
                                   BigDecimal.valueOf(responseTime),
                                   "ms", MetricType.TIMER, null);
                        
                        // Process additional health details if available
                        @SuppressWarnings("unchecked")
                        Map<String, Object> details = (Map<String, Object>) response.get("details");
                        if (details != null) {
                            processHealthDetails(service, details);
                        }
                    },
                    error -> {
                        // Record service as down
                        recordMetric(service, "health.status", BigDecimal.ZERO,
                                   "status", MetricType.GAUGE, null);
                        
                        logger.warn("Health check failed for service {}: {}", service, error.getMessage());
                        
                        // Create alert for service down
                        createServiceDownAlert(service, error.getMessage());
                    }
                );
                
        } catch (Exception e) {
            logger.error("Error performing health check for service {}", service, e);
        }
    }
    
    private void processHealthDetails(String service, Map<String, Object> details) {
        // Process database health
        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) details.get("db");
        if (db != null) {
            String dbStatus = (String) db.get("status");
            recordMetric(service, "database.status", 
                       "UP".equals(dbStatus) ? BigDecimal.ONE : BigDecimal.ZERO,
                       "status", MetricType.GAUGE, null);
        }
        
        // Process Redis health
        @SuppressWarnings("unchecked")
        Map<String, Object> redis = (Map<String, Object>) details.get("redis");
        if (redis != null) {
            String redisStatus = (String) redis.get("status");
            recordMetric(service, "redis.status", 
                       "UP".equals(redisStatus) ? BigDecimal.ONE : BigDecimal.ZERO,
                       "status", MetricType.GAUGE, null);
        }
        
        // Process disk space
        @SuppressWarnings("unchecked")
        Map<String, Object> diskSpace = (Map<String, Object>) details.get("diskSpace");
        if (diskSpace != null) {
            Long total = (Long) diskSpace.get("total");
            Long free = (Long) diskSpace.get("free");
            if (total != null && free != null) {
                double usagePercent = ((double) (total - free) / total) * 100;
                recordMetric(service, "disk.usage", 
                           BigDecimal.valueOf(usagePercent),
                           "percent", MetricType.GAUGE, null);
            }
        }
    }
    
    private void checkMetricThresholds(SystemMetrics metric) {
        // Define threshold rules
        Map<String, BigDecimal> thresholds = Map.of(
            "cpu.usage", BigDecimal.valueOf(80),
            "memory.usage", BigDecimal.valueOf(85),
            "disk.usage", BigDecimal.valueOf(90),
            "response.time", BigDecimal.valueOf(5000),
            "error.rate", BigDecimal.valueOf(5)
        );
        
        BigDecimal threshold = thresholds.get(metric.getMetricName());
        if (threshold != null && metric.getMetricValue().compareTo(threshold) > 0) {
            // Create alert for threshold breach
            SystemAlertService.CreateAlertRequest alertRequest = new SystemAlertService.CreateAlertRequest();
            alertRequest.setTitle("Metric Threshold Exceeded");
            alertRequest.setDescription(String.format(
                "%s for service %s exceeded threshold: %s (threshold: %s)",
                metric.getMetricName(), metric.getService(), 
                metric.getFormattedValue(), threshold
            ));
            alertRequest.setSeverity(com.fintech.admin.domain.AlertSeverity.HIGH);
            alertRequest.setCategory(com.fintech.admin.domain.AlertCategory.PERFORMANCE);
            alertRequest.setService(metric.getService());
            alertRequest.setThresholdValue(threshold);
            alertRequest.setActualValue(metric.getMetricValue());
            
            systemAlertService.createAlert(alertRequest);
        }
    }
    
    private void createServiceDownAlert(String service, String errorMessage) {
        SystemAlertService.CreateAlertRequest alertRequest = new SystemAlertService.CreateAlertRequest();
        alertRequest.setTitle("Service Health Check Failed");
        alertRequest.setDescription("Service " + service + " is not responding: " + errorMessage);
        alertRequest.setSeverity(com.fintech.admin.domain.AlertSeverity.CRITICAL);
        alertRequest.setCategory(com.fintech.admin.domain.AlertCategory.SYSTEM_HEALTH);
        alertRequest.setService(service);
        
        systemAlertService.createAlert(alertRequest);
    }
    
    // Response DTOs
    public static class ServiceMetricSummary {
        private final String service;
        private final String metricName;
        private final BigDecimal value;
        private final String unit;
        private final LocalDateTime timestamp;
        
        public ServiceMetricSummary(String service, String metricName, BigDecimal value, 
                                   String unit, LocalDateTime timestamp) {
            this.service = service;
            this.metricName = metricName;
            this.value = value;
            this.unit = unit;
            this.timestamp = timestamp;
        }
        
        // Getters
        public String getService() { return service; }
        public String getMetricName() { return metricName; }
        public BigDecimal getValue() { return value; }
        public String getUnit() { return unit; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    public static class SystemHealthOverview {
        private final List<ServiceHealth> services;
        private final LocalDateTime timestamp;
        
        public SystemHealthOverview(List<ServiceHealth> services, LocalDateTime timestamp) {
            this.services = services;
            this.timestamp = timestamp;
        }
        
        public List<ServiceHealth> getServices() { return services; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    public static class ServiceHealth {
        private final String service;
        private final Map<String, MetricValue> metrics = new HashMap<>();
        
        public ServiceHealth(String service) {
            this.service = service;
        }
        
        public void addMetric(String name, BigDecimal value, LocalDateTime timestamp) {
            metrics.put(name, new MetricValue(value, timestamp));
        }
        
        public String getService() { return service; }
        public Map<String, MetricValue> getMetrics() { return metrics; }
        
        public boolean isHealthy() {
            MetricValue healthStatus = metrics.get("health.status");
            return healthStatus != null && healthStatus.getValue().equals(BigDecimal.ONE);
        }
    }
    
    public static class MetricValue {
        private final BigDecimal value;
        private final LocalDateTime timestamp;
        
        public MetricValue(BigDecimal value, LocalDateTime timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
        
        public BigDecimal getValue() { return value; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    public static class MetricTrendData {
        private final LocalDateTime timestamp;
        private final BigDecimal average;
        private final BigDecimal maximum;
        private final BigDecimal minimum;
        
        public MetricTrendData(LocalDateTime timestamp, BigDecimal average, 
                              BigDecimal maximum, BigDecimal minimum) {
            this.timestamp = timestamp;
            this.average = average;
            this.maximum = maximum;
            this.minimum = minimum;
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public BigDecimal getAverage() { return average; }
        public BigDecimal getMaximum() { return maximum; }
        public BigDecimal getMinimum() { return minimum; }
    }
    
    public static class PerformanceSummary {
        private final String service;
        private final BigDecimal averageResponseTime;
        private final BigDecimal maxResponseTime;
        private final BigDecimal averageCpuUsage;
        private final BigDecimal averageMemoryUsage;
        private final BigDecimal errorRate;
        
        public PerformanceSummary(String service, BigDecimal averageResponseTime, 
                                BigDecimal maxResponseTime, BigDecimal averageCpuUsage,
                                BigDecimal averageMemoryUsage, BigDecimal errorRate) {
            this.service = service;
            this.averageResponseTime = averageResponseTime;
            this.maxResponseTime = maxResponseTime;
            this.averageCpuUsage = averageCpuUsage;
            this.averageMemoryUsage = averageMemoryUsage;
            this.errorRate = errorRate;
        }
        
        // Getters
        public String getService() { return service; }
        public BigDecimal getAverageResponseTime() { return averageResponseTime; }
        public BigDecimal getMaxResponseTime() { return maxResponseTime; }
        public BigDecimal getAverageCpuUsage() { return averageCpuUsage; }
        public BigDecimal getAverageMemoryUsage() { return averageMemoryUsage; }
        public BigDecimal getErrorRate() { return errorRate; }
    }
    
    // Event Message DTO
    public static class MetricEventMessage {
        private String service;
        private String metricName;
        private BigDecimal value;
        private String unit;
        private String type;
        
        // Getters and setters
        public String getService() { return service; }
        public void setService(String service) { this.service = service; }
        
        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        
        public BigDecimal getValue() { return value; }
        public void setValue(BigDecimal value) { this.value = value; }
        
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}
