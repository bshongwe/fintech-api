package com.fintech.admin.api;

import com.fintech.admin.application.SystemMonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * System Monitoring API Controller
 * 
 * Provides REST endpoints for system health monitoring,
 * performance metrics, and operational insights.
 */
@RestController
@RequestMapping("/api/admin/monitoring")
@Tag(name = "System Monitoring", description = "System health and performance monitoring")
public class SystemMonitoringController {
    
    private final SystemMonitoringService systemMonitoringService;
    
    @Autowired
    public SystemMonitoringController(SystemMonitoringService systemMonitoringService) {
        this.systemMonitoringService = systemMonitoringService;
    }
    
    /**
     * Get system health overview
     */
    @GetMapping("/health")
    @PreAuthorize("hasPermission('monitoring', 'read')")
    @Operation(summary = "Get system health", description = "Get comprehensive system health overview")
    public ResponseEntity<SystemMonitoringService.SystemHealthOverview> getSystemHealth() {
        SystemMonitoringService.SystemHealthOverview health = systemMonitoringService.getSystemHealthOverview();
        return ResponseEntity.ok(health);
    }
    
    /**
     * Get service metrics
     */
    @GetMapping("/services/{service}/metrics")
    @PreAuthorize("hasPermission('monitoring', 'read')")
    @Operation(summary = "Get service metrics", description = "Get latest metrics for specific service")
    public ResponseEntity<List<SystemMonitoringService.ServiceMetricSummary>> getServiceMetrics(
            @PathVariable String service) {
        
        List<SystemMonitoringService.ServiceMetricSummary> metrics = 
            systemMonitoringService.getServiceMetrics(service);
        return ResponseEntity.ok(metrics);
    }
    
    /**
     * Get metric trends
     */
    @GetMapping("/services/{service}/metrics/{metricName}/trends")
    @PreAuthorize("hasPermission('monitoring', 'read')")
    @Operation(summary = "Get metric trends", description = "Get historical trend data for specific metric")
    public ResponseEntity<List<SystemMonitoringService.MetricTrendData>> getMetricTrends(
            @PathVariable String service,
            @PathVariable String metricName,
            @RequestParam(defaultValue = "24") int hours) {
        
        List<SystemMonitoringService.MetricTrendData> trends = 
            systemMonitoringService.getMetricTrends(service, metricName, hours);
        return ResponseEntity.ok(trends);
    }
    
    /**
     * Get performance summary
     */
    @GetMapping("/services/{service}/performance")
    @PreAuthorize("hasPermission('monitoring', 'read')")
    @Operation(summary = "Get performance summary", description = "Get performance summary for service")
    public ResponseEntity<SystemMonitoringService.PerformanceSummary> getPerformanceSummary(
            @PathVariable String service,
            @RequestParam(defaultValue = "24") int hours) {
        
        SystemMonitoringService.PerformanceSummary performance = 
            systemMonitoringService.getPerformanceSummary(service, hours);
        return ResponseEntity.ok(performance);
    }
    
    /**
     * Get monitoring dashboard
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasPermission('monitoring', 'read')")
    @Operation(summary = "Get monitoring dashboard", description = "Get comprehensive monitoring dashboard data")
    public ResponseEntity<MonitoringDashboard> getMonitoringDashboard() {
        SystemMonitoringService.SystemHealthOverview health = systemMonitoringService.getSystemHealthOverview();
        
        // Get performance summaries for key services
        List<String> keyServices = List.of("account-service", "payment-service", "ledger-service", 
                                          "fraud-detection", "compliance-service");
        
        List<SystemMonitoringService.PerformanceSummary> performanceSummaries = keyServices.stream()
            .map(service -> systemMonitoringService.getPerformanceSummary(service, 24))
            .toList();
        
        MonitoringDashboard dashboard = new MonitoringDashboard(health, performanceSummaries);
        return ResponseEntity.ok(dashboard);
    }
    
    // Response DTO
    public static class MonitoringDashboard {
        private final SystemMonitoringService.SystemHealthOverview systemHealth;
        private final List<SystemMonitoringService.PerformanceSummary> performanceSummaries;
        
        public MonitoringDashboard(SystemMonitoringService.SystemHealthOverview systemHealth,
                                  List<SystemMonitoringService.PerformanceSummary> performanceSummaries) {
            this.systemHealth = systemHealth;
            this.performanceSummaries = performanceSummaries;
        }
        
        public SystemMonitoringService.SystemHealthOverview getSystemHealth() { return systemHealth; }
        public List<SystemMonitoringService.PerformanceSummary> getPerformanceSummaries() { return performanceSummaries; }
    }
}
