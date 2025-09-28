package com.fintech.fraud.core;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;

/**
 * Fraud detection result for Payment Service integration
 */
@Entity
@Table(name = "fraud_assessments", indexes = {
    @Index(name = "idx_transaction_id", columnList = "transactionId"),
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_risk_score", columnList = "riskScore"),
    @Index(name = "idx_status", columnList = "status")
})
public class FraudAssessment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String transactionId; // Links to Payment Service
    
    @Column(nullable = false)
    private String userId;
    
    @Column
    private String sessionId;
    
    @Column(nullable = false)
    private Double riskScore; // 0.0 - 1.0
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FraudStatus status;
    
    @Column(columnDefinition = "TEXT")
    private String rulesTrigger; // JSON array of triggered rules
    
    @Column(columnDefinition = "TEXT")
    private String features; // JSON of extracted features
    
    @Column
    private String recommendedAction;
    
    @Column
    private String reviewedBy; // For manual review integration
    
    @Column
    private Instant assessedAt;
    
    @Column
    private Instant reviewedAt;
    
    @Column
    private String bankConnectorUsed; // Which bank connector validated this
    
    @Column
    private Boolean bankValidationPassed;
    
    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public enum FraudStatus {
        PENDING,      // Initial assessment
        APPROVED,     // Low risk, proceed
        FLAGGED,      // Medium risk, monitor
        BLOCKED,      // High risk, deny transaction
        REVIEWING,    // Manual review required
        CONFIRMED_FRAUD, // Confirmed fraudulent
        FALSE_POSITIVE   // Was flagged but legitimate
    }
    
    // Constructors
    protected FraudAssessment() {}
    
    public FraudAssessment(String transactionId, String userId, Double riskScore) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.riskScore = riskScore;
        this.riskLevel = calculateRiskLevel(riskScore);
        this.status = determineInitialStatus(riskLevel);
        this.assessedAt = Instant.now();
    }
    
    private RiskLevel calculateRiskLevel(Double score) {
        if (score >= 0.8) return RiskLevel.CRITICAL;
        if (score >= 0.6) return RiskLevel.HIGH;
        if (score >= 0.3) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
    
    private FraudStatus determineInitialStatus(RiskLevel level) {
        return switch (level) {
            case LOW -> FraudStatus.APPROVED;
            case MEDIUM -> FraudStatus.FLAGGED;
            case HIGH -> FraudStatus.REVIEWING;
            case CRITICAL -> FraudStatus.BLOCKED;
        };
    }
    
    // Getters and setters
    public String getId() { return id; }
    public String getTransactionId() { return transactionId; }
    public String getUserId() { return userId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public Double getRiskScore() { return riskScore; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public FraudStatus getStatus() { return status; }
    public void setStatus(FraudStatus status) { this.status = status; }
    
    public String getRulesTrigger() { return rulesTrigger; }
    public void setRulesTrigger(String rulesTrigger) { this.rulesTrigger = rulesTrigger; }
    
    public String getFeatures() { return features; }
    public void setFeatures(String features) { this.features = features; }
    
    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }
    
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { 
        this.reviewedBy = reviewedBy; 
        this.reviewedAt = Instant.now();
    }
    
    public Instant getAssessedAt() { return assessedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    
    public String getBankConnectorUsed() { return bankConnectorUsed; }
    public void setBankConnectorUsed(String bankConnectorUsed) { this.bankConnectorUsed = bankConnectorUsed; }
    
    public Boolean getBankValidationPassed() { return bankValidationPassed; }
    public void setBankValidationPassed(Boolean bankValidationPassed) { this.bankValidationPassed = bankValidationPassed; }
    
    // Business methods
    public boolean requiresReview() {
        return status == FraudStatus.REVIEWING || riskLevel == RiskLevel.HIGH;
    }
    
    public boolean shouldBlock() {
        return status == FraudStatus.BLOCKED || riskLevel == RiskLevel.CRITICAL;
    }
    
    public boolean canProceed() {
        return status == FraudStatus.APPROVED && riskLevel == RiskLevel.LOW;
    }
}
