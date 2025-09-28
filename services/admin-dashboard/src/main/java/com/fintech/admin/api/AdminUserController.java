package com.fintech.admin.api;

import com.fintech.admin.application.AdminUserService;
import com.fintech.admin.domain.AdminUser;
import com.fintech.admin.domain.AdminRole;
import com.fintech.admin.domain.AdminStatus;
import com.fintech.commons.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Admin User Management API Controller
 * 
 * Provides REST endpoints for admin user CRUD operations,
 * role management, and user administration.
 */
@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin Users", description = "Admin user management operations")
public class AdminUserController {
    
    private final AdminUserService adminUserService;
    
    @Autowired
    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }
    
    /**
     * Create new admin user
     */
    @PostMapping
    @PreAuthorize("hasPermission('users', 'write')")
    @Operation(summary = "Create admin user", description = "Create a new admin user with specified roles")
    public ResponseEntity<ApiResponse<AdminUser>> createAdminUser(
            @Valid @RequestBody AdminUserService.CreateAdminUserRequest request,
            Authentication authentication) {
        
        UUID createdBy = getCurrentUserId(authentication);
        ApiResponse<AdminUser> response = adminUserService.createAdminUser(request, createdBy);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get admin user by ID
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasPermission('users', 'read')")
    @Operation(summary = "Get admin user", description = "Retrieve admin user by ID")
    public ResponseEntity<AdminUser> getAdminUser(@PathVariable UUID userId) {
        Optional<AdminUser> user = adminUserService.getAdminUser(userId);
        
        return user.map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get all admin users with pagination
     */
    @GetMapping
    @PreAuthorize("hasPermission('users', 'read')")
    @Operation(summary = "List admin users", description = "Get paginated list of admin users")
    public ResponseEntity<Page<AdminUser>> getAllAdminUsers(Pageable pageable) {
        Page<AdminUser> users = adminUserService.getAllAdminUsers(pageable);
        return ResponseEntity.ok(users);
    }
    
    /**
     * Search admin users
     */
    @GetMapping("/search")
    @PreAuthorize("hasPermission('users', 'read')")
    @Operation(summary = "Search admin users", description = "Search admin users by name, username, or email")
    public ResponseEntity<Page<AdminUser>> searchAdminUsers(
            @RequestParam String query,
            Pageable pageable) {
        
        Page<AdminUser> users = adminUserService.searchAdminUsers(query, pageable);
        return ResponseEntity.ok(users);
    }
    
    /**
     * Get users by status
     */
    @GetMapping("/by-status/{status}")
    @PreAuthorize("hasPermission('users', 'read')")
    @Operation(summary = "Get users by status", description = "Retrieve users filtered by status")
    public ResponseEntity<Page<AdminUser>> getUsersByStatus(
            @PathVariable AdminStatus status,
            Pageable pageable) {
        
        Page<AdminUser> users = adminUserService.getUsersByStatus(status, pageable);
        return ResponseEntity.ok(users);
    }
    
    /**
     * Update admin user
     */
    @PutMapping("/{userId}")
    @PreAuthorize("hasPermission('users', 'write')")
    @Operation(summary = "Update admin user", description = "Update admin user details")
    public ResponseEntity<ApiResponse<AdminUser>> updateAdminUser(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUserService.UpdateAdminUserRequest request,
            Authentication authentication) {
        
        UUID updatedBy = getCurrentUserId(authentication);
        ApiResponse<AdminUser> response = adminUserService.updateAdminUser(userId, request, updatedBy);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Update user roles
     */
    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasPermission('users', 'write')")
    @Operation(summary = "Update user roles", description = "Update admin user roles and permissions")
    public ResponseEntity<ApiResponse<AdminUser>> updateUserRoles(
            @PathVariable UUID userId,
            @RequestBody Set<AdminRole> roles,
            Authentication authentication) {
        
        UUID updatedBy = getCurrentUserId(authentication);
        ApiResponse<AdminUser> response = adminUserService.updateUserRoles(userId, roles, updatedBy);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Change user password
     */
    @PostMapping("/{userId}/change-password")
    @PreAuthorize("hasPermission('users', 'write') or #userId == authentication.principal.id")
    @Operation(summary = "Change password", description = "Change admin user password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @PathVariable UUID userId,
            @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        
        UUID changedBy = getCurrentUserId(authentication);
        ApiResponse<Void> response = adminUserService.changePassword(
            userId, request.getCurrentPassword(), request.getNewPassword(), changedBy);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Lock user account
     */
    @PostMapping("/{userId}/lock")
    @PreAuthorize("hasPermission('users', 'write')")
    @Operation(summary = "Lock user account", description = "Lock admin user account")
    public ResponseEntity<ApiResponse<Void>> lockUserAccount(
            @PathVariable UUID userId,
            Authentication authentication) {
        
        UUID actionBy = getCurrentUserId(authentication);
        ApiResponse<Void> response = adminUserService.toggleAccountLock(userId, true, actionBy);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Unlock user account
     */
    @PostMapping("/{userId}/unlock")
    @PreAuthorize("hasPermission('users', 'write')")
    @Operation(summary = "Unlock user account", description = "Unlock admin user account")
    public ResponseEntity<ApiResponse<Void>> unlockUserAccount(
            @PathVariable UUID userId,
            Authentication authentication) {
        
        UUID actionBy = getCurrentUserId(authentication);
        ApiResponse<Void> response = adminUserService.toggleAccountLock(userId, false, actionBy);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Delete admin user
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasPermission('users', 'delete')")
    @Operation(summary = "Delete admin user", description = "Soft delete admin user (set to inactive)")
    public ResponseEntity<ApiResponse<Void>> deleteAdminUser(
            @PathVariable UUID userId,
            Authentication authentication) {
        
        UUID deletedBy = getCurrentUserId(authentication);
        ApiResponse<Void> response = adminUserService.deleteAdminUser(userId, deletedBy);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get user statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasPermission('users', 'read')")
    @Operation(summary = "Get user statistics", description = "Get admin user statistics and counts")
    public ResponseEntity<AdminUserService.AdminUserStatistics> getUserStatistics() {
        AdminUserService.AdminUserStatistics stats = adminUserService.getUserStatistics();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get current user profile
     */
    @GetMapping("/profile")
    @Operation(summary = "Get current user profile", description = "Get current authenticated user's profile")
    public ResponseEntity<AdminUser> getCurrentUserProfile(Authentication authentication) {
        UUID userId = getCurrentUserId(authentication);
        Optional<AdminUser> user = adminUserService.getAdminUser(userId);
        
        return user.map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
    }
    
    private UUID getCurrentUserId(Authentication authentication) {
        // Extract user ID from authentication context
        // This would depend on your JWT token structure
        if (authentication != null && authentication.getPrincipal() instanceof String) {
            return UUID.fromString((String) authentication.getPrincipal());
        }
        throw new RuntimeException("Unable to determine current user ID");
    }
    
    // Request DTOs
    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;
        
        // Getters and setters
        public String getCurrentPassword() { return currentPassword; }
        public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
        
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}
