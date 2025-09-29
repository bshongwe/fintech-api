package com.fintech.admin.application;

import com.fintech.commons.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin Dashboard Integration Service
 * 
 * Integrates with other services to provide comprehensive dashboard functionality.
 * Consumes reports, compliance data, fraud analytics, and system information.
 */
@Service
public class DashboardIntegrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(DashboardIntegrationService.class);
    
    private final WebClient.Builder webClientBuilder;
    
    // Service base URLs
    private static final String REPORTING_SERVICE_URL = "http://reporting-service:8080";
    private static final String COMPLIANCE_SERVICE_URL = "http://compliance-service:8080";
    private static final String FRAUD_SERVICE_URL = "http://fraud-detection:8080";
    private static final String ACCOUNT_SERVICE_URL = "http://account-service:8080";
    private static final String PAYMENT_SERVICE_URL = "http://payment-service:8080";
    
    @Autowired
    public DashboardIntegrationService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }
    
    /**
     * Get executive dashboard summary
     */
    @Cacheable(value = "executiveSummary", key = "'dashboard'")
    public Mono<ExecutiveDashboard> getExecutiveDashboard() {
        logger.debug("Fetching executive dashboard data");
        
        WebClient webClient = webClientBuilder.build();
        
        // Fetch data from multiple services in parallel
        Mono<FinancialSummary> financialData = getFinancialSummary(webClient);
        Mono<ComplianceSummary> complianceData = getComplianceSummary(webClient);
        Mono<FraudSummary> fraudData = getFraudSummary(webClient);
        Mono<UserMetrics> userMetrics = getUserMetrics(webClient);
        
        return Mono.zip(financialData, complianceData, fraudData, userMetrics)
            .map(tuple -> new ExecutiveDashboard(
                tuple.getT1(), // financial
                tuple.getT2(), // compliance  
                tuple.getT3(), // fraud
                tuple.getT4(), // users
                LocalDateTime.now()
            ))
            .doOnError(error -> logger.error("Error fetching executive dashboard", error))
            .onErrorReturn(new ExecutiveDashboard(null, null, null, null, LocalDateTime.now()));
    }
    
    /**
     * Get available reports from reporting service
     */
    @Cacheable(value = "availableReports", key = "'reports'")
    public Mono<List<ReportDefinitionSummary>> getAvailableReports() {
        WebClient webClient = webClientBuilder.build();
        
        return webClient.get()
            .uri(REPORTING_SERVICE_URL + "/api/reports/definitions")
            .retrieve()
            .onStatus(status -> status.isError(), response -> {
                logger.error("Error fetching reports: {}", response.statusCode());
                return Mono.empty();
            })
            .bodyToFlux(ReportDefinitionSummary.class)
            .collectList()
            .doOnError(error -> logger.error("Error fetching available reports", error))
            .onErrorReturn(List.of());
    }
    
    /**
     * Request report generation
     */
    public Mono<ReportJobStatus> requestReportGeneration(String reportId, Map<String, Object> parameters) {
        WebClient webClient = webClientBuilder.build();
        
        ReportGenerationRequest request = new ReportGenerationRequest(reportId, parameters);
        
        return webClient.post()
            .uri(REPORTING_SERVICE_URL + "/api/reports/generate")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ReportJobStatus.class)
            .doOnError(error -> logger.error("Error requesting report generation", error));
    }
    
    /**
     * Get compliance dashboard data
     */
    @Cacheable(value = "complianceDashboard", key = "'compliance'")
    public Mono<ComplianceDashboard> getComplianceDashboard() {
        WebClient webClient = webClientBuilder.build();
        
        Mono<List<PolicyViolation>> violations = getPolicyViolations(webClient);
        Mono<List<AuditTrailSummary>> recentAudits = getRecentAuditTrails(webClient);
        Mono<ComplianceMetrics> metrics = getComplianceMetrics(webClient);
        
        return Mono.zip(violations, recentAudits, metrics)
            .map(tuple -> new ComplianceDashboard(
                tuple.getT1(), // violations
                tuple.getT2(), // audits
                tuple.getT3(), // metrics
                LocalDateTime.now()
            ));
    }
    
    /**
     * Get fraud investigation dashboard
     */
    @Cacheable(value = "fraudDashboard", key = "'fraud'")
    public Mono<FraudDashboard> getFraudDashboard() {
        WebClient webClient = webClientBuilder.build();
        
        Mono<List<FraudCase>> activeCases = getActiveFraudCases(webClient);
        Mono<FraudStatistics> stats = getFraudStatistics(webClient);
        Mono<List<HighRiskTransaction>> riskTransactions = getHighRiskTransactions(webClient);
        
        return Mono.zip(activeCases, stats, riskTransactions)
            .map(tuple -> new FraudDashboard(
                tuple.getT1(), // cases
                tuple.getT2(), // stats
                tuple.getT3(), // transactions
                LocalDateTime.now()
            ));
    }
    
    private Mono<FinancialSummary> getFinancialSummary(WebClient webClient) {
        return webClient.get()
            .uri(REPORTING_SERVICE_URL + "/api/reports/financial-summary")
            .retrieve()
            .bodyToMono(FinancialSummary.class)
            .onErrorReturn(new FinancialSummary(BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L));
    }
    
    private Mono<ComplianceSummary> getComplianceSummary(WebClient webClient) {
        return webClient.get()
            .uri(COMPLIANCE_SERVICE_URL + "/api/compliance/summary")
            .retrieve()
            .bodyToMono(ComplianceSummary.class)
            .onErrorReturn(new ComplianceSummary(0L, 0L, 0L, 0L));
    }
    
    private Mono<FraudSummary> getFraudSummary(WebClient webClient) {
        return webClient.get()
            .uri(FRAUD_SERVICE_URL + "/api/fraud/summary")
            .retrieve()
            .bodyToMono(FraudSummary.class)
            .onErrorReturn(new FraudSummary(0L, 0L, BigDecimal.ZERO, 0L));
    }
    
    private Mono<UserMetrics> getUserMetrics(WebClient webClient) {
        return webClient.get()
            .uri(ACCOUNT_SERVICE_URL + "/api/accounts/metrics")
            .retrieve()
            .bodyToMono(UserMetrics.class)
            .onErrorReturn(new UserMetrics(0L, 0L, 0L, 0L));
    }
    
    private Mono<List<PolicyViolation>> getPolicyViolations(WebClient webClient) {
        return webClient.get()
            .uri(COMPLIANCE_SERVICE_URL + "/api/compliance/violations/recent")
            .retrieve()
            .bodyToFlux(PolicyViolation.class)
            .collectList()
            .onErrorReturn(List.of());
    }
    
    private Mono<List<AuditTrailSummary>> getRecentAuditTrails(WebClient webClient) {
        return webClient.get()
            .uri(COMPLIANCE_SERVICE_URL + "/api/compliance/audit-trails/recent")
            .retrieve()
            .bodyToFlux(AuditTrailSummary.class)
            .collectList()
            .onErrorReturn(List.of());
    }
    
    private Mono<ComplianceMetrics> getComplianceMetrics(WebClient webClient) {
        return webClient.get()
            .uri(COMPLIANCE_SERVICE_URL + "/api/compliance/metrics")
            .retrieve()
            .bodyToMono(ComplianceMetrics.class)
            .onErrorReturn(new ComplianceMetrics(0L, 0L, BigDecimal.ZERO));
    }
    
    private Mono<List<FraudCase>> getActiveFraudCases(WebClient webClient) {
        return webClient.get()
            .uri(FRAUD_SERVICE_URL + "/api/fraud/cases/active")
            .retrieve()
            .bodyToFlux(FraudCase.class)
            .collectList()
            .onErrorReturn(List.of());
    }
    
    private Mono<FraudStatistics> getFraudStatistics(WebClient webClient) {
        return webClient.get()
            .uri(FRAUD_SERVICE_URL + "/api/fraud/statistics")
            .retrieve()
            .bodyToMono(FraudStatistics.class)
            .onErrorReturn(new FraudStatistics(0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO));
    }
    
    private Mono<List<HighRiskTransaction>> getHighRiskTransactions(WebClient webClient) {
        return webClient.get()
            .uri(FRAUD_SERVICE_URL + "/api/fraud/transactions/high-risk")
            .retrieve()
            .bodyToFlux(HighRiskTransaction.class)
            .collectList()
            .onErrorReturn(List.of());
    }
    
    // Dashboard DTOs
    public static class ExecutiveDashboard {
        private final FinancialSummary financial;
        private final ComplianceSummary compliance;
        private final FraudSummary fraud;
        private final UserMetrics users;
        private final LocalDateTime timestamp;
        
        public ExecutiveDashboard(FinancialSummary financial, ComplianceSummary compliance,
                                FraudSummary fraud, UserMetrics users, LocalDateTime timestamp) {
            this.financial = financial;
            this.compliance = compliance;
            this.fraud = fraud;
            this.users = users;
            this.timestamp = timestamp;
        }
        
        // Getters
        public FinancialSummary getFinancial() { return financial; }
        public ComplianceSummary getCompliance() { return compliance; }
        public FraudSummary getFraud() { return fraud; }
        public UserMetrics getUsers() { return users; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    public static class ComplianceDashboard {
        private final List<PolicyViolation> violations;
        private final List<AuditTrailSummary> recentAudits;
        private final ComplianceMetrics metrics;
        private final LocalDateTime timestamp;
        
        public ComplianceDashboard(List<PolicyViolation> violations, List<AuditTrailSummary> recentAudits,
                                 ComplianceMetrics metrics, LocalDateTime timestamp) {
            this.violations = violations;
            this.recentAudits = recentAudits;
            this.metrics = metrics;
            this.timestamp = timestamp;
        }
        
        // Getters
        public List<PolicyViolation> getViolations() { return violations; }
        public List<AuditTrailSummary> getRecentAudits() { return recentAudits; }
        public ComplianceMetrics getMetrics() { return metrics; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    public static class FraudDashboard {
        private final List<FraudCase> activeCases;
        private final FraudStatistics statistics;
        private final List<HighRiskTransaction> highRiskTransactions;
        private final LocalDateTime timestamp;
        
        public FraudDashboard(List<FraudCase> activeCases, FraudStatistics statistics,
                            List<HighRiskTransaction> highRiskTransactions, LocalDateTime timestamp) {
            this.activeCases = activeCases;
            this.statistics = statistics;
            this.highRiskTransactions = highRiskTransactions;
            this.timestamp = timestamp;
        }
        
        // Getters
        public List<FraudCase> getActiveCases() { return activeCases; }
        public FraudStatistics getStatistics() { return statistics; }
        public List<HighRiskTransaction> getHighRiskTransactions() { return highRiskTransactions; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    // Summary DTOs
    public static class FinancialSummary {
        private final BigDecimal totalVolume;
        private final BigDecimal totalValue;
        private final long transactionCount;
        private final long activeAccounts;
        
        public FinancialSummary(BigDecimal totalVolume, BigDecimal totalValue, 
                               long transactionCount, long activeAccounts) {
            this.totalVolume = totalVolume;
            this.totalValue = totalValue;
            this.transactionCount = transactionCount;
            this.activeAccounts = activeAccounts;
        }
        
        // Getters
        public BigDecimal getTotalVolume() { return totalVolume; }
        public BigDecimal getTotalValue() { return totalValue; }
        public long getTransactionCount() { return transactionCount; }
        public long getActiveAccounts() { return activeAccounts; }
    }
    
    public static class ComplianceSummary {
        private final long totalAudits;
        private final long violationCount;
        private final long resolvedViolations;
        private final long activePolicies;
        
        public ComplianceSummary(long totalAudits, long violationCount, 
                               long resolvedViolations, long activePolicies) {
            this.totalAudits = totalAudits;
            this.violationCount = violationCount;
            this.resolvedViolations = resolvedViolations;
            this.activePolicies = activePolicies;
        }
        
        // Getters
        public long getTotalAudits() { return totalAudits; }
        public long getViolationCount() { return violationCount; }
        public long getResolvedViolations() { return resolvedViolations; }
        public long getActivePolicies() { return activePolicies; }
    }
    
    public static class FraudSummary {
        private final long totalCases;
        private final long activeCases;
        private final BigDecimal averageRiskScore;
        private final long blockedTransactions;
        
        public FraudSummary(long totalCases, long activeCases, 
                           BigDecimal averageRiskScore, long blockedTransactions) {
            this.totalCases = totalCases;
            this.activeCases = activeCases;
            this.averageRiskScore = averageRiskScore;
            this.blockedTransactions = blockedTransactions;
        }
        
        // Getters
        public long getTotalCases() { return totalCases; }
        public long getActiveCases() { return activeCases; }
        public BigDecimal getAverageRiskScore() { return averageRiskScore; }
        public long getBlockedTransactions() { return blockedTransactions; }
    }
    
    public static class UserMetrics {
        private final long totalUsers;
        private final long activeUsers;
        private final long newUsers;
        private final long verifiedUsers;
        
        public UserMetrics(long totalUsers, long activeUsers, long newUsers, long verifiedUsers) {
            this.totalUsers = totalUsers;
            this.activeUsers = activeUsers;
            this.newUsers = newUsers;
            this.verifiedUsers = verifiedUsers;
        }
        
        // Getters
        public long getTotalUsers() { return totalUsers; }
        public long getActiveUsers() { return activeUsers; }
        public long getNewUsers() { return newUsers; }
        public long getVerifiedUsers() { return verifiedUsers; }
    }
    
    // Additional DTOs for detailed views
    public static class ReportDefinitionSummary {
        private String id;
        private String name;
        private String description;
        private String category;
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
    }
    
    public static class ReportGenerationRequest {
        private final String reportId;
        private final Map<String, Object> parameters;
        
        public ReportGenerationRequest(String reportId, Map<String, Object> parameters) {
            this.reportId = reportId;
            this.parameters = parameters;
        }
        
        public String getReportId() { return reportId; }
        public Map<String, Object> getParameters() { return parameters; }
    }
    
    public static class ReportJobStatus {
        private String jobId;
        private String status;
        private String downloadUrl;
        
        // Getters and setters
        public String getJobId() { return jobId; }
        public void setJobId(String jobId) { this.jobId = jobId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getDownloadUrl() { return downloadUrl; }
        public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    }
    
    public static class PolicyViolation {
        private UUID id;
        private String policyName;
        private String description;
        private String severity;
        private LocalDateTime timestamp;
        
        // Getters and setters
        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        
        public String getPolicyName() { return policyName; }
        public void setPolicyName(String policyName) { this.policyName = policyName; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
    
    public static class AuditTrailSummary {
        private UUID id;
        private String eventType;
        private String description;
        private LocalDateTime timestamp;
        
        // Getters and setters
        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
    
    public static class ComplianceMetrics {
        private final long totalEvents;
        private final long violations;
        private final BigDecimal complianceScore;
        
        public ComplianceMetrics(long totalEvents, long violations, BigDecimal complianceScore) {
            this.totalEvents = totalEvents;
            this.violations = violations;
            this.complianceScore = complianceScore;
        }
        
        public long getTotalEvents() { return totalEvents; }
        public long getViolations() { return violations; }
        public BigDecimal getComplianceScore() { return complianceScore; }
    }
    
    public static class FraudCase {
        private UUID id;
        private String status;
        private BigDecimal riskScore;
        private String description;
        private LocalDateTime createdAt;
        
        // Getters and setters
        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public BigDecimal getRiskScore() { return riskScore; }
        public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
    
    public static class FraudStatistics {
        private final long totalAssessments;
        private final long flaggedTransactions;
        private final BigDecimal averageRiskScore;
        private final BigDecimal falsePositiveRate;
        
        public FraudStatistics(long totalAssessments, long flaggedTransactions, 
                              BigDecimal averageRiskScore, BigDecimal falsePositiveRate) {
            this.totalAssessments = totalAssessments;
            this.flaggedTransactions = flaggedTransactions;
            this.averageRiskScore = averageRiskScore;
            this.falsePositiveRate = falsePositiveRate;
        }
        
        public long getTotalAssessments() { return totalAssessments; }
        public long getFlaggedTransactions() { return flaggedTransactions; }
        public BigDecimal getAverageRiskScore() { return averageRiskScore; }
        public BigDecimal getFalsePositiveRate() { return falsePositiveRate; }
    }
    
    public static class HighRiskTransaction {
        private UUID id;
        private BigDecimal amount;
        private BigDecimal riskScore;
        private String reason;
        private LocalDateTime timestamp;
        
        // Getters and setters
        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        
        public BigDecimal getRiskScore() { return riskScore; }
        public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
}
