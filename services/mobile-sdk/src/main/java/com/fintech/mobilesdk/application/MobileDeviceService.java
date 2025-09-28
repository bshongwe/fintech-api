package com.fintech.mobilesdk.application;

import com.fintech.mobilesdk.domain.MobileDevice;
import com.fintech.mobilesdk.domain.MobileSession;
import com.fintech.mobilesdk.domain.DeviceStatus;
import com.fintech.mobilesdk.infrastructure.MobileDeviceRepository;
import com.fintech.mobilesdk.infrastructure.MobileSessionRepository;
import com.fintech.commons.ApiResponse;
import com.fintech.commons.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mobile Device Management Service
 * 
 * Handles device registration, management, security monitoring,
 * and device-based operations for mobile applications.
 */
@Service
@Validated
@Transactional
public class MobileDeviceService {
    
    private static final Logger logger = LoggerFactory.getLogger(MobileDeviceService.class);
    
    private final MobileDeviceRepository mobileDeviceRepository;
    private final MobileSessionRepository mobileSessionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${app.mobile.max-devices-per-user:5}")
    private int maxDevicesPerUser;
    
    @Value("${app.mobile.device-cleanup-days:90}")
    private int deviceCleanupDays;
    
    @Value("${app.mobile.risk-threshold:7.0}")
    private double riskThreshold;
    
    @Autowired
    public MobileDeviceService(MobileDeviceRepository mobileDeviceRepository,
                             MobileSessionRepository mobileSessionRepository,
                             KafkaTemplate<String, Object> kafkaTemplate) {
        this.mobileDeviceRepository = mobileDeviceRepository;
        this.mobileSessionRepository = mobileSessionRepository;
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Get device information
     */
    @Cacheable(value = "deviceInfo", key = "#deviceId")
    public Optional<DeviceInfo> getDeviceInfo(@NotBlank String deviceId) {
        return mobileDeviceRepository.findByDeviceId(deviceId)
            .map(device -> new DeviceInfo(
                device.getId(),
                device.getDeviceId(),
                device.getUserId(),
                device.getDeviceType(),
                device.getDeviceName(),
                device.getOperatingSystem(),
                device.getOsVersion(),
                device.getAppVersion(),
                device.getStatus(),
                device.getIsTrusted(),
                device.getRiskScore(),
                device.getRegisteredAt(),
                device.getLastActivityAt(),
                device.getLastLocation(),
                device.hasSecurityFeatures(),
                device.getIsRootedOrJailbroken()
            ));
    }
    
    /**
     * Get all devices for a user
     */
    public List<DeviceInfo> getUserDevices(@NotNull UUID userId) {
        List<MobileDevice> devices = mobileDeviceRepository.findByUserId(userId);
        
        return devices.stream()
            .map(device -> new DeviceInfo(
                device.getId(),
                device.getDeviceId(),
                device.getUserId(),
                device.getDeviceType(),
                device.getDeviceName(),
                device.getOperatingSystem(),
                device.getOsVersion(),
                device.getAppVersion(),
                device.getStatus(),
                device.getIsTrusted(),
                device.getRiskScore(),
                device.getRegisteredAt(),
                device.getLastActivityAt(),
                device.getLastLocation(),
                device.hasSecurityFeatures(),
                device.getIsRootedOrJailbroken()
            ))
            .collect(Collectors.toList());
    }
    
    /**
     * Update device information
     */
    @CacheEvict(value = "deviceInfo", key = "#deviceId")
    public ApiResponse<DeviceInfo> updateDevice(@NotBlank String deviceId, DeviceUpdateRequest request) {
        try {
            Optional<MobileDevice> optionalDevice = mobileDeviceRepository.findByDeviceId(deviceId);
            
            if (optionalDevice.isEmpty()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Device not found")
                    .build());
            }
            
            MobileDevice device = optionalDevice.get();
            
            // Update device information
            if (request.getDeviceName() != null) {
                device.setDeviceName(request.getDeviceName());
            }
            
            if (request.getOsVersion() != null) {
                device.setOsVersion(request.getOsVersion());
            }
            
            if (request.getAppVersion() != null) {
                device.setAppVersion(request.getAppVersion());
            }
            
            if (request.getPushToken() != null) {
                device.setPushToken(request.getPushToken());
            }
            
            if (request.getDeviceFingerprint() != null) {
                device.setDeviceFingerprint(request.getDeviceFingerprint());
            }
            
            // Update security settings
            if (request.getBiometricEnabled() != null) {
                device.setBiometricEnabled(request.getBiometricEnabled());
            }
            
            if (request.getPinEnabled() != null) {
                device.setPinEnabled(request.getPinEnabled());
            }
            
            if (request.getLocationEnabled() != null) {
                device.setLocationEnabled(request.getLocationEnabled());
            }
            
            if (request.getPushEnabled() != null) {
                device.setPushEnabled(request.getPushEnabled());
            }
            
            // Update activity tracking
            device.updateActivity(request.getIpAddress(), request.getLocation());
            
            // Recalculate risk score if security settings changed
            if (hasSecuritySettingsChanged(request)) {
                double newRiskScore = recalculateRiskScore(device);
                device.setRiskScore(newRiskScore);
                device.setIsTrusted(newRiskScore < riskThreshold);
            }
            
            MobileDevice updatedDevice = mobileDeviceRepository.save(device);
            
            // Publish device update event
            publishDeviceEvent("DEVICE_UPDATED", updatedDevice);
            
            logger.info("Device updated: {}", deviceId);
            
            DeviceInfo deviceInfo = new DeviceInfo(
                updatedDevice.getId(),
                updatedDevice.getDeviceId(),
                updatedDevice.getUserId(),
                updatedDevice.getDeviceType(),
                updatedDevice.getDeviceName(),
                updatedDevice.getOperatingSystem(),
                updatedDevice.getOsVersion(),
                updatedDevice.getAppVersion(),
                updatedDevice.getStatus(),
                updatedDevice.getIsTrusted(),
                updatedDevice.getRiskScore(),
                updatedDevice.getRegisteredAt(),
                updatedDevice.getLastActivityAt(),
                updatedDevice.getLastLocation(),
                updatedDevice.hasSecurityFeatures(),
                updatedDevice.getIsRootedOrJailbroken()
            );
            
            return ApiResponse.success(deviceInfo);
            
        } catch (Exception e) {
            logger.error("Error updating device", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to update device")
                .build());
        }
    }
    
    /**
     * Trust or untrust a device
     */
    @CacheEvict(value = "deviceInfo", key = "#deviceId")
    public ApiResponse<Void> setDeviceTrust(@NotBlank String deviceId, boolean trusted, String reason) {
        try {
            Optional<MobileDevice> optionalDevice = mobileDeviceRepository.findByDeviceId(deviceId);
            
            if (optionalDevice.isEmpty()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Device not found")
                    .build());
            }
            
            MobileDevice device = optionalDevice.get();
            boolean previousTrustStatus = device.getIsTrusted();
            
            device.setIsTrusted(trusted);
            
            // Adjust risk score based on trust status
            if (trusted && device.getRiskScore() > 5.0) {
                device.setRiskScore(Math.max(device.getRiskScore() - 2.0, 1.0));
            } else if (!trusted && device.getRiskScore() < 5.0) {
                device.setRiskScore(Math.min(device.getRiskScore() + 3.0, 10.0));
            }
            
            // If device is being untrusted, terminate all active sessions
            if (previousTrustStatus && !trusted) {
                mobileSessionRepository.terminateDeviceSessions(
                    device.getId(), 
                    "Device trust revoked: " + (reason != null ? reason : "Security action"),
                    LocalDateTime.now()
                );
            }
            
            mobileDeviceRepository.save(device);
            
            // Publish trust change event
            publishTrustEvent(trusted ? "DEVICE_TRUSTED" : "DEVICE_UNTRUSTED", device, reason);
            
            logger.info("Device trust updated: {} - {}", deviceId, trusted ? "trusted" : "untrusted");
            
            return ApiResponse.success(null);
            
        } catch (Exception e) {
            logger.error("Error updating device trust", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to update device trust")
                .build());
        }
    }
    
    /**
     * Block or unblock a device
     */
    @CacheEvict(value = "deviceInfo", key = "#deviceId")
    public ApiResponse<Void> setDeviceStatus(@NotBlank String deviceId, DeviceStatus status, String reason) {
        try {
            Optional<MobileDevice> optionalDevice = mobileDeviceRepository.findByDeviceId(deviceId);
            
            if (optionalDevice.isEmpty()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Device not found")
                    .build());
            }
            
            MobileDevice device = optionalDevice.get();
            DeviceStatus previousStatus = device.getStatus();
            
            device.setStatus(status);
            
            // If device is being blocked, terminate all active sessions
            if (status == DeviceStatus.BLOCKED) {
                mobileSessionRepository.terminateDeviceSessions(
                    device.getId(),
                    "Device blocked: " + (reason != null ? reason : "Security action"),
                    LocalDateTime.now()
                );
                
                // Increase risk score for blocked devices
                device.setRiskScore(10.0);
                device.setIsTrusted(false);
            }
            
            mobileDeviceRepository.save(device);
            
            // Publish status change event
            publishStatusEvent("DEVICE_STATUS_CHANGED", device, previousStatus, status, reason);
            
            logger.info("Device status updated: {} - {} -> {}", deviceId, previousStatus, status);
            
            return ApiResponse.success(null);
            
        } catch (Exception e) {
            logger.error("Error updating device status", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to update device status")
                .build());
        }
    }
    
    /**
     * Remove device from user account
     */
    @CacheEvict(value = "deviceInfo", key = "#deviceId")
    public ApiResponse<Void> removeDevice(@NotBlank String deviceId, @NotNull UUID userId) {
        try {
            Optional<MobileDevice> optionalDevice = mobileDeviceRepository.findByDeviceId(deviceId);
            
            if (optionalDevice.isEmpty()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Device not found")
                    .build());
            }
            
            MobileDevice device = optionalDevice.get();
            
            // Verify device belongs to user
            if (!device.getUserId().equals(userId)) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Device does not belong to user")
                    .build());
            }
            
            // Terminate all active sessions
            mobileSessionRepository.terminateDeviceSessions(
                device.getId(),
                "Device removed from account",
                LocalDateTime.now()
            );
            
            // Set device status to removed instead of deleting
            device.setStatus(DeviceStatus.REMOVED);
            device.setIsTrusted(false);
            device.setPushToken(null);
            device.setPushEnabled(false);
            
            mobileDeviceRepository.save(device);
            
            // Publish device removal event
            publishDeviceEvent("DEVICE_REMOVED", device);
            
            logger.info("Device removed: {} for user: {}", deviceId, userId);
            
            return ApiResponse.success(null);
            
        } catch (Exception e) {
            logger.error("Error removing device", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to remove device")
                .build());
        }
    }
    
    /**
     * Get device security report
     */
    public DeviceSecurityReport getDeviceSecurityReport(@NotBlank String deviceId) {
        Optional<MobileDevice> optionalDevice = mobileDeviceRepository.findByDeviceId(deviceId);
        
        if (optionalDevice.isEmpty()) {
            return null;
        }
        
        MobileDevice device = optionalDevice.get();
        
        // Get recent sessions for analysis
        List<MobileSession> recentSessions = mobileSessionRepository.findDeviceSessionsInPeriod(
            device.getId(), 
            LocalDateTime.now().minusDays(30), 
            LocalDateTime.now()
        );
        
        // Calculate security metrics
        long totalSessions = recentSessions.size();
        long suspiciousSessions = recentSessions.stream()
            .mapToLong(session -> session.getRiskScore() > 5.0 ? 1 : 0)
            .sum();
        
        Set<String> uniqueLocations = recentSessions.stream()
            .map(MobileSession::getLocation)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        
        Set<String> uniqueIpAddresses = recentSessions.stream()
            .map(MobileSession::getIpAddress)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        
        LocalDateTime lastCompromisedActivity = recentSessions.stream()
            .filter(session -> session.getRiskScore() > 8.0)
            .map(MobileSession::getCreatedAt)
            .max(LocalDateTime::compareTo)
            .orElse(null);
        
        // Security recommendations
        List<String> recommendations = new ArrayList<>();
        
        if (!device.hasSecurityFeatures()) {
            recommendations.add("Enable biometric authentication or PIN");
        }
        
        if (Boolean.TRUE.equals(device.getIsRootedOrJailbroken())) {
            recommendations.add("Device is rooted/jailbroken - consider using a secure device");
        }
        
        if (device.getRiskScore() > riskThreshold) {
            recommendations.add("High risk device - review recent activity");
        }
        
        if (uniqueLocations.size() > 5) {
            recommendations.add("Multiple access locations detected - verify all sessions");
        }
        
        if (suspiciousSessions > totalSessions * 0.3) {
            recommendations.add("High percentage of suspicious sessions - review security settings");
        }
        
        return new DeviceSecurityReport(
            device.getId(),
            device.getDeviceId(),
            device.getRiskScore(),
            device.getIsTrusted(),
            device.hasSecurityFeatures(),
            device.getIsRootedOrJailbroken(),
            totalSessions,
            suspiciousSessions,
            uniqueLocations.size(),
            uniqueIpAddresses.size(),
            lastCompromisedActivity,
            recommendations
        );
    }
    
    /**
     * Get device usage analytics
     */
    public DeviceAnalytics getDeviceAnalytics(@NotNull UUID userId, LocalDateTime fromDate, LocalDateTime toDate) {
        List<MobileDevice> userDevices = mobileDeviceRepository.findByUserId(userId);
        
        Map<String, Long> deviceTypeBreakdown = userDevices.stream()
            .collect(Collectors.groupingBy(
                device -> device.getDeviceType().toString(),
                Collectors.counting()
            ));
        
        Map<String, Long> statusBreakdown = userDevices.stream()
            .collect(Collectors.groupingBy(
                device -> device.getStatus().toString(),
                Collectors.counting()
            ));
        
        long trustedDevices = userDevices.stream()
            .mapToLong(device -> Boolean.TRUE.equals(device.getIsTrusted()) ? 1 : 0)
            .sum();
        
        long highRiskDevices = userDevices.stream()
            .mapToLong(device -> device.getRiskScore() > riskThreshold ? 1 : 0)
            .sum();
        
        long devicesWithSecurity = userDevices.stream()
            .mapToLong(device -> device.hasSecurityFeatures() ? 1 : 0)
            .sum();
        
        OptionalDouble averageRiskScore = userDevices.stream()
            .mapToDouble(MobileDevice::getRiskScore)
            .average();
        
        return new DeviceAnalytics(
            userDevices.size(),
            trustedDevices,
            highRiskDevices,
            devicesWithSecurity,
            averageRiskScore.orElse(0.0),
            deviceTypeBreakdown,
            statusBreakdown
        );
    }
    
    /**
     * Cleanup old inactive devices
     */
    @Transactional
    public void cleanupInactiveDevices() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(deviceCleanupDays);
        
        List<MobileDevice> inactiveDevices = mobileDeviceRepository.findInactiveDevices(cutoffDate);
        
        for (MobileDevice device : inactiveDevices) {
            // Terminate any remaining sessions
            mobileSessionRepository.terminateDeviceSessions(
                device.getId(),
                "Device cleanup - inactive for " + deviceCleanupDays + " days",
                LocalDateTime.now()
            );
            
            // Mark device as inactive
            device.setStatus(DeviceStatus.INACTIVE);
            device.setIsTrusted(false);
            device.setPushToken(null);
            device.setPushEnabled(false);
            
            mobileDeviceRepository.save(device);
            
            // Publish cleanup event
            publishDeviceEvent("DEVICE_CLEANUP", device);
        }
        
        logger.info("Cleaned up {} inactive devices", inactiveDevices.size());
    }
    
    private double recalculateRiskScore(MobileDevice device) {
        double score = 1.0; // Base score
        
        // Security features
        if (!device.hasSecurityFeatures()) {
            score += 2.0;
        }
        
        // Rooted/jailbroken
        if (Boolean.TRUE.equals(device.getIsRootedOrJailbroken())) {
            score += 4.0;
        }
        
        // OS version
        if (isOldOsVersion(device.getOsVersion())) {
            score += 1.5;
        }
        
        // Activity patterns (could be enhanced with ML)
        // For now, just basic checks
        
        return Math.min(score, 10.0);
    }
    
    private boolean hasSecuritySettingsChanged(DeviceUpdateRequest request) {
        return request.getBiometricEnabled() != null ||
               request.getPinEnabled() != null ||
               request.getLocationEnabled() != null ||
               request.getDeviceFingerprint() != null;
    }
    
    private boolean isOldOsVersion(String osVersion) {
        if (osVersion == null) return false;
        // Simple version comparison - could be enhanced
        return osVersion.compareTo("10.0") < 0;
    }
    
    private void publishDeviceEvent(String eventType, MobileDevice device) {
        try {
            DeviceManagementEvent event = new DeviceManagementEvent(
                eventType,
                device.getUserId(),
                device.getId(),
                device.getDeviceId(),
                device.getStatus().toString(),
                device.getIsTrusted(),
                device.getRiskScore()
            );
            kafkaTemplate.send("mobile-device-management-events", event);
        } catch (Exception e) {
            logger.error("Failed to publish device event", e);
        }
    }
    
    private void publishTrustEvent(String eventType, MobileDevice device, String reason) {
        try {
            DeviceTrustEvent event = new DeviceTrustEvent(
                eventType,
                device.getUserId(),
                device.getId(),
                device.getDeviceId(),
                device.getIsTrusted(),
                reason
            );
            kafkaTemplate.send("device-trust-events", event);
        } catch (Exception e) {
            logger.error("Failed to publish trust event", e);
        }
    }
    
    private void publishStatusEvent(String eventType, MobileDevice device, 
                                  DeviceStatus oldStatus, DeviceStatus newStatus, String reason) {
        try {
            DeviceStatusEvent event = new DeviceStatusEvent(
                eventType,
                device.getUserId(),
                device.getId(),
                device.getDeviceId(),
                oldStatus.toString(),
                newStatus.toString(),
                reason
            );
            kafkaTemplate.send("device-status-events", event);
        } catch (Exception e) {
            logger.error("Failed to publish status event", e);
        }
    }
    
    // DTOs and Response Classes
    public static class DeviceInfo {
        private final UUID id;
        private final String deviceId;
        private final UUID userId;
        private final com.fintech.mobilesdk.domain.DeviceType deviceType;
        private final String deviceName;
        private final String operatingSystem;
        private final String osVersion;
        private final String appVersion;
        private final DeviceStatus status;
        private final Boolean isTrusted;
        private final Double riskScore;
        private final LocalDateTime registeredAt;
        private final LocalDateTime lastActivityAt;
        private final String lastLocation;
        private final Boolean hasSecurityFeatures;
        private final Boolean isRootedOrJailbroken;
        
        public DeviceInfo(UUID id, String deviceId, UUID userId,
                         com.fintech.mobilesdk.domain.DeviceType deviceType, String deviceName,
                         String operatingSystem, String osVersion, String appVersion,
                         DeviceStatus status, Boolean isTrusted, Double riskScore,
                         LocalDateTime registeredAt, LocalDateTime lastActivityAt, String lastLocation,
                         Boolean hasSecurityFeatures, Boolean isRootedOrJailbroken) {
            this.id = id;
            this.deviceId = deviceId;
            this.userId = userId;
            this.deviceType = deviceType;
            this.deviceName = deviceName;
            this.operatingSystem = operatingSystem;
            this.osVersion = osVersion;
            this.appVersion = appVersion;
            this.status = status;
            this.isTrusted = isTrusted;
            this.riskScore = riskScore;
            this.registeredAt = registeredAt;
            this.lastActivityAt = lastActivityAt;
            this.lastLocation = lastLocation;
            this.hasSecurityFeatures = hasSecurityFeatures;
            this.isRootedOrJailbroken = isRootedOrJailbroken;
        }
        
        // Getters
        public UUID getId() { return id; }
        public String getDeviceId() { return deviceId; }
        public UUID getUserId() { return userId; }
        public com.fintech.mobilesdk.domain.DeviceType getDeviceType() { return deviceType; }
        public String getDeviceName() { return deviceName; }
        public String getOperatingSystem() { return operatingSystem; }
        public String getOsVersion() { return osVersion; }
        public String getAppVersion() { return appVersion; }
        public DeviceStatus getStatus() { return status; }
        public Boolean getIsTrusted() { return isTrusted; }
        public Double getRiskScore() { return riskScore; }
        public LocalDateTime getRegisteredAt() { return registeredAt; }
        public LocalDateTime getLastActivityAt() { return lastActivityAt; }
        public String getLastLocation() { return lastLocation; }
        public Boolean getHasSecurityFeatures() { return hasSecurityFeatures; }
        public Boolean getIsRootedOrJailbroken() { return isRootedOrJailbroken; }
    }
    
    public static class DeviceUpdateRequest {
        private String deviceName;
        private String osVersion;
        private String appVersion;
        private String pushToken;
        private String deviceFingerprint;
        private String ipAddress;
        private String location;
        private Boolean biometricEnabled;
        private Boolean pinEnabled;
        private Boolean locationEnabled;
        private Boolean pushEnabled;
        
        // Getters and setters
        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
        
        public String getOsVersion() { return osVersion; }
        public void setOsVersion(String osVersion) { this.osVersion = osVersion; }
        
        public String getAppVersion() { return appVersion; }
        public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
        
        public String getPushToken() { return pushToken; }
        public void setPushToken(String pushToken) { this.pushToken = pushToken; }
        
        public String getDeviceFingerprint() { return deviceFingerprint; }
        public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
        
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        
        public Boolean getBiometricEnabled() { return biometricEnabled; }
        public void setBiometricEnabled(Boolean biometricEnabled) { this.biometricEnabled = biometricEnabled; }
        
        public Boolean getPinEnabled() { return pinEnabled; }
        public void setPinEnabled(Boolean pinEnabled) { this.pinEnabled = pinEnabled; }
        
        public Boolean getLocationEnabled() { return locationEnabled; }
        public void setLocationEnabled(Boolean locationEnabled) { this.locationEnabled = locationEnabled; }
        
        public Boolean getPushEnabled() { return pushEnabled; }
        public void setPushEnabled(Boolean pushEnabled) { this.pushEnabled = pushEnabled; }
    }
    
    public static class DeviceSecurityReport {
        private final UUID deviceId;
        private final String deviceIdString;
        private final Double riskScore;
        private final Boolean isTrusted;
        private final Boolean hasSecurityFeatures;
        private final Boolean isRootedOrJailbroken;
        private final Long totalSessions;
        private final Long suspiciousSessions;
        private final Integer uniqueLocations;
        private final Integer uniqueIpAddresses;
        private final LocalDateTime lastCompromisedActivity;
        private final List<String> recommendations;
        
        public DeviceSecurityReport(UUID deviceId, String deviceIdString, Double riskScore,
                                  Boolean isTrusted, Boolean hasSecurityFeatures, Boolean isRootedOrJailbroken,
                                  Long totalSessions, Long suspiciousSessions, Integer uniqueLocations,
                                  Integer uniqueIpAddresses, LocalDateTime lastCompromisedActivity,
                                  List<String> recommendations) {
            this.deviceId = deviceId;
            this.deviceIdString = deviceIdString;
            this.riskScore = riskScore;
            this.isTrusted = isTrusted;
            this.hasSecurityFeatures = hasSecurityFeatures;
            this.isRootedOrJailbroken = isRootedOrJailbroken;
            this.totalSessions = totalSessions;
            this.suspiciousSessions = suspiciousSessions;
            this.uniqueLocations = uniqueLocations;
            this.uniqueIpAddresses = uniqueIpAddresses;
            this.lastCompromisedActivity = lastCompromisedActivity;
            this.recommendations = recommendations;
        }
        
        // Getters
        public UUID getDeviceId() { return deviceId; }
        public String getDeviceIdString() { return deviceIdString; }
        public Double getRiskScore() { return riskScore; }
        public Boolean getIsTrusted() { return isTrusted; }
        public Boolean getHasSecurityFeatures() { return hasSecurityFeatures; }
        public Boolean getIsRootedOrJailbroken() { return isRootedOrJailbroken; }
        public Long getTotalSessions() { return totalSessions; }
        public Long getSuspiciousSessions() { return suspiciousSessions; }
        public Integer getUniqueLocations() { return uniqueLocations; }
        public Integer getUniqueIpAddresses() { return uniqueIpAddresses; }
        public LocalDateTime getLastCompromisedActivity() { return lastCompromisedActivity; }
        public List<String> getRecommendations() { return recommendations; }
    }
    
    public static class DeviceAnalytics {
        private final Integer totalDevices;
        private final Long trustedDevices;
        private final Long highRiskDevices;
        private final Long devicesWithSecurity;
        private final Double averageRiskScore;
        private final Map<String, Long> deviceTypeBreakdown;
        private final Map<String, Long> statusBreakdown;
        
        public DeviceAnalytics(Integer totalDevices, Long trustedDevices, Long highRiskDevices,
                             Long devicesWithSecurity, Double averageRiskScore,
                             Map<String, Long> deviceTypeBreakdown, Map<String, Long> statusBreakdown) {
            this.totalDevices = totalDevices;
            this.trustedDevices = trustedDevices;
            this.highRiskDevices = highRiskDevices;
            this.devicesWithSecurity = devicesWithSecurity;
            this.averageRiskScore = averageRiskScore;
            this.deviceTypeBreakdown = deviceTypeBreakdown;
            this.statusBreakdown = statusBreakdown;
        }
        
        // Getters
        public Integer getTotalDevices() { return totalDevices; }
        public Long getTrustedDevices() { return trustedDevices; }
        public Long getHighRiskDevices() { return highRiskDevices; }
        public Long getDevicesWithSecurity() { return devicesWithSecurity; }
        public Double getAverageRiskScore() { return averageRiskScore; }
        public Map<String, Long> getDeviceTypeBreakdown() { return deviceTypeBreakdown; }
        public Map<String, Long> getStatusBreakdown() { return statusBreakdown; }
    }
    
    // Event DTOs
    public static class DeviceManagementEvent {
        private final String eventType;
        private final UUID userId;
        private final UUID deviceId;
        private final String deviceIdString;
        private final String status;
        private final Boolean isTrusted;
        private final Double riskScore;
        private final LocalDateTime timestamp;
        
        public DeviceManagementEvent(String eventType, UUID userId, UUID deviceId, String deviceIdString,
                                   String status, Boolean isTrusted, Double riskScore) {
            this.eventType = eventType;
            this.userId = userId;
            this.deviceId = deviceId;
            this.deviceIdString = deviceIdString;
            this.status = status;
            this.isTrusted = isTrusted;
            this.riskScore = riskScore;
            this.timestamp = LocalDateTime.now();
        }
        
        // Getters
        public String getEventType() { return eventType; }
        public UUID getUserId() { return userId; }
        public UUID getDeviceId() { return deviceId; }
        public String getDeviceIdString() { return deviceIdString; }
        public String getStatus() { return status; }
        public Boolean getIsTrusted() { return isTrusted; }
        public Double getRiskScore() { return riskScore; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    public static class DeviceTrustEvent {
        private final String eventType;
        private final UUID userId;
        private final UUID deviceId;
        private final String deviceIdString;
        private final Boolean isTrusted;
        private final String reason;
        private final LocalDateTime timestamp;
        
        public DeviceTrustEvent(String eventType, UUID userId, UUID deviceId, String deviceIdString,
                              Boolean isTrusted, String reason) {
            this.eventType = eventType;
            this.userId = userId;
            this.deviceId = deviceId;
            this.deviceIdString = deviceIdString;
            this.isTrusted = isTrusted;
            this.reason = reason;
            this.timestamp = LocalDateTime.now();
        }
        
        // Getters
        public String getEventType() { return eventType; }
        public UUID getUserId() { return userId; }
        public UUID getDeviceId() { return deviceId; }
        public String getDeviceIdString() { return deviceIdString; }
        public Boolean getIsTrusted() { return isTrusted; }
        public String getReason() { return reason; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    public static class DeviceStatusEvent {
        private final String eventType;
        private final UUID userId;
        private final UUID deviceId;
        private final String deviceIdString;
        private final String oldStatus;
        private final String newStatus;
        private final String reason;
        private final LocalDateTime timestamp;
        
        public DeviceStatusEvent(String eventType, UUID userId, UUID deviceId, String deviceIdString,
                               String oldStatus, String newStatus, String reason) {
            this.eventType = eventType;
            this.userId = userId;
            this.deviceId = deviceId;
            this.deviceIdString = deviceIdString;
            this.oldStatus = oldStatus;
            this.newStatus = newStatus;
            this.reason = reason;
            this.timestamp = LocalDateTime.now();
        }
        
        // Getters
        public String getEventType() { return eventType; }
        public UUID getUserId() { return userId; }
        public UUID getDeviceId() { return deviceId; }
        public String getDeviceIdString() { return deviceIdString; }
        public String getOldStatus() { return oldStatus; }
        public String getNewStatus() { return newStatus; }
        public String getReason() { return reason; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}
