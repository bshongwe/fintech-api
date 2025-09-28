package com.fintech.compliance.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompliancePolicyRepository extends JpaRepository<CompliancePolicy, String> {
    
    List<CompliancePolicy> findByTypeAndActive(CompliancePolicy.PolicyType type, boolean active);
    
    List<CompliancePolicy> findByStandardAndActive(CompliancePolicy.ComplianceStandard standard, boolean active);
    
    List<CompliancePolicy> findByActiveTrue();
    
    List<CompliancePolicy> findByNameContainingIgnoreCase(String name);
}
