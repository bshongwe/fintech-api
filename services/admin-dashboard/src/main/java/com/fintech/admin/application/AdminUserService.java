package com.fintech.admin.application;

import com.fintech.admin.domain.AdminUser;
import com.fintech.admin.domain.AdminRole;
import com.fintech.admin.domain.AdminStatus;
import com.fintech.admin.infrastructure.AdminUserRepository;
import com.fintech.commons.ApiResponse;
import com.fintech.commons.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Admin User Management Service
 * 
 * Handles all admin user operations including CRUD, authentication,
 * role management, and security controls.
 */
@Service
@Validated
@Transactional
public class AdminUserService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminUserService.class);
    
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Autowired
    public AdminUserService(AdminUserRepository adminUserRepository,
                           PasswordEncoder passwordEncoder,
                           KafkaTemplate<String, Object> kafkaTemplate) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Create new admin user
     */
    public ApiResponse<AdminUser> createAdminUser(@Valid CreateAdminUserRequest request, UUID createdBy) {
        try {
            // Check if username or email already exists
            if (adminUserRepository.existsByUsername(request.getUsername())) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Username already exists")
                    .field("username")
                    .build());
            }
            
            if (adminUserRepository.existsByEmail(request.getEmail())) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Email already exists")
                    .field("email")
                    .build());
            }
            
            // Create new admin user
            AdminUser adminUser = new AdminUser(
                request.getUsername(),
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                passwordEncoder.encode(request.getPassword())
            );
            
            adminUser.setCreatedBy(createdBy);
            adminUser.setStatus(AdminStatus.ACTIVE);
            
            AdminUser savedUser = adminUserRepository.save(adminUser);
            
            // Publish audit event
            publishAuditEvent("ADMIN_USER_CREATED", savedUser.getId(), createdBy, 
                "Admin user created: " + savedUser.getUsername());
            
            logger.info("Admin user created: {} by {}", savedUser.getUsername(), createdBy);
            
            return ApiResponse.success(savedUser);
            
        } catch (Exception e) {
            logger.error("Error creating admin user", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to create admin user")
                .build());
        }
    }
    
    /**
     * Get admin user by ID
     */
    @Cacheable(value = "adminUsers", key = "#userId")
    public Optional<AdminUser> getAdminUser(@NotNull UUID userId) {
        return adminUserRepository.findById(userId);
    }
    
    /**
     * Get admin user by username
     */
    @Cacheable(value = "adminUsers", key = "#username")
    public Optional<AdminUser> getAdminUserByUsername(@NotBlank String username) {
        return adminUserRepository.findByUsername(username);
    }
    
    /**
     * Get all admin users with pagination
     */
    public Page<AdminUser> getAllAdminUsers(Pageable pageable) {
        return adminUserRepository.findAll(pageable);
    }
    
    /**
     * Search admin users
     */
    public Page<AdminUser> searchAdminUsers(@NotBlank String searchTerm, Pageable pageable) {
        return adminUserRepository.searchUsers(searchTerm, pageable);
    }
    
    /**
     * Get users by status
     */
    public Page<AdminUser> getUsersByStatus(@NotNull AdminStatus status, Pageable pageable) {
        return adminUserRepository.findByStatus(status, pageable);
    }
    
    /**
     * Update admin user
     */
    @CacheEvict(value = "adminUsers", allEntries = true)
    public ApiResponse<AdminUser> updateAdminUser(@NotNull UUID userId, 
                                                 @Valid UpdateAdminUserRequest request, 
                                                 UUID updatedBy) {
        try {
            Optional<AdminUser> optionalUser = adminUserRepository.findById(userId);
            if (optionalUser.isEmpty()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Admin user not found")
                    .build());
            }
            
            AdminUser adminUser = optionalUser.get();
            
            // Update fields if provided
            if (request.getEmail() != null && !request.getEmail().equals(adminUser.getEmail())) {
                if (adminUserRepository.existsByEmail(request.getEmail())) {
                    return ApiResponse.error(ErrorResponse.builder()
                        .message("Email already exists")
                        .field("email")
                        .build());
                }
                adminUser.setEmail(request.getEmail());
            }
            
            if (request.getFirstName() != null) {
                adminUser.setFirstName(request.getFirstName());
            }
            
            if (request.getLastName() != null) {
                adminUser.setLastName(request.getLastName());
            }
            
            if (request.getStatus() != null) {
                adminUser.setStatus(request.getStatus());
            }
            
            AdminUser updatedUser = adminUserRepository.save(adminUser);
            
            // Publish audit event
            publishAuditEvent("ADMIN_USER_UPDATED", updatedUser.getId(), updatedBy, 
                "Admin user updated: " + updatedUser.getUsername());
            
            logger.info("Admin user updated: {} by {}", updatedUser.getUsername(), updatedBy);
            
            return ApiResponse.success(updatedUser);
            
        } catch (Exception e) {
            logger.error("Error updating admin user", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to update admin user")
                .build());
        }
    }
    
    /**
     * Update admin user roles
     */
    @CacheEvict(value = "adminUsers", allEntries = true)
    public ApiResponse<AdminUser> updateUserRoles(@NotNull UUID userId, 
                                                 @NotNull Set<AdminRole> roles, 
                                                 UUID updatedBy) {
        try {
            Optional<AdminUser> optionalUser = adminUserRepository.findById(userId);
            if (optionalUser.isEmpty()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Admin user not found")
                    .build());
            }
            
            AdminUser adminUser = optionalUser.get();
            adminUser.setRoles(roles);
            
            AdminUser updatedUser = adminUserRepository.save(adminUser);
            
            // Publish audit event
            publishAuditEvent("ADMIN_USER_ROLES_UPDATED", updatedUser.getId(), updatedBy, 
                "Admin user roles updated: " + updatedUser.getUsername());
            
            logger.info("Admin user roles updated: {} by {}", updatedUser.getUsername(), updatedBy);
            
            return ApiResponse.success(updatedUser);
            
        } catch (Exception e) {
            logger.error("Error updating admin user roles", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to update admin user roles")
                .build());
        }
    }
    
    /**
     * Change admin user password
     */
    @CacheEvict(value = "adminUsers", key = "#userId")
    public ApiResponse<Void> changePassword(@NotNull UUID userId, 
                                           @NotBlank String currentPassword, 
                                           @NotBlank String newPassword,
                                           UUID changedBy) {
        try {
            Optional<AdminUser> optionalUser = adminUserRepository.findById(userId);
            if (optionalUser.isEmpty()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Admin user not found")
                    .build());
            }
            
            AdminUser adminUser = optionalUser.get();
            
            // Verify current password
            if (!passwordEncoder.matches(currentPassword, adminUser.getPasswordHash())) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Current password is incorrect")
                    .build());
            }
            
            // Update password
            adminUser.setPasswordHash(passwordEncoder.encode(newPassword));
            adminUserRepository.save(adminUser);
            
            // Publish audit event
            publishAuditEvent("ADMIN_USER_PASSWORD_CHANGED", adminUser.getId(), changedBy, 
                "Admin user password changed: " + adminUser.getUsername());
            
            logger.info("Admin user password changed: {} by {}", adminUser.getUsername(), changedBy);
            
            return ApiResponse.success(null);
            
        } catch (Exception e) {
            logger.error("Error changing admin user password", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to change password")
                .build());
        }
    }
    
    /**
     * Lock/unlock admin user account
     */
    @CacheEvict(value = "adminUsers", key = "#userId")
    public ApiResponse<Void> toggleAccountLock(@NotNull UUID userId, boolean lock, UUID actionBy) {
        try {
            Optional<AdminUser> optionalUser = adminUserRepository.findById(userId);
            if (optionalUser.isEmpty()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Admin user not found")
                    .build());
            }
            
            AdminUser adminUser = optionalUser.get();
            
            if (lock) {
                adminUser.setAccountLockedUntil(LocalDateTime.now().plusDays(30));
                adminUser.setStatus(AdminStatus.LOCKED);
            } else {
                adminUser.setAccountLockedUntil(null);
                adminUser.setFailedLoginAttempts(0);
                adminUser.setStatus(AdminStatus.ACTIVE);
            }
            
            adminUserRepository.save(adminUser);
            
            // Publish audit event
            String action = lock ? "locked" : "unlocked";
            publishAuditEvent("ADMIN_USER_ACCOUNT_" + action.toUpperCase(), adminUser.getId(), actionBy, 
                "Admin user account " + action + ": " + adminUser.getUsername());
            
            logger.info("Admin user account {}: {} by {}", action, adminUser.getUsername(), actionBy);
            
            return ApiResponse.success(null);
            
        } catch (Exception e) {
            logger.error("Error toggling account lock", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to update account lock status")
                .build());
        }
    }
    
    /**
     * Delete admin user (soft delete by setting status to INACTIVE)
     */
    @CacheEvict(value = "adminUsers", allEntries = true)
    public ApiResponse<Void> deleteAdminUser(@NotNull UUID userId, UUID deletedBy) {
        try {
            Optional<AdminUser> optionalUser = adminUserRepository.findById(userId);
            if (optionalUser.isEmpty()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Admin user not found")
                    .build());
            }
            
            AdminUser adminUser = optionalUser.get();
            adminUser.setStatus(AdminStatus.INACTIVE);
            adminUserRepository.save(adminUser);
            
            // Publish audit event
            publishAuditEvent("ADMIN_USER_DELETED", adminUser.getId(), deletedBy, 
                "Admin user deleted: " + adminUser.getUsername());
            
            logger.info("Admin user deleted: {} by {}", adminUser.getUsername(), deletedBy);
            
            return ApiResponse.success(null);
            
        } catch (Exception e) {
            logger.error("Error deleting admin user", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to delete admin user")
                .build());
        }
    }
    
    /**
     * Record successful login
     */
    @CacheEvict(value = "adminUsers", key = "#userId")
    public void recordSuccessfulLogin(@NotNull UUID userId) {
        adminUserRepository.updateLastLogin(userId, LocalDateTime.now());
        
        publishAuditEvent("ADMIN_USER_LOGIN_SUCCESS", userId, userId, 
            "Admin user successful login");
    }
    
    /**
     * Record failed login attempt
     */
    public void recordFailedLogin(@NotNull UUID userId) {
        adminUserRepository.incrementFailedLoginAttempts(userId);
        
        publishAuditEvent("ADMIN_USER_LOGIN_FAILED", userId, userId, 
            "Admin user failed login attempt");
    }
    
    /**
     * Get user statistics
     */
    public AdminUserStatistics getUserStatistics() {
        long totalUsers = adminUserRepository.count();
        long activeUsers = adminUserRepository.countByStatus(AdminStatus.ACTIVE);
        long lockedUsers = adminUserRepository.countByStatus(AdminStatus.LOCKED);
        long mfaEnabledUsers = adminUserRepository.countByMfaEnabled(true);
        
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<AdminUser> inactiveUsers = adminUserRepository.findInactiveUsers(thirtyDaysAgo);
        
        return new AdminUserStatistics(totalUsers, activeUsers, lockedUsers, 
                                     mfaEnabledUsers, inactiveUsers.size());
    }
    
    private void publishAuditEvent(String eventType, UUID userId, UUID actionBy, String description) {
        try {
            AuditEvent auditEvent = new AuditEvent(
                eventType,
                "admin-dashboard",
                userId.toString(),
                actionBy,
                description
            );
            
            kafkaTemplate.send("audit-events", auditEvent);
        } catch (Exception e) {
            logger.error("Failed to publish audit event", e);
        }
    }
    
    // Request DTOs
    public static class CreateAdminUserRequest {
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private String password;
        
        // Getters and setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
    
    public static class UpdateAdminUserRequest {
        private String email;
        private String firstName;
        private String lastName;
        private AdminStatus status;
        
        // Getters and setters
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        
        public AdminStatus getStatus() { return status; }
        public void setStatus(AdminStatus status) { this.status = status; }
    }
    
    // Statistics DTO
    public static class AdminUserStatistics {
        private final long totalUsers;
        private final long activeUsers;
        private final long lockedUsers;
        private final long mfaEnabledUsers;
        private final long inactiveUsers;
        
        public AdminUserStatistics(long totalUsers, long activeUsers, long lockedUsers, 
                                 long mfaEnabledUsers, long inactiveUsers) {
            this.totalUsers = totalUsers;
            this.activeUsers = activeUsers;
            this.lockedUsers = lockedUsers;
            this.mfaEnabledUsers = mfaEnabledUsers;
            this.inactiveUsers = inactiveUsers;
        }
        
        // Getters
        public long getTotalUsers() { return totalUsers; }
        public long getActiveUsers() { return activeUsers; }
        public long getLockedUsers() { return lockedUsers; }
        public long getMfaEnabledUsers() { return mfaEnabledUsers; }
        public long getInactiveUsers() { return inactiveUsers; }
    }
    
    // Audit Event DTO
    public static class AuditEvent {
        private final String eventType;
        private final String service;
        private final String resourceId;
        private final UUID userId;
        private final String description;
        private final LocalDateTime timestamp;
        
        public AuditEvent(String eventType, String service, String resourceId, 
                         UUID userId, String description) {
            this.eventType = eventType;
            this.service = service;
            this.resourceId = resourceId;
            this.userId = userId;
            this.description = description;
            this.timestamp = LocalDateTime.now();
        }
        
        // Getters
        public String getEventType() { return eventType; }
        public String getService() { return service; }
        public String getResourceId() { return resourceId; }
        public UUID getUserId() { return userId; }
        public String getDescription() { return description; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}
