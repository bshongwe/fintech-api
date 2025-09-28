package com.fintech.reporting.engine;

import com.fintech.reporting.core.ReportDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.*;

/**
 * Executes SQL queries for report generation
 * Handles cross-service data aggregation and secure query execution
 */
@Service
public class QueryExecutionService {
    
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, DataSource> serviceDataSources;
    
    @Autowired
    public QueryExecutionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.serviceDataSources = new HashMap<>();
        // In production, inject multiple data sources for different services
    }
    
    /**
     * Execute report query with parameter substitution
     */
    public ReportDataSet executeQuery(ReportDefinition definition, Map<String, Object> parameters) {
        String query = buildQuery(definition.getQueryTemplate(), parameters);
        
        try {
            // Execute query and map results
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(query);
            
            // Build dataset
            ReportDataSet dataSet = new ReportDataSet();
            dataSet.setRows(rows);
            dataSet.setRowCount(rows.size());
            dataSet.setColumns(extractColumns(rows));
            dataSet.setExecutedQuery(query);
            dataSet.setExecutionTime(System.currentTimeMillis()); // Would measure actual time
            
            return dataSet;
            
        } catch (Exception e) {
            throw new RuntimeException("Query execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute cross-service aggregation query
     * Combines data from multiple services (Payment, Account, Fraud, Compliance)
     */
    public ReportDataSet executeCrossServiceQuery(String reportType, Map<String, Object> parameters) {
        switch (reportType.toLowerCase()) {
            case "fraud-summary":
                return executeFraudSummaryQuery(parameters);
            case "compliance-audit":
                return executeComplianceAuditQuery(parameters);
            case "financial-overview":
                return executeFinancialOverviewQuery(parameters);
            case "transaction-analysis":
                return executeTransactionAnalysisQuery(parameters);
            default:
                throw new IllegalArgumentException("Unknown cross-service report type: " + reportType);
        }
    }
    
    private ReportDataSet executeFraudSummaryQuery(Map<String, Object> parameters) {
        // Aggregate fraud detection data with transaction data
        String query = """
            SELECT 
                DATE(fa.assessed_at) as assessment_date,
                COUNT(*) as total_assessments,
                COUNT(CASE WHEN fa.risk_level = 'HIGH' THEN 1 END) as high_risk_count,
                COUNT(CASE WHEN fa.risk_level = 'CRITICAL' THEN 1 END) as critical_risk_count,
                COUNT(CASE WHEN fa.status = 'BLOCKED' THEN 1 END) as blocked_count,
                AVG(fa.risk_score) as avg_risk_score,
                COUNT(CASE WHEN fa.bank_validation_passed = false THEN 1 END) as failed_validations
            FROM fraud_assessments fa
            WHERE fa.assessed_at BETWEEN ? AND ?
            GROUP BY DATE(fa.assessed_at)
            ORDER BY assessment_date DESC
            """;
        
        return executeParameterizedQuery(query, extractDateRange(parameters));
    }
    
    private ReportDataSet executeComplianceAuditQuery(Map<String, Object> parameters) {
        // Combine audit events with compliance policy violations
        String query = """
            SELECT 
                ae.event_type,
                ae.user_id,
                ae.timestamp,
                ae.level,
                ae.description,
                ae.compliance_flags,
                ae.outcome,
                cp.name as policy_name,
                cp.standard as compliance_standard
            FROM audit_events ae
            LEFT JOIN compliance_policies cp ON ae.compliance_flags LIKE CONCAT('%', cp.standard, '%')
            WHERE ae.timestamp BETWEEN ? AND ?
            AND (ae.compliance_flags IS NOT NULL OR ae.level IN ('HIGH', 'CRITICAL'))
            ORDER BY ae.timestamp DESC
            """;
        
        return executeParameterizedQuery(query, extractDateRange(parameters));
    }
    
    private ReportDataSet executeFinancialOverviewQuery(Map<String, Object> parameters) {
        // Aggregate account balances, transactions, and risk scores
        String query = """
            SELECT 
                a.account_id,
                a.balance,
                a.currency,
                COUNT(t.transaction_id) as transaction_count,
                SUM(t.amount) as total_transaction_amount,
                AVG(fa.risk_score) as avg_risk_score,
                COUNT(CASE WHEN fa.status = 'FLAGGED' THEN 1 END) as flagged_transactions
            FROM account a
            LEFT JOIN transactions t ON a.account_id = t.from_account_id
            LEFT JOIN fraud_assessments fa ON t.transaction_id = fa.transaction_id
            WHERE (? IS NULL OR a.account_id = ?)
            GROUP BY a.account_id, a.balance, a.currency
            """;
        
        String accountId = (String) parameters.get("account_id");
        return executeParameterizedQuery(query, Arrays.asList(accountId, accountId));
    }
    
    private ReportDataSet executeTransactionAnalysisQuery(Map<String, Object> parameters) {
        // Detailed transaction analysis with fraud scores and bank connector data
        String query = """
            SELECT 
                t.transaction_id,
                t.from_account_id,
                t.to_account_id,
                t.amount,
                t.currency,
                t.transaction_type,
                t.timestamp,
                fa.risk_score,
                fa.risk_level,
                fa.status as fraud_status,
                fa.bank_connector_used,
                fa.bank_validation_passed,
                ae.event_type as audit_event
            FROM transactions t
            LEFT JOIN fraud_assessments fa ON t.transaction_id = fa.transaction_id
            LEFT JOIN audit_events ae ON t.transaction_id = ae.resource_id
            WHERE t.timestamp BETWEEN ? AND ?
            ORDER BY t.timestamp DESC
            """;
        
        return executeParameterizedQuery(query, extractDateRange(parameters));
    }
    
    private String buildQuery(String template, Map<String, Object> parameters) {
        String query = template;
        
        // Simple parameter substitution (in production, use proper prepared statements)
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = String.valueOf(entry.getValue());
            query = query.replace(placeholder, value);
        }
        
        return query;
    }
    
    private ReportDataSet executeParameterizedQuery(String query, List<Object> parameters) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(query, parameters.toArray());
            
            ReportDataSet dataSet = new ReportDataSet();
            dataSet.setRows(rows);
            dataSet.setRowCount(rows.size());
            dataSet.setColumns(extractColumns(rows));
            dataSet.setExecutedQuery(query);
            
            return dataSet;
            
        } catch (Exception e) {
            throw new RuntimeException("Parameterized query execution failed: " + e.getMessage(), e);
        }
    }
    
    private List<String> extractColumns(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(rows.get(0).keySet());
    }
    
    private List<Object> extractDateRange(Map<String, Object> parameters) {
        Object startDate = parameters.get("start_date");
        Object endDate = parameters.get("end_date");
        
        if (startDate == null || endDate == null) {
            // Default to last 30 days
            java.time.Instant now = java.time.Instant.now();
            java.time.Instant thirtyDaysAgo = now.minus(30, java.time.temporal.ChronoUnit.DAYS);
            return Arrays.asList(thirtyDaysAgo, now);
        }
        
        return Arrays.asList(startDate, endDate);
    }
}

/**
 * Report dataset container
 */
class ReportDataSet {
    private List<Map<String, Object>> rows;
    private List<String> columns;
    private int rowCount;
    private String executedQuery;
    private long executionTime;
    
    // Getters and setters
    public List<Map<String, Object>> getRows() { return rows; }
    public void setRows(List<Map<String, Object>> rows) { this.rows = rows; }
    
    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }
    
    public int getRowCount() { return rowCount; }
    public void setRowCount(int rowCount) { this.rowCount = rowCount; }
    
    public String getExecutedQuery() { return executedQuery; }
    public void setExecutedQuery(String executedQuery) { this.executedQuery = executedQuery; }
    
    public long getExecutionTime() { return executionTime; }
    public void setExecutionTime(long executionTime) { this.executionTime = executionTime; }
}
