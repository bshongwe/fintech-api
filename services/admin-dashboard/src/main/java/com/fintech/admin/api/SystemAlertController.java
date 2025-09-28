package com.fintech.admin.api;

import com.fintech.admin.application.SystemAlertService;
import com.fintech.admin.domain.SystemAlert;
import com.fintech.admin.domain.AlertSeverity;
import com.fintech.admin.domain.AlertStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * System Alert Management API Controller
 * 
 * Provides REST endpoints for system alert monitoring,
 * acknowledgment, resolution, and statistics.
 */
@RestController
@RequestMapping("/api/admin/alerts")
@Tag(name = "System Alerts", description = "System alert management and monitoring")
public class SystemAlertController {
    
    private final SystemAlertService systemAlertService;
    
    @Autowired
    public SystemAlertController(SystemAlertService systemAlertService) {
        this.systemAlertService = systemAlertService;
    }
    
    /**
     * Get all active alerts
     */
    @GetMapping("/active")
    @PreAuthorize("hasPermission('alerts', 'read')")
    @Operation(summary = "Get active alerts", description = "Retrieve all active (open/acknowledged) alerts")
    public ResponseEntity<Page<SystemAlert>> getActiveAlerts(Pageable pageable) {
        Page<SystemAlert> alerts = systemAlertService.getActiveAlerts(pageable);
        return ResponseEntity.ok(alerts);
    }
    
    /**
     * Get alerts by status
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasPermission('alerts', 'read')")
    @Operation(summary = "Get alerts by status", description = "Retrieve alerts filtered by status")
    public ResponseEntity<Page<SystemAlert>> getAlertsByStatus(
            @PathVariable AlertStatus status,
            Pageable pageable) {
        
        Page<SystemAlert> alerts = systemAlertService.getAlertsByStatus(status, pageable);
        return ResponseEntity.ok(alerts);
    }
    
    /**
     * Get alerts by severity
     */
    @GetMapping("/severity/{severity}")
    @PreAuthorize("hasPermission('alerts', 'read')")
    @Operation(summary = "Get alerts by severity", description = "Retrieve alerts filtered by severity level")
    public ResponseEntity<List<SystemAlert>> getAlertsBySeverity(@PathVariable AlertSeverity severity) {
        List<SystemAlert> alerts = systemAlertService.getAlertsBySeverity(severity);
        return ResponseEntity.ok(alerts);
    }
    
    /**
     * Get critical alerts
     */
    @GetMapping("/critical")
    @PreAuthorize("hasPermission('alerts', 'read')")
    @Operation(summary = "Get critical alerts", description = "Retrieve all critical and high severity alerts")
    public ResponseEntity<List<SystemAlert>> getCriticalAlerts() {
        List<SystemAlert> alerts = systemAlertService.getCriticalAlerts();
        return ResponseEntity.ok(alerts);
    }
    
    /**
     * Get alerts by service
     */
    @GetMapping("/service/{service}")
    @PreAuthorize("hasPermission('alerts', 'read')")
    @Operation(summary = "Get alerts by service", description = "Retrieve alerts for specific service")
    public ResponseEntity<List<SystemAlert>> getAlertsByService(@PathVariable String service) {
        List<SystemAlert> alerts = systemAlertService.getAlertsByService(service);
        return ResponseEntity.ok(alerts);
    }
    
    /**
     * Get alert by ID
     */
    @GetMapping("/{alertId}")
    @PreAuthorize("hasPermission('alerts', 'read')")
    @Operation(summary = "Get alert details", description = "Retrieve detailed alert information by ID")
    public ResponseEntity<SystemAlert> getAlert(@PathVariable UUID alertId) {
        Optional<SystemAlert> alert = systemAlertService.getAlert(alertId);
        
        return alert.map(ResponseEntity::ok)
                   .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Create system alert
     */
    @PostMapping
    @PreAuthorize("hasPermission('alerts', 'write')")
    @Operation(summary = "Create alert", description = "Create a new system alert")
    public ResponseEntity<SystemAlert> createAlert(
            @RequestBody SystemAlertService.CreateAlertRequest request) {
        
        SystemAlert alert = systemAlertService.createAlert(request);
        return ResponseEntity.ok(alert);
    }
    
    /**
     * Acknowledge alert
     */
    @PostMapping("/{alertId}/acknowledge")
    @PreAuthorize("hasPermission('alerts', 'write')")
    @Operation(summary = "Acknowledge alert", description = "Acknowledge an alert to indicate it's being handled")
    public ResponseEntity<SystemAlert> acknowledgeAlert(
            @PathVariable UUID alertId,
            Authentication authentication) {
        
        UUID adminUserId = getCurrentUserId(authentication);
        Optional<SystemAlert> alert = systemAlertService.acknowledgeAlert(alertId, adminUserId);
        
        return alert.map(ResponseEntity::ok)
                   .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Resolve alert
     */
    @PostMapping("/{alertId}/resolve")
    @PreAuthorize("hasPermission('alerts', 'write')")
    @Operation(summary = "Resolve alert", description = "Resolve an alert with resolution notes")
    public ResponseEntity<SystemAlert> resolveAlert(
            @PathVariable UUID alertId,
            @RequestBody ResolveAlertRequest request,
            Authentication authentication) {
        
        UUID adminUserId = getCurrentUserId(authentication);
        Optional<SystemAlert> alert = systemAlertService.resolveAlert(
            alertId, adminUserId, request.getResolutionNotes());
        
        return alert.map(ResponseEntity::ok)
                   .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get alert statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasPermission('alerts', 'read')")
    @Operation(summary = "Get alert statistics", description = "Get comprehensive alert statistics")
    public ResponseEntity<SystemAlertService.AlertStatistics> getAlertStatistics() {
        SystemAlertService.AlertStatistics stats = systemAlertService.getAlertStatistics();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get alert statistics by service
     */
    @GetMapping("/statistics/by-service")
    @PreAuthorize("hasPermission('alerts', 'read')")
    @Operation(summary = "Get service alert stats", description = "Get alert statistics grouped by service")
    public ResponseEntity<List<SystemAlertService.ServiceAlertStats>> getAlertStatsByService(
            @RequestParam(defaultValue = "7") int days) {
        
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<SystemAlertService.ServiceAlertStats> stats = systemAlertService.getAlertStatsByService(since);
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get daily alert trends
     */
    @GetMapping("/trends/daily")
    @PreAuthorize("hasPermission('alerts', 'read')")
    @Operation(summary = "Get daily alert trends", description = "Get daily alert count trends")
    public ResponseEntity<List<SystemAlertService.DailyAlertCount>> getDailyAlertTrends(
            @RequestParam(defaultValue = "30") int days) {
        
        List<SystemAlertService.DailyAlertCount> trends = systemAlertService.getDailyAlertTrends(days);
        return ResponseEntity.ok(trends);
    }
    
    /**
     * Get resolution times by category
     */
    @GetMapping("/resolution-times")
    @PreAuthorize("hasPermission('alerts', 'read')")
    @Operation(summary = "Get resolution times", description = "Get average resolution times by category")
    public ResponseEntity<List<SystemAlertService.CategoryResolutionTime>> getResolutionTimes(
            @RequestParam(defaultValue = "30") int days) {
        
        List<SystemAlertService.CategoryResolutionTime> resolutionTimes = 
            systemAlertService.getResolutionTimesByCategory(days);
        return ResponseEntity.ok(resolutionTimes);
    }
    
    /**
     * Get alert dashboard summary
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasPermission('alerts', 'read')")
    @Operation(summary = "Get alert dashboard", description = "Get alert dashboard summary with key metrics")
    public ResponseEntity<AlertDashboard> getAlertDashboard() {
        SystemAlertService.AlertStatistics stats = systemAlertService.getAlertStatistics();
        List<SystemAlert> criticalAlerts = systemAlertService.getCriticalAlerts();
        
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<SystemAlertService.ServiceAlertStats> serviceStats = 
            systemAlertService.getAlertStatsByService(since);
        
        AlertDashboard dashboard = new AlertDashboard(stats, criticalAlerts, serviceStats);
        return ResponseEntity.ok(dashboard);
    }
    
    private UUID getCurrentUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof String) {
            return UUID.fromString((String) authentication.getPrincipal());
        }
        throw new RuntimeException("Unable to determine current user ID");
    }
    
    // Request/Response DTOs
    public static class ResolveAlertRequest {
        private String resolutionNotes;
        
        public String getResolutionNotes() { return resolutionNotes; }
        public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    }
    
    public static class AlertDashboard {
        private final SystemAlertService.AlertStatistics statistics;
        private final List<SystemAlert> criticalAlerts;
        private final List<SystemAlertService.ServiceAlertStats> serviceStats;
        
        public AlertDashboard(SystemAlertService.AlertStatistics statistics,
                             List<SystemAlert> criticalAlerts,
                             List<SystemAlertService.ServiceAlertStats> serviceStats) {
            this.statistics = statistics;
            this.criticalAlerts = criticalAlerts;
            this.serviceStats = serviceStats;
        }
        
        public SystemAlertService.AlertStatistics getStatistics() { return statistics; }
        public List<SystemAlert> getCriticalAlerts() { return criticalAlerts; }
        public List<SystemAlertService.ServiceAlertStats> getServiceStats() { return serviceStats; }
    }
}
