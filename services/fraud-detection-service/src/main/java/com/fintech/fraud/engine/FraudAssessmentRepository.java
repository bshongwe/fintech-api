package com.fintech.fraud.engine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.fintech.fraud.core.FraudAssessment;

import java.time.Instant;
import java.util.List;

@Repository
public interface FraudAssessmentRepository extends JpaRepository<FraudAssessment, String> {
    
    FraudAssessment findByTransactionId(String transactionId);
    
    List<FraudAssessment> findByUserIdAndAssessedAtBetween(String userId, Instant start, Instant end);
    
    List<FraudAssessment> findByStatusAndAssessedAtAfter(FraudAssessment.FraudStatus status, Instant after);
    
    List<FraudAssessment> findByRiskLevelAndStatusIn(FraudAssessment.RiskLevel riskLevel, 
                                                    List<FraudAssessment.FraudStatus> statuses);
    
    @Query("SELECT COUNT(f) FROM FraudAssessment f WHERE f.userId = :userId AND f.assessedAt > :since")
    long countUserAssessmentsSince(@Param("userId") String userId, @Param("since") Instant since);
    
    @Query("SELECT f FROM FraudAssessment f WHERE f.status = 'REVIEWING' ORDER BY f.riskScore DESC")
    List<FraudAssessment> findPendingReviews();
    
    @Query("SELECT f FROM FraudAssessment f WHERE f.bankConnectorUsed = :connector AND f.bankValidationPassed = false")
    List<FraudAssessment> findFailedBankValidations(@Param("connector") String connector);
}
