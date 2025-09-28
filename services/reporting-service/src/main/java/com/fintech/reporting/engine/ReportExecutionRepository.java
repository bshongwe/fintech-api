package com.fintech.reporting.engine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.fintech.reporting.core.ReportExecution;

import java.time.Instant;
import java.util.List;

@Repository
public interface ReportExecutionRepository extends JpaRepository<ReportExecution, String> {
    
    List<ReportExecution> findByDefinitionIdAndStatusOrderByRequestedAtDesc(String definitionId, 
                                                                           ReportExecution.ExecutionStatus status);
    
    List<ReportExecution> findByRequestedByAndRequestedAtBetween(String requestedBy, Instant start, Instant end);
    
    @Query("SELECT r FROM ReportExecution r WHERE r.status = 'RUNNING' AND r.startedAt < :threshold")
    List<ReportExecution> findLongRunningExecutions(@Param("threshold") Instant threshold);
    
    @Query("SELECT r FROM ReportExecution r WHERE r.expiresAt < :now AND r.status = 'COMPLETED'")
    List<ReportExecution> findExpiredExecutions(@Param("now") Instant now);
    
    @Query("SELECT COUNT(r) FROM ReportExecution r WHERE r.requestedBy = :user AND r.requestedAt > :since")
    long countUserExecutionsSince(@Param("user") String user, @Param("since") Instant since);
    
    @Query("SELECT r FROM ReportExecution r WHERE r.definitionId = :definitionId AND r.parameters = :parameters AND r.status = 'COMPLETED' AND r.expiresAt > :now ORDER BY r.completedAt DESC")
    List<ReportExecution> findCachedExecutions(@Param("definitionId") String definitionId,
                                             @Param("parameters") String parameters,
                                             @Param("now") Instant now);
}
