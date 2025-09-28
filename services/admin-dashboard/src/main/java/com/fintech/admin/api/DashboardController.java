package com.fintech.admin.api;

import com.fintech.admin.application.DashboardIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Dashboard API Controller
 * 
 * Provides comprehensive dashboard endpoints that integrate data from all services.
 * Supports executive dashboards, compliance views, fraud investigation, and reporting.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@Tag(name = "Dashboard", description = "Comprehensive dashboard and integration endpoints")
public class DashboardController {
    
    private final DashboardIntegrationService dashboardIntegrationService;
    
    @Autowired
    public DashboardController(DashboardIntegrationService dashboardIntegrationService) {
        this.dashboardIntegrationService = dashboardIntegrationService;
    }
    
    /**
     * Get executive dashboard
     */
    @GetMapping("/executive")
    @PreAuthorize("hasPermission('dashboard', 'read')")
    @Operation(summary = "Get executive dashboard", description = "Get high-level executive dashboard with key metrics")
    public Mono<ResponseEntity<DashboardIntegrationService.ExecutiveDashboard>> getExecutiveDashboard() {
        return dashboardIntegrationService.getExecutiveDashboard()
            .map(ResponseEntity::ok);
    }
    
    /**
     * Get compliance dashboard
     */
    @GetMapping("/compliance")
    @PreAuthorize("hasPermission('compliance', 'read')")
    @Operation(summary = "Get compliance dashboard", description = "Get compliance monitoring dashboard")
    public Mono<ResponseEntity<DashboardIntegrationService.ComplianceDashboard>> getComplianceDashboard() {
        return dashboardIntegrationService.getComplianceDashboard()
            .map(ResponseEntity::ok);
    }
    
    /**
     * Get fraud investigation dashboard
     */
    @GetMapping("/fraud")
    @PreAuthorize("hasPermission('fraud', 'read')")
    @Operation(summary = "Get fraud dashboard", description = "Get fraud investigation and monitoring dashboard")
    public Mono<ResponseEntity<DashboardIntegrationService.FraudDashboard>> getFraudDashboard() {
        return dashboardIntegrationService.getFraudDashboard()
            .map(ResponseEntity::ok);
    }
    
    /**
     * Get available reports
     */
    @GetMapping("/reports/available")
    @PreAuthorize("hasPermission('reports', 'read')")
    @Operation(summary = "Get available reports", description = "Get list of available reports from reporting service")
    public Mono<ResponseEntity<List<DashboardIntegrationService.ReportDefinitionSummary>>> getAvailableReports() {
        return dashboardIntegrationService.getAvailableReports()
            .map(ResponseEntity::ok);
    }
    
    /**
     * Request report generation
     */
    @PostMapping("/reports/generate")
    @PreAuthorize("hasPermission('reports', 'generate')")
    @Operation(summary = "Generate report", description = "Request generation of a specific report")
    public Mono<ResponseEntity<DashboardIntegrationService.ReportJobStatus>> generateReport(
            @RequestBody GenerateReportRequest request) {
        
        return dashboardIntegrationService.requestReportGeneration(request.getReportId(), request.getParameters())
            .map(ResponseEntity::ok);
    }
    
    // Request DTO
    public static class GenerateReportRequest {
        private String reportId;
        private Map<String, Object> parameters;
        
        // Getters and setters
        public String getReportId() { return reportId; }
        public void setReportId(String reportId) { this.reportId = reportId; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    }
}
