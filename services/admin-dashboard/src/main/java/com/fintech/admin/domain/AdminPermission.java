package com.fintech.admin.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Admin Permission Entity
 * 
 * Represents granular permissions for different admin operations.
 * Supports resource-based access control with action-level permissions.
 */
@Entity
@Table(name = "admin_permissions", indexes = {
    @Index(name = "idx_permission_name", columnList = "name"),
    @Index(name = "idx_permission_resource", columnList = "resource"),
    @Index(name = "idx_permission_action", columnList = "action")
})
@EntityListeners(AuditingEntityListener.class)
public class AdminPermission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false, length = 100)
    @NotBlank(message = "Permission name is required")
    @Size(max = 100, message = "Permission name must not exceed 100 characters")
    private String name;
    
    @Column(length = 200)
    @Size(max = 200, message = "Description must not exceed 200 characters")
    private String description;
    
    @Column(nullable = false, length = 50)
    @NotBlank(message = "Resource is required")
    @Size(max = 50, message = "Resource must not exceed 50 characters")
    private String resource; // e.g., "users", "reports", "transactions"
    
    @Column(nullable = false, length = 20)
    @NotBlank(message = "Action is required")
    @Size(max = 20, message = "Action must not exceed 20 characters")
    private String action; // e.g., "read", "write", "delete", "execute"
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public AdminPermission() {}
    
    public AdminPermission(String name, String description, String resource, String action) {
        this.name = name;
        this.description = description;
        this.resource = resource;
        this.action = action;
    }
    
    // Business Methods
    public String getFullPermission() {
        return resource + ":" + action;
    }
    
    public boolean matches(String resource, String action) {
        return this.resource.equals(resource) && this.action.equals(action);
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
