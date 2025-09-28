package com.fintech.fraud.engine;

import com.fintech.fraud.core.FraudAssessment;
import com.fintech.fraud.core.TransactionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Core fraud detection engine that orchestrates all fraud detection components
 * Integrates with Payment Service, Ledger Service, Bank Connectors, and API Gateway
 */
@Service
public class FraudDetectionEngine {
    
    private final RuleEngine ruleEngine;
    private final FeatureExtractor featureExtractor;
    private final RiskScoreCalculator riskCalculator;
    private final ModelInferenceService modelService;
    private final FraudAssessmentRepository assessmentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Autowired
    public FraudDetectionEngine(RuleEngine ruleEngine,
                               FeatureExtractor featureExtractor,
                               RiskScoreCalculator riskCalculator,
                               ModelInferenceService modelService,
                               FraudAssessmentRepository assessmentRepository,
                               KafkaTemplate<String, Object> kafkaTemplate) {
        this.ruleEngine = ruleEngine;
        this.featureExtractor = featureExtractor;
        this.riskCalculator = riskCalculator;
        this.modelService = modelService;
        this.assessmentRepository = assessmentRepository;
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Real-time fraud assessment for Payment Service integration
     * Called before transaction processing
     */
    public CompletableFuture<FraudAssessment> assessTransaction(TransactionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Extract features from transaction context
                List<Double> features = featureExtractor.extractFeatures(context);
                
                // 2. Apply rule-based detection (fast, deterministic)
                RuleEvaluationResult ruleResult = ruleEngine.evaluate(context);
                
                // 3. Calculate risk score using ML model (if available) + rules
                double riskScore = riskCalculator.calculateRiskScore(features, ruleResult);
                
                // 4. Create fraud assessment
                FraudAssessment assessment = new FraudAssessment(
                    context.getTransactionId(),
                    context.getUserId(),
                    riskScore
                );
                
                // 5. Set additional context
                assessment.setSessionId(context.getSessionId());
                assessment.setBankConnectorUsed(context.getBankConnector());
                assessment.setRulesTrigger(ruleResult.getTriggeredRulesJson());
                assessment.setFeatures(featureExtractor.getFeaturesJson(features));
                assessment.setRecommendedAction(determineRecommendedAction(assessment));
                
                // 6. Save assessment
                FraudAssessment savedAssessment = assessmentRepository.save(assessment);
                
                // 7. Stream to Kafka for real-time monitoring and further processing
                kafkaTemplate.send("fraud-assessments", savedAssessment.getTransactionId(), savedAssessment);
                
                // 8. If high risk, also send to admin dashboard topic
                if (assessment.getRiskLevel().ordinal() >= FraudAssessment.RiskLevel.HIGH.ordinal()) {
                    kafkaTemplate.send("high-risk-transactions", savedAssessment.getTransactionId(), savedAssessment);
                }
                
                return savedAssessment;
                
            } catch (Exception e) {
                // Fail-safe: if fraud detection fails, don't block legitimate transactions
                // Log error and return low-risk assessment
                FraudAssessment fallbackAssessment = new FraudAssessment(
                    context.getTransactionId(),
                    context.getUserId(),
                    0.1 // Low risk score as fallback
                );
                fallbackAssessment.setRecommendedAction("PROCEED_WITH_MONITORING");
                return assessmentRepository.save(fallbackAssessment);
            }
        });
    }
    
    /**
     * Batch fraud assessment for Ledger Service integration
     * Analyzes patterns across multiple transactions
     */
    public CompletableFuture<List<FraudAssessment>> assessTransactionBatch(List<TransactionContext> contexts) {
        return CompletableFuture.supplyAsync(() -> {
            // Analyze patterns across the batch
            return contexts.stream()
                .map(context -> assessTransaction(context).join())
                .toList();
        });
    }
    
    /**
     * Post-transaction analysis for pattern detection
     * Called by Ledger Service after transaction completion
     */
    public void analyzePostTransaction(String transactionId, String ledgerEntryId, boolean transactionSucceeded) {
        CompletableFuture.runAsync(() -> {
            try {
                FraudAssessment assessment = assessmentRepository.findByTransactionId(transactionId);
                if (assessment != null) {
                    // Update assessment with actual outcome
                    if (!transactionSucceeded && assessment.getRiskLevel() == FraudAssessment.RiskLevel.LOW) {
                        // Transaction failed despite low risk - possible false negative
                        // Trigger model retraining signal
                        kafkaTemplate.send("model-feedback", transactionId, 
                            new ModelFeedback(transactionId, "FALSE_NEGATIVE", assessment.getRiskScore()));
                    }
                    
                    // Check for velocity patterns with other recent transactions
                    checkVelocityPatterns(assessment.getUserId(), transactionId);
                }
            } catch (Exception e) {
                // Log error but don't fail
            }
        });
    }
    
    /**
     * API Gateway integration - pre-flight fraud check
     * Quick risk assessment before routing to services
     */
    public CompletableFuture<GatewayFraudDecision> preFlightCheck(String userId, String sessionId, 
                                                                 String ipAddress, String endpoint) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Quick behavioral check
                long recentRequests = countRecentRequests(userId, sessionId);
                boolean suspiciousLocation = isLocationSuspicious(ipAddress);
                boolean rateLimitHit = recentRequests > getRateLimit(endpoint);
                
                GatewayFraudDecision decision = new GatewayFraudDecision();
                decision.setUserId(userId);
                decision.setAllowRequest(!rateLimitHit && !suspiciousLocation);
                decision.setRequireAdditionalAuth(suspiciousLocation);
                decision.setRiskScore(calculatePreFlightRisk(recentRequests, suspiciousLocation));
                
                return decision;
            } catch (Exception e) {
                // Fail open for API Gateway
                return GatewayFraudDecision.allowWithMonitoring(userId);
            }
        });
    }
    
    /**
     * Bank Connector integration - validate external transactions
     */
    public CompletableFuture<BankValidationResult> validateWithBankConnector(TransactionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if beneficiary bank account exists and is valid
                // This would integrate with actual bank connector services
                String bankConnector = context.getBankConnector();
                
                // Simulate bank validation (in real implementation, call actual bank APIs)
                boolean accountExists = true; // bankConnectorService.validateAccount()
                boolean accountActive = true; // bankConnectorService.isAccountActive()
                boolean previousTransactions = false; // bankConnectorService.hasPreviousTransactions()
                
                BankValidationResult result = new BankValidationResult();
                result.setBankConnector(bankConnector);
                result.setAccountExists(accountExists);
                result.setAccountActive(accountActive);
                result.setHasPreviousTransactions(previousTransactions);
                result.setValidationPassed(accountExists && accountActive);
                
                // Update fraud assessment with bank validation result
                FraudAssessment assessment = assessmentRepository.findByTransactionId(context.getTransactionId());
                if (assessment != null) {
                    assessment.setBankValidationPassed(result.isValidationPassed());
                    assessmentRepository.save(assessment);
                }
                
                return result;
                
            } catch (Exception e) {
                // If bank validation fails, return inconclusive result
                return BankValidationResult.inconclusive(context.getBankConnector());
            }
        });
    }
    
    private String determineRecommendedAction(FraudAssessment assessment) {
        return switch (assessment.getRiskLevel()) {
            case LOW -> "PROCEED";
            case MEDIUM -> "PROCEED_WITH_MONITORING";
            case HIGH -> "REQUIRE_ADDITIONAL_VERIFICATION";
            case CRITICAL -> "BLOCK_TRANSACTION";
        };
    }
    
    private void checkVelocityPatterns(String userId, String transactionId) {
        // Analyze velocity patterns - implementation would check for:
        // - Rapid successive transactions
        // - Unusual transaction amounts
        // - Geographic anomalies
        // - Time-based patterns
    }
    
    private long countRecentRequests(String userId, String sessionId) {
        // Count recent API requests for rate limiting
        return 0; // Stub implementation
    }
    
    private boolean isLocationSuspicious(String ipAddress) {
        // Check if IP address is from suspicious location
        return false; // Stub implementation
    }
    
    private int getRateLimit(String endpoint) {
        // Get rate limit for specific endpoint
        return switch (endpoint) {
            case "/v1/payments" -> 10; // 10 payments per minute
            case "/v1/transfers" -> 5; // 5 transfers per minute
            default -> 100; // Default rate limit
        };
    }
    
    private double calculatePreFlightRisk(long recentRequests, boolean suspiciousLocation) {
        double risk = 0.0;
        if (recentRequests > 20) risk += 0.3;
        if (suspiciousLocation) risk += 0.4;
        return Math.min(risk, 1.0);
    }
    
    // Inner classes for return types
    public static class GatewayFraudDecision {
        private String userId;
        private boolean allowRequest;
        private boolean requireAdditionalAuth;
        private double riskScore;
        
        public static GatewayFraudDecision allowWithMonitoring(String userId) {
            GatewayFraudDecision decision = new GatewayFraudDecision();
            decision.setUserId(userId);
            decision.setAllowRequest(true);
            decision.setRequireAdditionalAuth(false);
            decision.setRiskScore(0.2);
            return decision;
        }
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public boolean isAllowRequest() { return allowRequest; }
        public void setAllowRequest(boolean allowRequest) { this.allowRequest = allowRequest; }
        public boolean isRequireAdditionalAuth() { return requireAdditionalAuth; }
        public void setRequireAdditionalAuth(boolean requireAdditionalAuth) { this.requireAdditionalAuth = requireAdditionalAuth; }
        public double getRiskScore() { return riskScore; }
        public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
    }
    
    public static class BankValidationResult {
        private String bankConnector;
        private boolean accountExists;
        private boolean accountActive;
        private boolean hasPreviousTransactions;
        private boolean validationPassed;
        
        public static BankValidationResult inconclusive(String bankConnector) {
            BankValidationResult result = new BankValidationResult();
            result.setBankConnector(bankConnector);
            result.setValidationPassed(true); // Fail open
            return result;
        }
        
        // Getters and setters
        public String getBankConnector() { return bankConnector; }
        public void setBankConnector(String bankConnector) { this.bankConnector = bankConnector; }
        public boolean isAccountExists() { return accountExists; }
        public void setAccountExists(boolean accountExists) { this.accountExists = accountExists; }
        public boolean isAccountActive() { return accountActive; }
        public void setAccountActive(boolean accountActive) { this.accountActive = accountActive; }
        public boolean isHasPreviousTransactions() { return hasPreviousTransactions; }
        public void setHasPreviousTransactions(boolean hasPreviousTransactions) { this.hasPreviousTransactions = hasPreviousTransactions; }
        public boolean isValidationPassed() { return validationPassed; }
        public void setValidationPassed(boolean validationPassed) { this.validationPassed = validationPassed; }
    }
    
    public static class ModelFeedback {
        private String transactionId;
        private String feedbackType;
        private double originalRiskScore;
        
        public ModelFeedback(String transactionId, String feedbackType, double originalRiskScore) {
            this.transactionId = transactionId;
            this.feedbackType = feedbackType;
            this.originalRiskScore = originalRiskScore;
        }
        
        // Getters
        public String getTransactionId() { return transactionId; }
        public String getFeedbackType() { return feedbackType; }
        public double getOriginalRiskScore() { return originalRiskScore; }
    }
}
