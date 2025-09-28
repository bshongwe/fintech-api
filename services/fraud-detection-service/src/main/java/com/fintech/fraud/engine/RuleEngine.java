package com.fintech.fraud.engine;

import com.fintech.fraud.core.TransactionContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based fraud detection engine
 * Fast, deterministic rules that complement ML models
 */
@Component
public class RuleEngine {
    
    /**
     * Evaluate transaction against all fraud rules
     */
    public RuleEvaluationResult evaluate(TransactionContext context) {
        RuleEvaluationResult result = new RuleEvaluationResult();
        
        // High-risk rules (immediate blocking)
        evaluateHighRiskRules(context, result);
        
        // Medium-risk rules (flagging/monitoring)
        evaluateMediumRiskRules(context, result);
        
        // Low-risk rules (informational)
        evaluateLowRiskRules(context, result);
        
        return result;
    }
    
    private void evaluateHighRiskRules(TransactionContext context, RuleEvaluationResult result) {
        
        // Rule: Massive amount transaction
        if (context.getAmount() != null && context.getAmount().compareTo(new BigDecimal("1000000")) > 0) {
            result.addTriggeredRule("MASSIVE_AMOUNT", 0.9, "Transaction amount exceeds 1M");
        }
        
        // Rule: Account balance drain (>95% of balance)
        if (isAccountBalanceDrain(context)) {
            result.addTriggeredRule("BALANCE_DRAIN", 0.8, "Transaction drains >95% of account balance");
        }
        
        // Rule: Rapid fire transactions (>10 in 5 minutes)
        if (context.isRapidFireTransaction()) {
            result.addTriggeredRule("RAPID_FIRE", 0.7, "Too many transactions in short time");
        }
        
        // Rule: First transaction from new device with high amount
        if (context.isFirstTimeDevice() && isHighAmount(context.getAmount())) {
            result.addTriggeredRule("NEW_DEVICE_HIGH_AMOUNT", 0.75, "High amount from new device");
        }
        
        // Rule: Transaction from blacklisted location/IP
        if (isFromBlacklistedLocation(context.getIpAddress())) {
            result.addTriggeredRule("BLACKLISTED_LOCATION", 0.95, "Transaction from blacklisted location");
        }
    }
    
    private void evaluateMediumRiskRules(TransactionContext context, RuleEvaluationResult result) {
        
        // Rule: Unusual time (outside normal user patterns)
        if (context.isOutsideNormalHours()) {
            result.addTriggeredRule("UNUSUAL_TIME", 0.4, "Transaction outside normal hours");
        }
        
        // Rule: First time location
        if (context.isFirstTimeLocation()) {
            result.addTriggeredRule("NEW_LOCATION", 0.3, "Transaction from new location");
        }
        
        // Rule: Unusual recipient
        if (context.isUnusualRecipient()) {
            result.addTriggeredRule("UNUSUAL_RECIPIENT", 0.5, "Transfer to unusual recipient");
        }
        
        // Rule: Amount much higher than average
        if (isAmountUnusuallyHigh(context)) {
            result.addTriggeredRule("UNUSUAL_AMOUNT", 0.4, "Amount significantly higher than average");
        }
        
        // Rule: Cross-border transfer without verification
        if (context.isExternalTransfer() && !context.isBeneficiaryVerified()) {
            result.addTriggeredRule("UNVERIFIED_EXTERNAL", 0.6, "External transfer to unverified account");
        }
        
        // Rule: Multiple failed transactions recently
        if (context.isHasRecentFailedTransactions()) {
            result.addTriggeredRule("RECENT_FAILURES", 0.3, "Recent failed transaction attempts");
        }
        
        // Rule: Velocity - too many transactions today
        if (context.getTransactionCountToday() > 20) {
            result.addTriggeredRule("HIGH_VELOCITY", 0.5, "Too many transactions today");
        }
        
        // Rule: Daily limit exceeded
        if (isDailyLimitExceeded(context)) {
            result.addTriggeredRule("DAILY_LIMIT", 0.6, "Daily transaction limit exceeded");
        }
    }
    
    private void evaluateLowRiskRules(TransactionContext context, RuleEvaluationResult result) {
        
        // Rule: New account (< 30 days)
        if (isNewAccount(context.getFromAccountCreatedAt())) {
            result.addTriggeredRule("NEW_ACCOUNT", 0.2, "Transaction from new account");
        }
        
        // Rule: Round amount (potential money laundering indicator)
        if (isRoundAmount(context.getAmount())) {
            result.addTriggeredRule("ROUND_AMOUNT", 0.1, "Round amount transaction");
        }
        
        // Rule: Weekend transaction
        if (isWeekendTransaction(context.getTimestamp())) {
            result.addTriggeredRule("WEEKEND_TRANSACTION", 0.1, "Weekend transaction");
        }
        
        // Rule: First transaction of the day
        if (context.getTransactionCountToday() == 1) {
            result.addTriggeredRule("FIRST_DAILY", 0.05, "First transaction of the day");
        }
    }
    
    // Helper methods for rule evaluation
    
    private boolean isAccountBalanceDrain(TransactionContext context) {
        if (context.getAmount() == null || context.getFromAccountBalance() == null) {
            return false;
        }
        
        BigDecimal percentage = context.getAmount()
            .divide(context.getFromAccountBalance(), 4, java.math.RoundingMode.HALF_UP);
        
        return percentage.compareTo(new BigDecimal("0.95")) > 0;
    }
    
    private boolean isHighAmount(BigDecimal amount) {
        return amount != null && amount.compareTo(new BigDecimal("50000")) > 0; // >50K
    }
    
    private boolean isFromBlacklistedLocation(String ipAddress) {
        // Check against blacklisted IP ranges/countries
        // This would integrate with external threat intelligence
        return false; // Stub implementation
    }
    
    private boolean isAmountUnusuallyHigh(TransactionContext context) {
        if (context.getAmount() == null || context.getAverageTransactionAmount() == null) {
            return false;
        }
        
        BigDecimal ratio = context.getAmount()
            .divide(context.getAverageTransactionAmount(), 4, java.math.RoundingMode.HALF_UP);
        
        return ratio.compareTo(new BigDecimal("5.0")) > 0; // 5x higher than average
    }
    
    private boolean isDailyLimitExceeded(TransactionContext context) {
        if (context.getTotalAmountToday() == null) return false;
        
        BigDecimal dailyLimit = new BigDecimal("100000"); // 100K daily limit
        return context.getTotalAmountToday().compareTo(dailyLimit) > 0;
    }
    
    private boolean isNewAccount(Instant accountCreatedAt) {
        if (accountCreatedAt == null) return true;
        
        Duration age = Duration.between(accountCreatedAt, Instant.now());
        return age.toDays() < 30;
    }
    
    private boolean isRoundAmount(BigDecimal amount) {
        if (amount == null) return false;
        
        // Check if amount is a round number (e.g., 1000, 5000, 10000)
        return amount.remainder(new BigDecimal("1000")).equals(BigDecimal.ZERO);
    }
    
    private boolean isWeekendTransaction(Instant timestamp) {
        int dayOfWeek = timestamp.atZone(java.time.ZoneOffset.UTC).getDayOfWeek().getValue();
        return dayOfWeek >= 6; // Saturday = 6, Sunday = 7
    }
}
