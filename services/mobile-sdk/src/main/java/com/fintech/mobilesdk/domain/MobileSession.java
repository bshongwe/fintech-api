package com.fintech.mobilesdk.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mobile Session Entity
 * 
 * Represents active mobile application sessions with security tracking,
 * device binding, and activity monitoring.
 */
@Entity
@Table(name = "mobile_sessions", indexes = {
    @Index(name = "idx_session_token", columnList = "session_token"),
    @Index(name = "idx_device_id", columnList = "device_id"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_session_status", columnList = "status"),
    @Index(name = "idx_expires_at", columnList = "expires_at"),
    @Index(name = "idx_last_activity", columnList = "last_activity_at")
})
@EntityListeners(AuditingEntityListener.class)
public class MobileSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "session_token", unique = true, nullable = false)
    @NotBlank(message = "Session token is required")
    private String sessionToken;
    
    @Column(name = "refresh_token")
    private String refreshToken;
    
    @Column(name = "user_id", nullable = false)
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @Column(name = "device_id", nullable = false)
    @NotNull(message = "Device ID is required")
    private UUID deviceId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.ACTIVE;
    
    @Column(name = "expires_at", nullable = false)
    @NotNull(message = "Expiration time is required")
    private LocalDateTime expiresAt;
    
    @Column(name = "refresh_expires_at")
    private LocalDateTime refreshExpiresAt;
    
    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    @Column(name = "location")
    private String location;
    
    @ElementCollection
    @CollectionTable(name = "session_metadata", joinColumns = @JoinColumn(name = "session_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private Map<String, String> metadata = new HashMap<>();
    
    @Column(name = "login_method", length = 50)
    private String loginMethod; // PASSWORD, BIOMETRIC, PIN, SSO
    
    @Column(name = "security_level")
    private Integer securityLevel = 1; // 1=Basic, 2=Enhanced, 3=High
    
    @Column(name = "mfa_verified")
    private Boolean mfaVerified = false;
    
    @Column(name = "biometric_verified")
    private Boolean biometricVerified = false;
    
    @Column(name = "risk_score", precision = 5, scale = 2)
    private Double riskScore = 0.0;
    
    @Column(name = "activity_count")
    private Long activityCount = 0L;
    
    @Column(name = "terminated_reason")
    private String terminatedReason;
    
    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public MobileSession() {}
    
    public MobileSession(String sessionToken, UUID userId, UUID deviceId, LocalDateTime expiresAt) {
        this.sessionToken = sessionToken;
        this.userId = userId;
        this.deviceId = deviceId;
        this.expiresAt = expiresAt;
        this.lastActivityAt = LocalDateTime.now();
    }
    
    // Business Methods
    public boolean isActive() {
        return status == SessionStatus.ACTIVE && 
               expiresAt.isAfter(LocalDateTime.now());
    }
    
    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
    
    public boolean canRefresh() {
        return refreshToken != null && 
               refreshExpiresAt != null && 
               refreshExpiresAt.isAfter(LocalDateTime.now()) &&
               status == SessionStatus.ACTIVE;
    }
    
    public void updateActivity(String ipAddress, String location) {
        this.lastActivityAt = LocalDateTime.now();
        this.ipAddress = ipAddress;
        this.location = location;
        this.activityCount++;
    }
    
    public void terminate(String reason) {
        this.status = SessionStatus.TERMINATED;
        this.terminatedReason = reason;
        this.terminatedAt = LocalDateTime.now();
    }
    
    public void suspend(String reason) {
        this.status = SessionStatus.SUSPENDED;
        this.terminatedReason = reason;
    }
    
    public void extend(LocalDateTime newExpiresAt) {
        if (isActive()) {
            this.expiresAt = newExpiresAt;
        }
    }
    
    public void updateRiskScore(double newRiskScore) {
        this.riskScore = newRiskScore;
        
        // Auto-suspend high-risk sessions
        if (newRiskScore > 8.5) {
            suspend("High risk score detected: " + newRiskScore);
        }
    }
    
    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
    }
    
    public String getMetadata(String key) {
        return this.metadata.get(key);
    }
    
    public boolean isHighSecurity() {
        return securityLevel != null && securityLevel >= 3;
    }
    
    public boolean isFullyAuthenticated() {
        return Boolean.TRUE.equals(mfaVerified) && 
               (Boolean.TRUE.equals(biometricVerified) || securityLevel >= 2);
    }
    
    public long getSessionDurationMinutes() {
        LocalDateTime endTime = terminatedAt != null ? terminatedAt : LocalDateTime.now();
        return java.time.Duration.between(createdAt, endTime).toMinutes();
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
    
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    
    public LocalDateTime getRefreshExpiresAt() { return refreshExpiresAt; }
    public void setRefreshExpiresAt(LocalDateTime refreshExpiresAt) { this.refreshExpiresAt = refreshExpiresAt; }
    
    public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    
    public String getLoginMethod() { return loginMethod; }
    public void setLoginMethod(String loginMethod) { this.loginMethod = loginMethod; }
    
    public Integer getSecurityLevel() { return securityLevel; }
    public void setSecurityLevel(Integer securityLevel) { this.securityLevel = securityLevel; }
    
    public Boolean getMfaVerified() { return mfaVerified; }
    public void setMfaVerified(Boolean mfaVerified) { this.mfaVerified = mfaVerified; }
    
    public Boolean getBiometricVerified() { return biometricVerified; }
    public void setBiometricVerified(Boolean biometricVerified) { this.biometricVerified = biometricVerified; }
    
    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }
    
    public Long getActivityCount() { return activityCount; }
    public void setActivityCount(Long activityCount) { this.activityCount = activityCount; }
    
    public String getTerminatedReason() { return terminatedReason; }
    public void setTerminatedReason(String terminatedReason) { this.terminatedReason = terminatedReason; }
    
    public LocalDateTime getTerminatedAt() { return terminatedAt; }
    public void setTerminatedAt(LocalDateTime terminatedAt) { this.terminatedAt = terminatedAt; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

enum SessionStatus {
    ACTIVE,
    EXPIRED,
    TERMINATED,
    SUSPENDED
}
