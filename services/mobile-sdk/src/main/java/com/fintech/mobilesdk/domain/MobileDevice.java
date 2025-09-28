package com.fintech.mobilesdk.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mobile Device Entity
 * 
 * Represents registered mobile devices with security attributes,
 * device fingerprinting, and session management capabilities.
 */
@Entity
@Table(name = "mobile_devices", indexes = {
    @Index(name = "idx_device_id", columnList = "device_id"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_device_status", columnList = "status"),
    @Index(name = "idx_device_type", columnList = "device_type"),
    @Index(name = "idx_last_activity", columnList = "last_activity_at")
})
@EntityListeners(AuditingEntityListener.class)
public class MobileDevice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "device_id", unique = true, nullable = false)
    @NotBlank(message = "Device ID is required")
    private String deviceId; // Unique device identifier
    
    @Column(name = "user_id", nullable = false)
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @Column(name = "device_name", length = 100)
    @Size(max = 100, message = "Device name must not exceed 100 characters")
    private String deviceName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false)
    @NotNull(message = "Device type is required")
    private DeviceType deviceType;
    
    @Column(name = "operating_system", length = 50)
    private String operatingSystem;
    
    @Column(name = "os_version", length = 20)
    private String osVersion;
    
    @Column(name = "app_version", length = 20)
    private String appVersion;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceStatus status = DeviceStatus.ACTIVE;
    
    @Column(name = "push_token")
    private String pushToken; // FCM/APNS token
    
    @Column(name = "device_fingerprint", columnDefinition = "TEXT")
    private String deviceFingerprint;
    
    @ElementCollection
    @CollectionTable(name = "device_metadata", joinColumns = @JoinColumn(name = "device_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private Map<String, String> metadata = new HashMap<>();
    
    @Column(name = "is_trusted")
    private Boolean isTrusted = false;
    
    @Column(name = "is_rooted_jailbroken")
    private Boolean isRootedOrJailbroken = false;
    
    @Column(name = "biometric_enabled")
    private Boolean biometricEnabled = false;
    
    @Column(name = "pin_enabled")
    private Boolean pinEnabled = false;
    
    @Column(name = "location_enabled")
    private Boolean locationEnabled = false;
    
    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;
    
    @Column(name = "last_ip_address", length = 45)
    private String lastIpAddress;
    
    @Column(name = "last_location")
    private String lastLocation;
    
    @Column(name = "risk_score", precision = 5, scale = 2)
    private Double riskScore = 0.0;
    
    @Column(name = "registration_ip", length = 45)
    private String registrationIp;
    
    @Column(name = "registration_location")
    private String registrationLocation;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Constructors
    public MobileDevice() {}
    
    public MobileDevice(String deviceId, UUID userId, DeviceType deviceType, String deviceName) {
        this.deviceId = deviceId;
        this.userId = userId;
        this.deviceType = deviceType;
        this.deviceName = deviceName;
    }
    
    // Business Methods
    public boolean isActive() {
        return status == DeviceStatus.ACTIVE;
    }
    
    public boolean isSuspicious() {
        return riskScore != null && riskScore > 7.0;
    }
    
    public boolean isHighRisk() {
        return riskScore != null && riskScore > 8.5;
    }
    
    public void updateActivity(String ipAddress, String location) {
        this.lastActivityAt = LocalDateTime.now();
        this.lastIpAddress = ipAddress;
        this.lastLocation = location;
    }
    
    public void updateRiskScore(double newRiskScore) {
        this.riskScore = newRiskScore;
        
        // Auto-suspend high-risk devices
        if (newRiskScore > 9.0) {
            this.status = DeviceStatus.SUSPENDED;
        }
    }
    
    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
    }
    
    public String getMetadata(String key) {
        return this.metadata.get(key);
    }
    
    public boolean hasSecurityFeatures() {
        return Boolean.TRUE.equals(biometricEnabled) || Boolean.TRUE.equals(pinEnabled);
    }
    
    public boolean isSecure() {
        return hasSecurityFeatures() && 
               Boolean.FALSE.equals(isRootedOrJailbroken) && 
               Boolean.TRUE.equals(isTrusted);
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    
    public DeviceType getDeviceType() { return deviceType; }
    public void setDeviceType(DeviceType deviceType) { this.deviceType = deviceType; }
    
    public String getOperatingSystem() { return operatingSystem; }
    public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }
    
    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }
    
    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
    
    public DeviceStatus getStatus() { return status; }
    public void setStatus(DeviceStatus status) { this.status = status; }
    
    public String getPushToken() { return pushToken; }
    public void setPushToken(String pushToken) { this.pushToken = pushToken; }
    
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
    
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    
    public Boolean getIsTrusted() { return isTrusted; }
    public void setIsTrusted(Boolean isTrusted) { this.isTrusted = isTrusted; }
    
    public Boolean getIsRootedOrJailbroken() { return isRootedOrJailbroken; }
    public void setIsRootedOrJailbroken(Boolean isRootedOrJailbroken) { this.isRootedOrJailbroken = isRootedOrJailbroken; }
    
    public Boolean getBiometricEnabled() { return biometricEnabled; }
    public void setBiometricEnabled(Boolean biometricEnabled) { this.biometricEnabled = biometricEnabled; }
    
    public Boolean getPinEnabled() { return pinEnabled; }
    public void setPinEnabled(Boolean pinEnabled) { this.pinEnabled = pinEnabled; }
    
    public Boolean getLocationEnabled() { return locationEnabled; }
    public void setLocationEnabled(Boolean locationEnabled) { this.locationEnabled = locationEnabled; }
    
    public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }
    
    public String getLastIpAddress() { return lastIpAddress; }
    public void setLastIpAddress(String lastIpAddress) { this.lastIpAddress = lastIpAddress; }
    
    public String getLastLocation() { return lastLocation; }
    public void setLastLocation(String lastLocation) { this.lastLocation = lastLocation; }
    
    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }
    
    public String getRegistrationIp() { return registrationIp; }
    public void setRegistrationIp(String registrationIp) { this.registrationIp = registrationIp; }
    
    public String getRegistrationLocation() { return registrationLocation; }
    public void setRegistrationLocation(String registrationLocation) { this.registrationLocation = registrationLocation; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

enum DeviceType {
    IOS,
    ANDROID,
    WEB,
    TABLET,
    DESKTOP
}

enum DeviceStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    BLOCKED,
    PENDING_VERIFICATION
}
