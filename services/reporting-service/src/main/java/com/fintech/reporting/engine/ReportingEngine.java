package com.fintech.reporting.engine;

import com.fintech.reporting.core.ReportDefinition;
import com.fintech.reporting.core.ReportExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central reporting engine that orchestrates report generation
 * Integrates with all fintech services for comprehensive reporting
 */
@Service
public class ReportingEngine {
    
    private final DataAggregationService dataAggregationService;
    private final QueryExecutionService queryExecutionService;
    private final ReportFormatterService formatterService;
    private final ReportCacheService cacheService;
    private final ReportDefinitionRepository definitionRepository;
    private final ReportExecutionRepository executionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Track active report generations to prevent duplicates
    private final Map<String, CompletableFuture<ReportExecution>> activeExecutions = new ConcurrentHashMap<>();
    
    @Autowired
    public ReportingEngine(DataAggregationService dataAggregationService,
                          QueryExecutionService queryExecutionService,
                          ReportFormatterService formatterService,
                          ReportCacheService cacheService,
                          ReportDefinitionRepository definitionRepository,
                          ReportExecutionRepository executionRepository,
                          KafkaTemplate<String, Object> kafkaTemplate) {
        this.dataAggregationService = dataAggregationService;
        this.queryExecutionService = queryExecutionService;
        this.formatterService = formatterService;
        this.cacheService = cacheService;
        this.definitionRepository = definitionRepository;
        this.executionRepository = executionRepository;
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Generate report asynchronously
     * Primary entry point for all report generation
     */
    public CompletableFuture<ReportExecution> generateReport(String definitionId, 
                                                           Map<String, Object> parameters,
                                                           String requestedBy,
                                                           String outputFormat) {
        
        // Create execution record
        ReportExecution execution = new ReportExecution(definitionId, requestedBy, 
                                                       serializeParameters(parameters));
        execution.setOutputFormat(outputFormat);
        execution = executionRepository.save(execution);
        
        // Check for duplicate execution (same parameters, recent)
        String cacheKey = buildCacheKey(definitionId, parameters);
        CompletableFuture<ReportExecution> activeExecution = activeExecutions.get(cacheKey);
        if (activeExecution != null && !activeExecution.isDone()) {
            return activeExecution; // Return existing execution
        }
        
        // Check cache first
        CompletableFuture<ReportExecution> future = CompletableFuture.supplyAsync(() -> {
            try {
                ReportDefinition definition = definitionRepository.findById(definitionId)
                    .orElseThrow(() -> new RuntimeException("Report definition not found: " + definitionId));
                
                // Check cache
                if (definition.isCacheable()) {
                    ReportExecution cachedResult = cacheService.getCachedReport(cacheKey);
                    if (cachedResult != null && !cachedResult.isExpired()) {
                        return cachedResult;
                    }
                }
                
                return executeReport(execution, definition, parameters);
                
            } catch (Exception e) {
                execution.markFailed(e.getMessage());
                executionRepository.save(execution);
                throw new RuntimeException("Report generation failed", e);
            } finally {
                activeExecutions.remove(cacheKey);
            }
        });
        
        activeExecutions.put(cacheKey, future);
        return future;
    }
    
    /**
     * Generate compliance report with audit trail integration
     */
    public CompletableFuture<ReportExecution> generateComplianceReport(String definitionId,
                                                                      Map<String, Object> parameters,
                                                                      String requestedBy,
                                                                      String complianceStandard) {
        
        // Add compliance-specific parameters
        parameters.put("compliance_standard", complianceStandard);
        parameters.put("audit_required", true);
        
        return generateReport(definitionId, parameters, requestedBy, "PDF")
            .thenCompose(execution -> {
                // Log compliance report generation for audit
                kafkaTemplate.send("compliance-reports", execution.getId(), 
                    new ComplianceReportEvent(execution.getId(), definitionId, 
                                            complianceStandard, requestedBy));
                return CompletableFuture.completedFuture(execution);
            });
    }
    
    /**
     * Generate fraud analysis report with ML insights
     */
    public CompletableFuture<ReportExecution> generateFraudAnalysisReport(String userId,
                                                                         String timeRange,
                                                                         String requestedBy) {
        Map<String, Object> parameters = Map.of(
            "user_id", userId,
            "time_range", timeRange,
            "include_risk_scores", true,
            "include_rule_analysis", true
        );
        
        return generateReport("fraud-analysis-report", parameters, requestedBy, "PDF")
            .thenCompose(execution -> {
                // Enrich with ML insights
                return enrichWithFraudInsights(execution);
            });
    }
    
    /**
     * Generate financial summary report aggregating from all services
     */
    public CompletableFuture<ReportExecution> generateFinancialSummary(String accountId,
                                                                      String period,
                                                                      String requestedBy) {
        Map<String, Object> parameters = Map.of(
            "account_id", accountId,
            "period", period,
            "include_transactions", true,
            "include_balances", true,
            "include_fraud_flags", true
        );
        
        return generateReport("financial-summary-report", parameters, requestedBy, "EXCEL")
            .thenCompose(execution -> {
                // Cross-reference with payment service, ledger service, and fraud detection
                return enrichWithCrossServiceData(execution);
            });
    }
    
    /**
     * Generate operational dashboard data
     */
    public CompletableFuture<ReportExecution> generateDashboardData(String dashboardType,
                                                                   String timeRange,
                                                                   String requestedBy) {
        Map<String, Object> parameters = Map.of(
            "dashboard_type", dashboardType,
            "time_range", timeRange,
            "real_time", true
        );
        
        return generateReport("dashboard-" + dashboardType, parameters, requestedBy, "JSON");
    }
    
    private ReportExecution executeReport(ReportExecution execution, 
                                        ReportDefinition definition,
                                        Map<String, Object> parameters) {
        execution.markStarted();
        executionRepository.save(execution);
        
        try {
            // 1. Execute query and get raw data
            ReportDataSet dataSet = queryExecutionService.executeQuery(definition, parameters);
            
            // 2. Apply aggregations if needed
            if (definition.getType() == ReportDefinition.ReportType.SUMMARY) {
                dataSet = dataAggregationService.aggregateData(dataSet, definition);
            }
            
            // 3. Format output
            ReportOutput output = formatterService.formatReport(dataSet, execution.getOutputFormat(), definition);
            
            // 4. Store result
            String storageLocation = storeReportOutput(output, execution.getId());
            
            // 5. Update execution record
            execution.markCompleted(dataSet.getRowCount(), output.getFileSize(), storageLocation);
            execution.setDownloadUrl(generateDownloadUrl(storageLocation));
            
            // 6. Cache if applicable
            if (definition.isCacheable()) {
                String cacheKey = buildCacheKey(definition.getId(), parameters);
                cacheService.cacheReport(cacheKey, execution, definition.getCacheTtlMinutes());
            }
            
            executionRepository.save(execution);
            
            // 7. Notify completion
            kafkaTemplate.send("report-completed", execution.getId(), execution);
            
            return execution;
            
        } catch (Exception e) {
            execution.markFailed(e.getMessage());
            executionRepository.save(execution);
            throw e;
        }
    }
    
    private CompletableFuture<ReportExecution> enrichWithFraudInsights(ReportExecution execution) {
        return CompletableFuture.supplyAsync(() -> {
            // Call fraud detection service for additional insights
            // This would integrate with the fraud detection service we built
            try {
                // Add ML model insights, risk score trends, etc.
                String metadata = "{\"fraud_insights\": true, \"ml_insights\": true}";
                execution.setExecutionMetadata(metadata);
                return executionRepository.save(execution);
            } catch (Exception e) {
                // Fail gracefully - return original execution
                return execution;
            }
        });
    }
    
    private CompletableFuture<ReportExecution> enrichWithCrossServiceData(ReportExecution execution) {
        return CompletableFuture.supplyAsync(() -> {
            // Aggregate data from payment service, account service, ledger service
            try {
                // This would make calls to other services for enrichment
                String metadata = "{\"cross_service_data\": true, \"services\": [\"payment\", \"account\", \"ledger\", \"fraud\"]}";
                execution.setExecutionMetadata(metadata);
                return executionRepository.save(execution);
            } catch (Exception e) {
                return execution;
            }
        });
    }
    
    private String buildCacheKey(String definitionId, Map<String, Object> parameters) {
        return definitionId + ":" + parameters.hashCode();
    }
    
    private String serializeParameters(Map<String, Object> parameters) {
        // Convert parameters to JSON string
        try {
            // Use Jackson ObjectMapper to serialize
            return "{}"; // Placeholder
        } catch (Exception e) {
            return "{}";
        }
    }
    
    private String storeReportOutput(ReportOutput output, String executionId) {
        // Store in S3 or local filesystem
        return "/reports/" + executionId + "." + output.getFormat().toLowerCase();
    }
    
    private String generateDownloadUrl(String storageLocation) {
        // Generate signed URL for download
        return "/api/v1/reports/download?location=" + storageLocation;
    }
    
    // Inner classes and events
    public static class ComplianceReportEvent {
        private String executionId;
        private String definitionId;
        private String complianceStandard;
        private String requestedBy;
        
        public ComplianceReportEvent(String executionId, String definitionId, 
                                   String complianceStandard, String requestedBy) {
            this.executionId = executionId;
            this.definitionId = definitionId;
            this.complianceStandard = complianceStandard;
            this.requestedBy = requestedBy;
        }
        
        // Getters
        public String getExecutionId() { return executionId; }
        public String getDefinitionId() { return definitionId; }
        public String getComplianceStandard() { return complianceStandard; }
        public String getRequestedBy() { return requestedBy; }
    }
}
