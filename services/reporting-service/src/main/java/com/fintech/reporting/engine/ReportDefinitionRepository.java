package com.fintech.reporting.engine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.fintech.reporting.core.ReportDefinition;

import java.util.List;

@Repository
public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, String> {
    
    List<ReportDefinition> findByCategoryAndActiveTrue(ReportDefinition.ReportCategory category);
    
    List<ReportDefinition> findByActiveTrue();
    
    List<ReportDefinition> findByComplianceReportTrue();
    
    List<ReportDefinition> findByRequiredRole(String role);
    
    @Query("SELECT r FROM ReportDefinition r WHERE r.cronSchedule IS NOT NULL AND r.active = true")
    List<ReportDefinition> findScheduledReports();
    
    @Query("SELECT r FROM ReportDefinition r WHERE r.name LIKE %:searchTerm% OR r.displayName LIKE %:searchTerm%")
    List<ReportDefinition> searchByName(@Param("searchTerm") String searchTerm);
}
