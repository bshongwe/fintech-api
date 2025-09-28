package com.fintech.compliance.policy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Policy engine for compliance rule evaluation
 */
@Service
public class PolicyEngine {
    
    private final CompliancePolicyRepository policyRepository;
    
    @Autowired
    public PolicyEngine(CompliancePolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }
    
    /**
     * Evaluate if an action complies with active policies
     */
    public PolicyEvaluationResult evaluate(String actionType, Map<String, Object> context) {
        List<CompliancePolicy> applicablePolicies = policyRepository
            .findByTypeAndActive(getPolicyType(actionType), true);
        
        PolicyEvaluationResult result = new PolicyEvaluationResult();
        result.setAllowed(true);
        
        for (CompliancePolicy policy : applicablePolicies) {
            PolicyViolation violation = evaluatePolicy(policy, context);
            if (violation != null) {
                result.addViolation(violation);
                if (violation.isBlocking()) {
                    result.setAllowed(false);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Evaluate PCI DSS compliance for card data operations
     */
    public PolicyEvaluationResult evaluatePciCompliance(String operation, 
                                                       String userId, 
                                                       Map<String, Object> cardDataContext) {
        List<CompliancePolicy> pciPolicies = policyRepository
            .findByStandardAndActive(CompliancePolicy.ComplianceStandard.PCI_DSS, true);
        
        PolicyEvaluationResult result = new PolicyEvaluationResult();
        result.setAllowed(true);
        
        // Check access controls
        if (!hasValidPciAccess(userId)) {
            result.addViolation(new PolicyViolation("PCI_ACCESS_DENIED", 
                "User does not have PCI DSS clearance", true));
            result.setAllowed(false);
        }
        
        // Check encryption requirements
        if (!isCardDataEncrypted(cardDataContext)) {
            result.addViolation(new PolicyViolation("PCI_ENCRYPTION_REQUIRED", 
                "Card data must be encrypted", true));
            result.setAllowed(false);
        }
        
        return result;
    }
    
    /**
     * Evaluate PSD2 compliance for payment operations
     */
    public PolicyEvaluationResult evaluatePsd2Compliance(String paymentType,
                                                        String userId,
                                                        Map<String, Object> paymentContext) {
        PolicyEvaluationResult result = new PolicyEvaluationResult();
        result.setAllowed(true);
        
        // Check SCA (Strong Customer Authentication)
        if (requiresStrongAuthentication(paymentContext) && !hasStrongAuth(userId)) {
            result.addViolation(new PolicyViolation("PSD2_SCA_REQUIRED", 
                "Strong Customer Authentication required", true));
            result.setAllowed(false);
        }
        
        // Check transaction limits
        Double amount = (Double) paymentContext.get("amount");
        if (amount != null && amount > getPsd2TransactionLimit()) {
            result.addViolation(new PolicyViolation("PSD2_LIMIT_EXCEEDED", 
                "Transaction exceeds PSD2 limits", false));
        }
        
        return result;
    }
    
    private CompliancePolicy.PolicyType getPolicyType(String actionType) {
        // Map action types to policy types
        return switch (actionType.toUpperCase()) {
            case "PAYMENT", "TRANSFER" -> CompliancePolicy.PolicyType.TRANSACTION_LIMIT;
            case "LOGIN", "ACCESS" -> CompliancePolicy.PolicyType.ACCESS_CONTROL;
            case "CARD_DATA", "PII" -> CompliancePolicy.PolicyType.ENCRYPTION;
            default -> CompliancePolicy.PolicyType.AUDIT_LOGGING;
        };
    }
    
    private PolicyViolation evaluatePolicy(CompliancePolicy policy, Map<String, Object> context) {
        // Simple rule evaluation - in production, use a rule engine like Drools
        try {
            // Parse and evaluate rule expression
            // This is a simplified implementation
            return null; // No violation
        } catch (Exception e) {
            return new PolicyViolation(policy.getName(), "Policy evaluation failed", false);
        }
    }
    
    private boolean hasValidPciAccess(String userId) {
        // Check if user has PCI DSS clearance
        return true; // Stub implementation
    }
    
    private boolean isCardDataEncrypted(Map<String, Object> context) {
        // Check if card data is properly encrypted
        return context.containsKey("encrypted") && Boolean.TRUE.equals(context.get("encrypted"));
    }
    
    private boolean requiresStrongAuthentication(Map<String, Object> context) {
        Double amount = (Double) context.get("amount");
        return amount != null && amount > 30.0; // EUR 30 threshold for SCA
    }
    
    private boolean hasStrongAuth(String userId) {
        // Check if user has completed strong authentication
        return true; // Stub implementation
    }
    
    private double getPsd2TransactionLimit() {
        return 15000.0; // EUR 15,000 daily limit
    }
}
