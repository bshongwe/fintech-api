package com.fintech.compliance.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {
    
    List<AuditEvent> findByUserIdAndTimestampBetween(String userId, Instant start, Instant end);
    
    List<AuditEvent> findByEventTypeAndTimestampBetween(String eventType, Instant start, Instant end);
    
    List<AuditEvent> findByResourceIdAndResourceType(String resourceId, String resourceType);
    
    List<AuditEvent> findByLevelAndTimestampAfter(AuditEvent.AuditLevel level, Instant after);
    
    @Query("SELECT a FROM AuditEvent a WHERE a.complianceFlags LIKE %:flag% AND a.timestamp BETWEEN :start AND :end")
    List<AuditEvent> findByComplianceFlagAndTimeRange(@Param("flag") String flag, 
                                                     @Param("start") Instant start, 
                                                     @Param("end") Instant end);
    
    @Query("SELECT COUNT(a) FROM AuditEvent a WHERE a.userId = :userId AND a.eventType = :eventType AND a.timestamp > :since")
    long countUserEventsSince(@Param("userId") String userId, 
                             @Param("eventType") String eventType, 
                             @Param("since") Instant since);
}
