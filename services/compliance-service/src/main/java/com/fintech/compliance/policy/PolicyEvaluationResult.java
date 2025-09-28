package com.fintech.compliance.policy;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of policy evaluation
 */
public class PolicyEvaluationResult {
    private boolean allowed;
    private List<PolicyViolation> violations;
    private String reason;
    
    public PolicyEvaluationResult() {
        this.violations = new ArrayList<>();
        this.allowed = true;
    }
    
    public boolean isAllowed() { return allowed; }
    public void setAllowed(boolean allowed) { this.allowed = allowed; }
    
    public List<PolicyViolation> getViolations() { return violations; }
    public void addViolation(PolicyViolation violation) { this.violations.add(violation); }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    
    public boolean hasViolations() { return !violations.isEmpty(); }
    public boolean hasBlockingViolations() { 
        return violations.stream().anyMatch(PolicyViolation::isBlocking); 
    }
}

/**
 * Represents a policy violation
 */
class PolicyViolation {
    private String code;
    private String message;
    private boolean blocking;
    
    public PolicyViolation(String code, String message, boolean blocking) {
        this.code = code;
        this.message = message;
        this.blocking = blocking;
    }
    
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public boolean isBlocking() { return blocking; }
}
