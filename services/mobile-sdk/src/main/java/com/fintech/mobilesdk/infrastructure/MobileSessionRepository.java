package com.fintech.mobilesdk.infrastructure;

import com.fintech.mobilesdk.domain.MobileSession;
import com.fintech.mobilesdk.domain.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mobile Session Repository
 * 
 * Provides data access operations for mobile session management and tracking.
 */
@Repository
public interface MobileSessionRepository extends JpaRepository<MobileSession, UUID> {
    
    /**
     * Find session by token
     */
    Optional<MobileSession> findBySessionToken(String sessionToken);
    
    /**
     * Find session by refresh token
     */
    Optional<MobileSession> findByRefreshToken(String refreshToken);
    
    /**
     * Find active sessions for user
     */
    @Query("SELECT s FROM MobileSession s WHERE s.userId = :userId AND s.status = 'ACTIVE' AND s.expiresAt > :currentTime")
    List<MobileSession> findActiveSessionsByUser(@Param("userId") UUID userId, @Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Find active sessions for device
     */
    @Query("SELECT s FROM MobileSession s WHERE s.deviceId = :deviceId AND s.status = 'ACTIVE' AND s.expiresAt > :currentTime")
    List<MobileSession> findActiveSessionsByDevice(@Param("deviceId") UUID deviceId, @Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Find sessions by user with pagination
     */
    Page<MobileSession> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    /**
     * Find sessions by device with pagination
     */
    Page<MobileSession> findByDeviceIdOrderByCreatedAtDesc(UUID deviceId, Pageable pageable);
    
    /**
     * Find sessions by status
     */
    List<MobileSession> findByStatus(SessionStatus status);
    
    /**
     * Find expired sessions
     */
    @Query("SELECT s FROM MobileSession s WHERE s.expiresAt < :currentTime AND s.status = 'ACTIVE'")
    List<MobileSession> findExpiredSessions(@Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Find sessions expiring soon
     */
    @Query("SELECT s FROM MobileSession s WHERE s.expiresAt BETWEEN :currentTime AND :warningTime AND s.status = 'ACTIVE'")
    List<MobileSession> findSessionsExpiringSoon(@Param("currentTime") LocalDateTime currentTime, 
                                               @Param("warningTime") LocalDateTime warningTime);
    
    /**
     * Find high-risk sessions
     */
    @Query("SELECT s FROM MobileSession s WHERE s.riskScore >= :threshold AND s.status = 'ACTIVE'")
    List<MobileSession> findHighRiskSessions(@Param("threshold") double threshold);
    
    /**
     * Find sessions by IP address
     */
    List<MobileSession> findByIpAddress(String ipAddress);
    
    /**
     * Find concurrent sessions from different locations
     */
    @Query("SELECT s FROM MobileSession s WHERE s.userId = :userId AND s.status = 'ACTIVE' " +
           "AND s.location != :currentLocation AND s.lastActivityAt > :recentThreshold")
    List<MobileSession> findConcurrentSessionsFromDifferentLocations(
        @Param("userId") UUID userId, 
        @Param("currentLocation") String currentLocation,
        @Param("recentThreshold") LocalDateTime recentThreshold
    );
    
    /**
     * Find sessions by login method
     */
    List<MobileSession> findByLoginMethod(String loginMethod);
    
    /**
     * Find long-running sessions
     */
    @Query("SELECT s FROM MobileSession s WHERE s.createdAt < :threshold AND s.status = 'ACTIVE'")
    List<MobileSession> findLongRunningSessions(@Param("threshold") LocalDateTime threshold);
    
    /**
     * Count active sessions by user
     */
    @Query("SELECT COUNT(s) FROM MobileSession s WHERE s.userId = :userId AND s.status = 'ACTIVE' AND s.expiresAt > :currentTime")
    long countActiveSessionsByUser(@Param("userId") UUID userId, @Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Count sessions by status
     */
    long countByStatus(SessionStatus status);
    
    /**
     * Count sessions created in time period
     */
    @Query("SELECT COUNT(s) FROM MobileSession s WHERE s.createdAt BETWEEN :startDate AND :endDate")
    long countSessionsCreatedBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get session statistics by login method
     */
    @Query("SELECT s.loginMethod, COUNT(s), AVG(s.activityCount) FROM MobileSession s " +
           "WHERE s.createdAt >= :since GROUP BY s.loginMethod")
    List<Object[]> getSessionStatsByLoginMethod(@Param("since") LocalDateTime since);
    
    /**
     * Get average session duration by security level
     */
    @Query("SELECT s.securityLevel, AVG(TIMESTAMPDIFF(MINUTE, s.createdAt, COALESCE(s.terminatedAt, :currentTime))) " +
           "FROM MobileSession s WHERE s.createdAt >= :since GROUP BY s.securityLevel")
    List<Object[]> getAverageSessionDurationBySecurityLevel(@Param("since") LocalDateTime since, 
                                                           @Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Update session activity
     */
    @Modifying
    @Query("UPDATE MobileSession s SET s.lastActivityAt = :activityTime, s.ipAddress = :ipAddress, " +
           "s.location = :location, s.activityCount = s.activityCount + 1 WHERE s.id = :sessionId")
    void updateSessionActivity(@Param("sessionId") UUID sessionId,
                              @Param("activityTime") LocalDateTime activityTime,
                              @Param("ipAddress") String ipAddress,
                              @Param("location") String location);
    
    /**
     * Update session risk score
     */
    @Modifying
    @Query("UPDATE MobileSession s SET s.riskScore = :riskScore WHERE s.id = :sessionId")
    void updateRiskScore(@Param("sessionId") UUID sessionId, @Param("riskScore") double riskScore);
    
    /**
     * Terminate session
     */
    @Modifying
    @Query("UPDATE MobileSession s SET s.status = 'TERMINATED', s.terminatedReason = :reason, " +
           "s.terminatedAt = :terminatedAt WHERE s.id = :sessionId")
    void terminateSession(@Param("sessionId") UUID sessionId, 
                         @Param("reason") String reason,
                         @Param("terminatedAt") LocalDateTime terminatedAt);
    
    /**
     * Bulk terminate sessions for user
     */
    @Modifying
    @Query("UPDATE MobileSession s SET s.status = 'TERMINATED', s.terminatedReason = :reason, " +
           "s.terminatedAt = :terminatedAt WHERE s.userId = :userId AND s.status = 'ACTIVE'")
    void terminateAllUserSessions(@Param("userId") UUID userId,
                                 @Param("reason") String reason,
                                 @Param("terminatedAt") LocalDateTime terminatedAt);
    
    /**
     * Bulk terminate sessions for device
     */
    @Modifying
    @Query("UPDATE MobileSession s SET s.status = 'TERMINATED', s.terminatedReason = :reason, " +
           "s.terminatedAt = :terminatedAt WHERE s.deviceId = :deviceId AND s.status = 'ACTIVE'")
    void terminateAllDeviceSessions(@Param("deviceId") UUID deviceId,
                                   @Param("reason") String reason,
                                   @Param("terminatedAt") LocalDateTime terminatedAt);
    
    /**
     * Mark expired sessions
     */
    @Modifying
    @Query("UPDATE MobileSession s SET s.status = 'EXPIRED' WHERE s.expiresAt < :currentTime AND s.status = 'ACTIVE'")
    int markExpiredSessions(@Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Extend session expiration
     */
    @Modifying
    @Query("UPDATE MobileSession s SET s.expiresAt = :newExpiresAt WHERE s.id = :sessionId AND s.status = 'ACTIVE'")
    void extendSession(@Param("sessionId") UUID sessionId, @Param("newExpiresAt") LocalDateTime newExpiresAt);
    
    /**
     * Update MFA verification status
     */
    @Modifying
    @Query("UPDATE MobileSession s SET s.mfaVerified = :verified, s.securityLevel = :securityLevel WHERE s.id = :sessionId")
    void updateMfaVerification(@Param("sessionId") UUID sessionId, 
                              @Param("verified") boolean verified,
                              @Param("securityLevel") int securityLevel);
    
    /**
     * Update biometric verification status
     */
    @Modifying
    @Query("UPDATE MobileSession s SET s.biometricVerified = :verified WHERE s.id = :sessionId")
    void updateBiometricVerification(@Param("sessionId") UUID sessionId, @Param("verified") boolean verified);
    
    /**
     * Clean up old terminated sessions
     */
    @Modifying
    @Query("DELETE FROM MobileSession s WHERE s.status IN ('TERMINATED', 'EXPIRED') AND s.terminatedAt < :cutoffDate")
    int cleanupOldSessions(@Param("cutoffDate") LocalDateTime cutoffDate);
}
