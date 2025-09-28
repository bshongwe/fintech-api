package com.fintech.reporting.engine;

import com.fintech.reporting.core.ReportDefinition;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Data aggregation service for summary and analytical reports
 * Provides statistical analysis and grouping capabilities
 */
@Service
public class DataAggregationService {
    
    /**
     * Aggregate data based on report definition
     */
    public ReportDataSet aggregateData(ReportDataSet dataSet, ReportDefinition definition) {
        switch (definition.getType()) {
            case SUMMARY:
                return createSummaryAggregation(dataSet);
            case TIME_SERIES:
                return createTimeSeriesAggregation(dataSet);
            case DRILL_DOWN:
                return createDrillDownAggregation(dataSet);
            default:
                return dataSet; // No aggregation needed
        }
    }
    
    /**
     * Create summary statistics from raw data
     */
    private ReportDataSet createSummaryAggregation(ReportDataSet dataSet) {
        if (dataSet.getRows().isEmpty()) {
            return dataSet;
        }
        
        List<Map<String, Object>> aggregatedRows = new ArrayList<>();
        
        // Group by date for financial summaries
        Map<String, List<Map<String, Object>>> groupedByDate = groupByDateField(dataSet.getRows());
        
        for (Map.Entry<String, List<Map<String, Object>>> entry : groupedByDate.entrySet()) {
            Map<String, Object> summaryRow = new HashMap<>();
            summaryRow.put("date", entry.getKey());
            
            List<Map<String, Object>> dayRows = entry.getValue();
            
            // Calculate aggregations
            summaryRow.put("transaction_count", dayRows.size());
            summaryRow.put("total_amount", calculateSum(dayRows, "amount"));
            summaryRow.put("avg_amount", calculateAverage(dayRows, "amount"));
            summaryRow.put("max_amount", calculateMax(dayRows, "amount"));
            summaryRow.put("min_amount", calculateMin(dayRows, "amount"));
            summaryRow.put("high_risk_count", countByField(dayRows, "risk_level", "HIGH"));
            summaryRow.put("blocked_count", countByField(dayRows, "fraud_status", "BLOCKED"));
            summaryRow.put("avg_risk_score", calculateAverage(dayRows, "risk_score"));
            
            aggregatedRows.add(summaryRow);
        }
        
        // Sort by date descending
        aggregatedRows.sort((a, b) -> ((String) b.get("date")).compareTo((String) a.get("date")));
        
        ReportDataSet aggregatedDataSet = new ReportDataSet();
        aggregatedDataSet.setRows(aggregatedRows);
        aggregatedDataSet.setRowCount(aggregatedRows.size());
        aggregatedDataSet.setColumns(Arrays.asList("date", "transaction_count", "total_amount", 
                                                  "avg_amount", "max_amount", "min_amount", 
                                                  "high_risk_count", "blocked_count", "avg_risk_score"));
        
        return aggregatedDataSet;
    }
    
    /**
     * Create time series data for trending analysis
     */
    private ReportDataSet createTimeSeriesAggregation(ReportDataSet dataSet) {
        if (dataSet.getRows().isEmpty()) {
            return dataSet;
        }
        
        // Group by hour for intraday analysis
        Map<String, List<Map<String, Object>>> groupedByHour = groupByHourField(dataSet.getRows());
        
        List<Map<String, Object>> timeSeriesRows = new ArrayList<>();
        
        for (Map.Entry<String, List<Map<String, Object>>> entry : groupedByHour.entrySet()) {
            Map<String, Object> timePoint = new HashMap<>();
            timePoint.put("time_period", entry.getKey());
            
            List<Map<String, Object>> periodRows = entry.getValue();
            
            // Time series metrics
            timePoint.put("volume", periodRows.size());
            timePoint.put("total_value", calculateSum(periodRows, "amount"));
            timePoint.put("fraud_rate", calculateFraudRate(periodRows));
            timePoint.put("avg_risk_score", calculateAverage(periodRows, "risk_score"));
            timePoint.put("unique_users", countUniqueValues(periodRows, "user_id"));
            
            timeSeriesRows.add(timePoint);
        }
        
        // Sort chronologically
        timeSeriesRows.sort(Comparator.comparing(row -> (String) row.get("time_period")));
        
        ReportDataSet timeSeriesDataSet = new ReportDataSet();
        timeSeriesDataSet.setRows(timeSeriesRows);
        timeSeriesDataSet.setRowCount(timeSeriesRows.size());
        timeSeriesDataSet.setColumns(Arrays.asList("time_period", "volume", "total_value", 
                                                  "fraud_rate", "avg_risk_score", "unique_users"));
        
        return timeSeriesDataSet;
    }
    
    /**
     * Create hierarchical drill-down data
     */
    private ReportDataSet createDrillDownAggregation(ReportDataSet dataSet) {
        if (dataSet.getRows().isEmpty()) {
            return dataSet;
        }
        
        List<Map<String, Object>> drillDownRows = new ArrayList<>();
        
        // Group by transaction type, then by risk level
        Map<String, Map<String, List<Map<String, Object>>>> hierarchy = 
            dataSet.getRows().stream()
                .collect(Collectors.groupingBy(
                    row -> (String) row.getOrDefault("transaction_type", "Unknown"),
                    Collectors.groupingBy(
                        row -> (String) row.getOrDefault("risk_level", "Unknown")
                    )
                ));
        
        for (Map.Entry<String, Map<String, List<Map<String, Object>>>> typeEntry : hierarchy.entrySet()) {
            String transactionType = typeEntry.getKey();
            
            for (Map.Entry<String, List<Map<String, Object>>> riskEntry : typeEntry.getValue().entrySet()) {
                String riskLevel = riskEntry.getKey();
                List<Map<String, Object>> rows = riskEntry.getValue();
                
                Map<String, Object> drillDownRow = new HashMap<>();
                drillDownRow.put("transaction_type", transactionType);
                drillDownRow.put("risk_level", riskLevel);
                drillDownRow.put("count", rows.size());
                drillDownRow.put("total_amount", calculateSum(rows, "amount"));
                drillDownRow.put("avg_amount", calculateAverage(rows, "amount"));
                drillDownRow.put("success_rate", calculateSuccessRate(rows));
                
                drillDownRows.add(drillDownRow);
            }
        }
        
        ReportDataSet drillDownDataSet = new ReportDataSet();
        drillDownDataSet.setRows(drillDownRows);
        drillDownDataSet.setRowCount(drillDownRows.size());
        drillDownDataSet.setColumns(Arrays.asList("transaction_type", "risk_level", "count", 
                                                  "total_amount", "avg_amount", "success_rate"));
        
        return drillDownDataSet;
    }
    
    // Helper methods for calculations
    
    private Map<String, List<Map<String, Object>>> groupByDateField(List<Map<String, Object>> rows) {
        return rows.stream()
            .collect(Collectors.groupingBy(row -> {
                Object timestamp = row.get("timestamp");
                if (timestamp != null) {
                    return timestamp.toString().substring(0, 10); // Extract date part
                }
                return "Unknown";
            }));
    }
    
    private Map<String, List<Map<String, Object>>> groupByHourField(List<Map<String, Object>> rows) {
        return rows.stream()
            .collect(Collectors.groupingBy(row -> {
                Object timestamp = row.get("timestamp");
                if (timestamp != null) {
                    return timestamp.toString().substring(0, 13); // Extract date + hour
                }
                return "Unknown";
            }));
    }
    
    private double calculateSum(List<Map<String, Object>> rows, String field) {
        return rows.stream()
            .mapToDouble(row -> {
                Object value = row.get(field);
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                return 0.0;
            })
            .sum();
    }
    
    private double calculateAverage(List<Map<String, Object>> rows, String field) {
        return rows.stream()
            .mapToDouble(row -> {
                Object value = row.get(field);
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                return 0.0;
            })
            .average()
            .orElse(0.0);
    }
    
    private double calculateMax(List<Map<String, Object>> rows, String field) {
        return rows.stream()
            .mapToDouble(row -> {
                Object value = row.get(field);
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                return 0.0;
            })
            .max()
            .orElse(0.0);
    }
    
    private double calculateMin(List<Map<String, Object>> rows, String field) {
        return rows.stream()
            .mapToDouble(row -> {
                Object value = row.get(field);
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                return 0.0;
            })
            .min()
            .orElse(0.0);
    }
    
    private long countByField(List<Map<String, Object>> rows, String field, String value) {
        return rows.stream()
            .filter(row -> value.equals(row.get(field)))
            .count();
    }
    
    private double calculateFraudRate(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return 0.0;
        
        long fraudCount = countByField(rows, "fraud_status", "BLOCKED") + 
                         countByField(rows, "fraud_status", "FLAGGED");
        
        return (double) fraudCount / rows.size() * 100.0; // Percentage
    }
    
    private double calculateSuccessRate(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return 0.0;
        
        long successCount = countByField(rows, "outcome", "SUCCESS");
        return (double) successCount / rows.size() * 100.0; // Percentage
    }
    
    private long countUniqueValues(List<Map<String, Object>> rows, String field) {
        return rows.stream()
            .map(row -> row.get(field))
            .filter(Objects::nonNull)
            .distinct()
            .count();
    }
}
