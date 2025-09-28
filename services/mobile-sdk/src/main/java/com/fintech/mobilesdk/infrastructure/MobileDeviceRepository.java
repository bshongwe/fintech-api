package com.fintech.mobilesdk.infrastructure;

import com.fintech.mobilesdk.domain.MobileDevice;
import com.fintech.mobilesdk.domain.DeviceType;
import com.fintech.mobilesdk.domain.DeviceStatus;
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
 * Mobile Device Repository
 * 
 * Provides data access operations for mobile device management with advanced querying capabilities.
 */
@Repository
public interface MobileDeviceRepository extends JpaRepository<MobileDevice, UUID> {
    
    /**
     * Find device by device ID
     */
    Optional<MobileDevice> findByDeviceId(String deviceId);
    
    /**
     * Find devices by user ID
     */
    List<MobileDevice> findByUserId(UUID userId);
    
    /**
     * Find devices by user ID with pagination
     */
    Page<MobileDevice> findByUserId(UUID userId, Pageable pageable);
    
    /**
     * Find devices by status
     */
    List<MobileDevice> findByStatus(DeviceStatus status);
    
    /**
     * Find devices by device type
     */
    List<MobileDevice> findByDeviceType(DeviceType deviceType);
    
    /**
     * Find devices by user and status
     */
    List<MobileDevice> findByUserIdAndStatus(UUID userId, DeviceStatus status);
    
    /**
     * Find trusted devices for user
     */
    @Query("SELECT d FROM MobileDevice d WHERE d.userId = :userId AND d.isTrusted = true AND d.status = 'ACTIVE'")
    List<MobileDevice> findTrustedDevicesByUser(@Param("userId") UUID userId);
    
    /**
     * Find devices with high risk scores
     */
    @Query("SELECT d FROM MobileDevice d WHERE d.riskScore >= :threshold ORDER BY d.riskScore DESC")
    List<MobileDevice> findHighRiskDevices(@Param("threshold") double threshold);
    
    /**
     * Find inactive devices
     */
    @Query("SELECT d FROM MobileDevice d WHERE d.lastActivityAt < :threshold OR d.lastActivityAt IS NULL")
    List<MobileDevice> findInactiveDevices(@Param("threshold") LocalDateTime threshold);
    
    /**
     * Find rooted/jailbroken devices
     */
    @Query("SELECT d FROM MobileDevice d WHERE d.isRootedOrJailbroken = true")
    List<MobileDevice> findRootedOrJailbrokenDevices();
    
    /**
     * Find devices by IP address
     */
    List<MobileDevice> findByLastIpAddress(String ipAddress);
    
    /**
     * Find devices by location pattern
     */
    @Query("SELECT d FROM MobileDevice d WHERE d.lastLocation LIKE :locationPattern")
    List<MobileDevice> findByLocationPattern(@Param("locationPattern") String locationPattern);
    
    /**
     * Find devices registered from same IP
     */
    @Query("SELECT d FROM MobileDevice d WHERE d.registrationIp = :ip AND d.userId != :excludeUserId")
    List<MobileDevice> findDevicesRegisteredFromSameIp(@Param("ip") String ip, @Param("excludeUserId") UUID excludeUserId);
    
    /**
     * Count devices by user
     */
    long countByUserId(UUID userId);
    
    /**
     * Count active devices by user
     */
    long countByUserIdAndStatus(UUID userId, DeviceStatus status);
    
    /**
     * Count devices by type
     */
    long countByDeviceType(DeviceType deviceType);
    
    /**
     * Count devices registered in time period
     */
    @Query("SELECT COUNT(d) FROM MobileDevice d WHERE d.createdAt BETWEEN :startDate AND :endDate")
    long countDevicesRegisteredBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get device statistics by type
     */
    @Query("SELECT d.deviceType, COUNT(d) FROM MobileDevice d GROUP BY d.deviceType")
    List<Object[]> getDeviceStatsByType();
    
    /**
     * Get device statistics by OS
     */
    @Query("SELECT d.operatingSystem, COUNT(d) FROM MobileDevice d WHERE d.operatingSystem IS NOT NULL GROUP BY d.operatingSystem")
    List<Object[]> getDeviceStatsByOS();
    
    /**
     * Update device activity
     */
    @Modifying
    @Query("UPDATE MobileDevice d SET d.lastActivityAt = :activityTime, d.lastIpAddress = :ipAddress, " +
           "d.lastLocation = :location WHERE d.id = :deviceId")
    void updateDeviceActivity(@Param("deviceId") UUID deviceId, 
                             @Param("activityTime") LocalDateTime activityTime,
                             @Param("ipAddress") String ipAddress, 
                             @Param("location") String location);
    
    /**
     * Update device risk score
     */
    @Modifying
    @Query("UPDATE MobileDevice d SET d.riskScore = :riskScore WHERE d.id = :deviceId")
    void updateRiskScore(@Param("deviceId") UUID deviceId, @Param("riskScore") double riskScore);
    
    /**
     * Update device trust status
     */
    @Modifying
    @Query("UPDATE MobileDevice d SET d.isTrusted = :trusted WHERE d.id = :deviceId")
    void updateTrustStatus(@Param("deviceId") UUID deviceId, @Param("trusted") boolean trusted);
    
    /**
     * Update device status
     */
    @Modifying
    @Query("UPDATE MobileDevice d SET d.status = :status WHERE d.id = :deviceId")
    void updateDeviceStatus(@Param("deviceId") UUID deviceId, @Param("status") DeviceStatus status);
    
    /**
     * Update push token
     */
    @Modifying
    @Query("UPDATE MobileDevice d SET d.pushToken = :pushToken WHERE d.id = :deviceId")
    void updatePushToken(@Param("deviceId") UUID deviceId, @Param("pushToken") String pushToken);
    
    /**
     * Bulk update device status for user
     */
    @Modifying
    @Query("UPDATE MobileDevice d SET d.status = :status WHERE d.userId = :userId AND d.status = :currentStatus")
    void bulkUpdateDeviceStatusForUser(@Param("userId") UUID userId, 
                                      @Param("currentStatus") DeviceStatus currentStatus,
                                      @Param("status") DeviceStatus status);
    
    /**
     * Find devices needing security update
     */
    @Query("SELECT d FROM MobileDevice d WHERE " +
           "(d.appVersion < :minAppVersion OR d.osVersion < :minOsVersion) AND d.status = 'ACTIVE'")
    List<MobileDevice> findDevicesNeedingUpdate(@Param("minAppVersion") String minAppVersion, 
                                               @Param("minOsVersion") String minOsVersion);
    
    /**
     * Find suspicious device patterns
     */
    @Query("SELECT d FROM MobileDevice d WHERE " +
           "d.riskScore > :riskThreshold OR " +
           "d.isRootedOrJailbroken = true OR " +
           "(d.isTrusted = false AND d.createdAt > :recentThreshold)")
    List<MobileDevice> findSuspiciousDevices(@Param("riskThreshold") double riskThreshold, 
                                           @Param("recentThreshold") LocalDateTime recentThreshold);
}
