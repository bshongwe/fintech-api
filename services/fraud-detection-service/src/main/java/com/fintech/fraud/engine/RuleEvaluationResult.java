package com.fintech.fraud.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of rule-based fraud evaluation
 */
public class RuleEvaluationResult {
    private List<TriggeredRule> triggeredRules;
    private double maxRiskScore;
    private boolean blockingRuleTriggered;
    
    public RuleEvaluationResult() {
        this.triggeredRules = new ArrayList<>();
        this.maxRiskScore = 0.0;
        this.blockingRuleTriggered = false;
    }
    
    public void addTriggeredRule(String ruleName, double riskScore, String description) {
        TriggeredRule rule = new TriggeredRule(ruleName, riskScore, description);
        triggeredRules.add(rule);
        
        // Update max risk score
        if (riskScore > maxRiskScore) {
            maxRiskScore = riskScore;
        }
        
        // Check if this is a blocking rule (high risk)
        if (riskScore >= 0.7) {
            blockingRuleTriggered = true;
        }
    }
    
    public String getTriggeredRulesJson() {
        if (triggeredRules.isEmpty()) {
            return "[]";
        }
        
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < triggeredRules.size(); i++) {
            if (i > 0) json.append(",");
            TriggeredRule rule = triggeredRules.get(i);
            json.append("{")
                .append("\"name\":\"").append(rule.getName()).append("\",")
                .append("\"riskScore\":").append(rule.getRiskScore()).append(",")
                .append("\"description\":\"").append(rule.getDescription()).append("\"")
                .append("}");
        }
        json.append("]");
        
        return json.toString();
    }
    
    // Getters
    public List<TriggeredRule> getTriggeredRules() { return triggeredRules; }
    public double getMaxRiskScore() { return maxRiskScore; }
    public boolean isBlockingRuleTriggered() { return blockingRuleTriggered; }
    public boolean hasTriggeredRules() { return !triggeredRules.isEmpty(); }
    
    public static class TriggeredRule {
        private String name;
        private double riskScore;
        private String description;
        
        public TriggeredRule(String name, double riskScore, String description) {
            this.name = name;
            this.riskScore = riskScore;
            this.description = description;
        }
        
        // Getters
        public String getName() { return name; }
        public double getRiskScore() { return riskScore; }
        public String getDescription() { return description; }
    }
}
