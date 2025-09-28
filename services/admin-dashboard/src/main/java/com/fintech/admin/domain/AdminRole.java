package com.fintech.admin.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Admin Role Entity
 * 
 * Represents hierarchical roles with associated permissions.
 * Supports fine-grained access control for different admin functions.
 */
@Entity
@Table(name = "admin_roles", indexes = {
    @Index(name = "idx_role_name", columnList = "name"),
    @Index(name = "idx_role_level", columnList = "level")
})
@EntityListeners(AuditingEntityListener.class)
public class AdminRole {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false, length = 50)
    @NotBlank(message = "Role name is required")
    @Size(max = 50, message = "Role name must not exceed 50 characters")
    private String name;
    
    @Column(length = 200)
    @Size(max = 200, message = "Description must not exceed 200 characters")
    private String description;
    
    @Column(nullable = false)
    private Integer level; // Hierarchy level (1 = highest)
    
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "admin_role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<AdminPermission> permissions = new HashSet<>();
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public AdminRole() {}
    
    public AdminRole(String name, String description, Integer level) {
        this.name = name;
        this.description = description;
        this.level = level;
    }
    
    // Business Methods
    public boolean hasPermission(String permissionName) {
        return permissions.stream()
            .anyMatch(permission -> permission.getName().equals(permissionName));
    }
    
    public void addPermission(AdminPermission permission) {
        this.permissions.add(permission);
    }
    
    public void removePermission(AdminPermission permission) {
        this.permissions.remove(permission);
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    
    public Set<AdminPermission> getPermissions() { return permissions; }
    public void setPermissions(Set<AdminPermission> permissions) { this.permissions = permissions; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
