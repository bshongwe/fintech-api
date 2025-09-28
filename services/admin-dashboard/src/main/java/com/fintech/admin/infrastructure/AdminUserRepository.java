package com.fintech.admin.infrastructure;

import com.fintech.admin.domain.AdminUser;
import com.fintech.admin.domain.AdminStatus;
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
 * Admin User Repository
 * 
 * Provides data access operations for admin user management with advanced querying capabilities.
 */
@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {
    
    /**
     * Find admin user by username
     */
    Optional<AdminUser> findByUsername(String username);
    
    /**
     * Find admin user by email
     */
    Optional<AdminUser> findByEmail(String email);
    
    /**
     * Check if username exists
     */
    boolean existsByUsername(String username);
    
    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);
    
    /**
     * Find users by status
     */
    List<AdminUser> findByStatus(AdminStatus status);
    
    /**
     * Find users by status with pagination
     */
    Page<AdminUser> findByStatus(AdminStatus status, Pageable pageable);
    
    /**
     * Find users with specific role
     */
    @Query("SELECT u FROM AdminUser u JOIN u.roles r WHERE r.name = :roleName")
    List<AdminUser> findByRoleName(@Param("roleName") String roleName);
    
    /**
     * Find users with specific permission
     */
    @Query("SELECT DISTINCT u FROM AdminUser u " +
           "JOIN u.roles r " +
           "JOIN r.permissions p " +
           "WHERE p.name = :permissionName")
    List<AdminUser> findByPermissionName(@Param("permissionName") String permissionName);
    
    /**
     * Find locked accounts
     */
    @Query("SELECT u FROM AdminUser u WHERE u.accountLockedUntil > :currentTime")
    List<AdminUser> findLockedAccounts(@Param("currentTime") LocalDateTime currentTime);
    
    /**
     * Find users with failed login attempts
     */
    @Query("SELECT u FROM AdminUser u WHERE u.failedLoginAttempts >= :threshold")
    List<AdminUser> findUsersWithFailedLogins(@Param("threshold") Integer threshold);
    
    /**
     * Find users who haven't logged in recently
     */
    @Query("SELECT u FROM AdminUser u WHERE u.lastLogin < :threshold OR u.lastLogin IS NULL")
    List<AdminUser> findInactiveUsers(@Param("threshold") LocalDateTime threshold);
    
    /**
     * Find users created by specific admin
     */
    List<AdminUser> findByCreatedBy(UUID createdBy);
    
    /**
     * Find users created within date range
     */
    @Query("SELECT u FROM AdminUser u WHERE u.createdAt BETWEEN :startDate AND :endDate")
    List<AdminUser> findUsersCreatedBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * Count users by status
     */
    long countByStatus(AdminStatus status);
    
    /**
     * Count users with MFA enabled
     */
    long countByMfaEnabled(Boolean mfaEnabled);
    
    /**
     * Search users by name or username
     */
    @Query("SELECT u FROM AdminUser u WHERE " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<AdminUser> searchUsers(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    /**
     * Update last login time
     */
    @Modifying
    @Query("UPDATE AdminUser u SET u.lastLogin = :loginTime, u.failedLoginAttempts = 0, u.accountLockedUntil = NULL WHERE u.id = :userId")
    void updateLastLogin(@Param("userId") UUID userId, @Param("loginTime") LocalDateTime loginTime);
    
    /**
     * Increment failed login attempts
     */
    @Modifying
    @Query("UPDATE AdminUser u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :userId")
    void incrementFailedLoginAttempts(@Param("userId") UUID userId);
    
    /**
     * Lock user account
     */
    @Modifying
    @Query("UPDATE AdminUser u SET u.accountLockedUntil = :lockUntil WHERE u.id = :userId")
    void lockAccount(@Param("userId") UUID userId, @Param("lockUntil") LocalDateTime lockUntil);
    
    /**
     * Unlock user account
     */
    @Modifying
    @Query("UPDATE AdminUser u SET u.accountLockedUntil = NULL, u.failedLoginAttempts = 0 WHERE u.id = :userId")
    void unlockAccount(@Param("userId") UUID userId);
    
    /**
     * Update user status
     */
    @Modifying
    @Query("UPDATE AdminUser u SET u.status = :status WHERE u.id = :userId")
    void updateStatus(@Param("userId") UUID userId, @Param("status") AdminStatus status);
}
