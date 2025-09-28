package com.fintech.fraud.engine;

import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Risk score calculator combining rule-based and ML-based approaches
 */
@Component
public class RiskScoreCalculator {
    
    private static final double RULE_WEIGHT = 0.6;  // 60% weight to rules
    private static final double ML_WEIGHT = 0.4;    // 40% weight to ML model
    
    /**
     * Calculate combined risk score from rules and ML features
     */
    public double calculateRiskScore(List<Double> mlFeatures, RuleEvaluationResult ruleResult) {
        // Rule-based score
        double ruleScore = calculateRuleScore(ruleResult);
        
        // ML-based score (placeholder - would use actual ML model)
        double mlScore = calculateMlScore(mlFeatures);
        
        // Combined weighted score
        double combinedScore = (ruleScore * RULE_WEIGHT) + (mlScore * ML_WEIGHT);
        
        // Apply rule overrides for critical cases
        if (ruleResult.isBlockingRuleTriggered()) {
            combinedScore = Math.max(combinedScore, 0.8); // Ensure high risk for blocking rules
        }
        
        return Math.min(Math.max(combinedScore, 0.0), 1.0); // Clamp to [0,1]
    }
    
    /**
     * Calculate risk score from triggered rules
     */
    private double calculateRuleScore(RuleEvaluationResult ruleResult) {
        if (!ruleResult.hasTriggeredRules()) {
            return 0.1; // Base risk for no triggered rules
        }
        
        // Use maximum rule score with diminishing returns for multiple rules
        double maxScore = ruleResult.getMaxRiskScore();
        int ruleCount = ruleResult.getTriggeredRules().size();
        
        // Add bonus for multiple triggered rules (but with diminishing returns)
        double multiRuleBonus = Math.min(ruleCount * 0.05, 0.2); // Max 20% bonus
        
        return Math.min(maxScore + multiRuleBonus, 1.0);
    }
    
    /**
     * Calculate ML-based risk score
     * In production, this would call an actual ML model (TensorFlow, PyTorch, etc.)
     */
    private double calculateMlScore(List<Double> features) {
        if (features == null || features.isEmpty()) {
            return 0.3; // Default moderate risk if no features
        }
        
        // Simplified ML model simulation
        // In production, this would be replaced with actual model inference
        double score = 0.0;
        
        // Simple weighted sum of key features (placeholder logic)
        // Feature indices based on FeatureExtractor order
        try {
            // Amount-based features (higher amounts = higher risk)
            if (features.size() > 0) score += features.get(0) * 0.3; // amount_normalized
            if (features.size() > 1) score += features.get(1) * 0.2; // amount_percentile
            
            // Time-based features
            if (features.size() > 5) score += features.get(5) * 0.1; // outside_business_hours
            
            // Velocity features
            if (features.size() > 6) score += features.get(6) * 0.15; // transaction_count_today
            if (features.size() > 8) score += (1.0 - features.get(8)) * 0.1; // time_since_last (less time = higher risk)
            
            // Device/location features
            if (features.size() > 11) score += features.get(11) * 0.2; // first_time_device
            if (features.size() > 12) score += features.get(12) * 0.15; // first_time_location
            
            // Risk indicators
            if (features.size() > 19) score += features.get(19) * 0.25; // unusual_amount
            if (features.size() > 20) score += features.get(20) * 0.2;  // unusual_recipient
            if (features.size() > 21) score += features.get(21) * 0.3;  // rapid_fire
            if (features.size() > 22) score += features.get(22) * 0.15; // recent_failures
            
        } catch (IndexOutOfBoundsException e) {
            // Handle missing features gracefully
            return 0.3; // Default risk
        }
        
        return Math.min(score, 1.0);
    }
    
    /**
     * Calculate risk score for API Gateway pre-flight checks
     */
    public double calculatePreFlightRisk(long requestCount, boolean suspiciousLocation, 
                                       boolean rateLimited, String endpoint) {
        double risk = 0.0;
        
        // Rate limiting factor
        if (rateLimited) risk += 0.4;
        else if (requestCount > 50) risk += 0.2;
        else if (requestCount > 20) risk += 0.1;
        
        // Location factor
        if (suspiciousLocation) risk += 0.3;
        
        // Endpoint sensitivity factor
        risk += getEndpointRiskMultiplier(endpoint);
        
        return Math.min(risk, 1.0);
    }
    
    private double getEndpointRiskMultiplier(String endpoint) {
        return switch (endpoint.toLowerCase()) {
            case "/v1/payments", "/v1/transfers" -> 0.1; // High-value endpoints
            case "/v1/accounts" -> 0.05; // Medium-value endpoints
            default -> 0.0; // Low-value endpoints
        };
    }
    
    /**
     * Calculate risk adjustment based on bank connector validation
     */
    public double adjustRiskForBankValidation(double originalRisk, boolean bankValidationPassed, 
                                            boolean accountExists, boolean hasPreviousTransactions) {
        double adjustment = 0.0;
        
        if (!bankValidationPassed) {
            adjustment += 0.3; // Significant risk increase for validation failure
        }
        
        if (!accountExists) {
            adjustment += 0.4; // High risk for non-existent accounts
        }
        
        if (!hasPreviousTransactions && originalRisk < 0.5) {
            adjustment += 0.2; // Increase risk for first-time recipients
        }
        
        return Math.min(originalRisk + adjustment, 1.0);
    }
}
