package com.fintech.fraud.engine;

import com.fintech.fraud.core.TransactionContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Feature extraction for ML model input
 * Converts transaction context into numerical features
 */
@Component
public class FeatureExtractor {
    
    /**
     * Extract features for ML model input
     * Returns normalized feature vector (0.0 - 1.0)
     */
    public List<Double> extractFeatures(TransactionContext context) {
        List<Double> features = new ArrayList<>();
        
        // 1. Transaction amount features (normalized)
        features.add(normalizeAmount(context.getAmount()));
        features.add(getAmountPercentileVsHistory(context));
        
        // 2. Time-based features
        features.add(getHourOfDayNormalized(context.getTimestamp()));
        features.add(getDayOfWeekNormalized(context.getTimestamp()));
        features.add(isWeekend(context.getTimestamp()) ? 1.0 : 0.0);
        features.add(isOutsideBusinessHours(context.getTimestamp()) ? 1.0 : 0.0);
        
        // 3. Velocity features
        features.add(normalizeCount(context.getTransactionCountToday()));
        features.add(normalizeAmount(context.getTotalAmountToday()));
        features.add(getTimeSinceLastTransaction(context));
        
        // 4. Account features
        features.add(getAccountAgeInDays(context.getFromAccountCreatedAt()));
        features.add(getBalanceRatio(context.getAmount(), context.getFromAccountBalance()));
        
        // 5. Device and location features
        features.add(context.isFirstTimeDevice() ? 1.0 : 0.0);
        features.add(context.isFirstTimeLocation() ? 1.0 : 0.0);
        
        // 6. Transaction type features (one-hot encoding)
        features.addAll(encodeTransactionType(context.getTransactionType()));
        
        // 7. Bank connector features
        features.add(context.isExternalTransfer() ? 1.0 : 0.0);
        features.add(context.isBeneficiaryVerified() ? 1.0 : 0.0);
        
        // 8. Risk indicator features
        features.add(context.isUnusualAmount() ? 1.0 : 0.0);
        features.add(context.isUnusualRecipient() ? 1.0 : 0.0);
        features.add(context.isRapidFireTransaction() ? 1.0 : 0.0);
        features.add(context.isHasRecentFailedTransactions() ? 1.0 : 0.0);
        
        return features;
    }
    
    /**
     * Convert features to JSON for storage
     */
    public String getFeaturesJson(List<Double> features) {
        StringBuilder json = new StringBuilder("{");
        String[] featureNames = getFeatureNames();
        
        for (int i = 0; i < Math.min(features.size(), featureNames.length); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(featureNames[i]).append("\":").append(features.get(i));
        }
        
        json.append("}");
        return json.toString();
    }
    
    private String[] getFeatureNames() {
        return new String[]{
            "amount_normalized", "amount_percentile", "hour_of_day", "day_of_week",
            "is_weekend", "outside_business_hours", "transaction_count_today",
            "total_amount_today", "time_since_last", "account_age_days",
            "balance_ratio", "first_time_device", "first_time_location",
            "type_transfer", "type_payment", "type_withdrawal", "type_other",
            "external_transfer", "beneficiary_verified", "unusual_amount",
            "unusual_recipient", "rapid_fire", "recent_failures"
        };
    }
    
    private double normalizeAmount(BigDecimal amount) {
        if (amount == null) return 0.0;
        // Normalize using log scale for amounts (handles wide range)
        double amountDouble = amount.doubleValue();
        return Math.min(Math.log10(amountDouble + 1) / 6.0, 1.0); // Assuming max 1M
    }
    
    private double getAmountPercentileVsHistory(TransactionContext context) {
        // Compare current amount to user's historical average
        if (context.getAverageTransactionAmount() == null || context.getAmount() == null) {
            return 0.5; // Neutral if no history
        }
        
        double ratio = context.getAmount().doubleValue() / 
                      context.getAverageTransactionAmount().doubleValue();
        
        // Convert ratio to percentile-like score
        return Math.min(Math.tanh(ratio - 1.0) * 0.5 + 0.5, 1.0);
    }
    
    private double getHourOfDayNormalized(Instant timestamp) {
        LocalTime time = LocalTime.ofInstant(timestamp, java.time.ZoneOffset.UTC);
        return time.getHour() / 24.0;
    }
    
    private double getDayOfWeekNormalized(Instant timestamp) {
        int dayOfWeek = timestamp.atZone(java.time.ZoneOffset.UTC).getDayOfWeek().getValue();
        return (dayOfWeek - 1) / 6.0; // 0-6 normalized to 0-1
    }
    
    private boolean isWeekend(Instant timestamp) {
        int dayOfWeek = timestamp.atZone(java.time.ZoneOffset.UTC).getDayOfWeek().getValue();
        return dayOfWeek >= 6; // Saturday = 6, Sunday = 7
    }
    
    private boolean isOutsideBusinessHours(Instant timestamp) {
        LocalTime time = LocalTime.ofInstant(timestamp, java.time.ZoneOffset.UTC);
        return time.isBefore(LocalTime.of(8, 0)) || time.isAfter(LocalTime.of(18, 0));
    }
    
    private double normalizeCount(int count) {
        // Normalize transaction count (assuming max 50 per day)
        return Math.min(count / 50.0, 1.0);
    }
    
    private double getTimeSinceLastTransaction(TransactionContext context) {
        if (context.getLastTransactionTime() == null) {
            return 1.0; // Long time since last (or first transaction)
        }
        
        Duration duration = Duration.between(context.getLastTransactionTime(), context.getTimestamp());
        long minutes = duration.toMinutes();
        
        // Normalize: 0 = immediate, 1 = > 24 hours
        return Math.min(minutes / (24.0 * 60.0), 1.0);
    }
    
    private double getAccountAgeInDays(Instant accountCreatedAt) {
        if (accountCreatedAt == null) return 0.0;
        
        Duration age = Duration.between(accountCreatedAt, Instant.now());
        long days = age.toDays();
        
        // Normalize: newer accounts = higher risk
        return Math.min(days / 365.0, 1.0); // 0 = new account, 1 = 1+ years old
    }
    
    private double getBalanceRatio(BigDecimal amount, BigDecimal balance) {
        if (amount == null || balance == null || balance.equals(BigDecimal.ZERO)) {
            return 0.5; // Neutral if unknown
        }
        
        double ratio = amount.doubleValue() / balance.doubleValue();
        return Math.min(ratio, 1.0); // Cap at 1.0 (100% of balance)
    }
    
    private List<Double> encodeTransactionType(String transactionType) {
        // One-hot encoding for transaction types
        List<Double> encoded = new ArrayList<>();
        
        encoded.add("TRANSFER".equals(transactionType) ? 1.0 : 0.0);
        encoded.add("PAYMENT".equals(transactionType) ? 1.0 : 0.0);
        encoded.add("WITHDRAWAL".equals(transactionType) ? 1.0 : 0.0);
        encoded.add(!"TRANSFER".equals(transactionType) && 
                   !"PAYMENT".equals(transactionType) && 
                   !"WITHDRAWAL".equals(transactionType) ? 1.0 : 0.0); // Other
        
        return encoded;
    }
}
