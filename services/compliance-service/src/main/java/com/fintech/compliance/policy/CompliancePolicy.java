package com.fintech.compliance.policy;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Configurable compliance policy for dynamic rule enforcement
 */
@Entity
@Table(name = "compliance_policies")
public class CompliancePolicy {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PolicyType type;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ComplianceStandard standard;
    
    @Column(columnDefinition = "TEXT")
    private String ruleExpression; // JSON or rule engine expression
    
    @Column
    private String description;
    
    @Column(nullable = false)
    private boolean active = true;
    
    @Column
    private Instant createdAt;
    
    @Column
    private Instant updatedAt;
    
    @Column
    private String createdBy;
    
    public enum PolicyType {
        ACCESS_CONTROL,
        DATA_RETENTION,
        ENCRYPTION,
        AUDIT_LOGGING,
        TRANSACTION_LIMIT,
        FRAUD_DETECTION,
        IDENTITY_VERIFICATION
    }
    
    public enum ComplianceStandard {
        PCI_DSS,
        PSD2,
        GDPR,
        SOX,
        INTERNAL
    }
    
    // Constructors
    protected CompliancePolicy() {}
    
    public CompliancePolicy(String name, PolicyType type, ComplianceStandard standard, 
                           String ruleExpression, String description) {
        this.name = name;
        this.type = type;
        this.standard = standard;
        this.ruleExpression = ruleExpression;
        this.description = description;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    // Getters and setters
    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; this.updatedAt = Instant.now(); }
    
    public PolicyType getType() { return type; }
    public void setType(PolicyType type) { this.type = type; this.updatedAt = Instant.now(); }
    
    public ComplianceStandard getStandard() { return standard; }
    public void setStandard(ComplianceStandard standard) { this.standard = standard; this.updatedAt = Instant.now(); }
    
    public String getRuleExpression() { return ruleExpression; }
    public void setRuleExpression(String ruleExpression) { this.ruleExpression = ruleExpression; this.updatedAt = Instant.now(); }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; this.updatedAt = Instant.now(); }
    
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; this.updatedAt = Instant.now(); }
    
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
