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
 * Push Notification Entity
 * 
 * Represents push notifications sent to mobile devices with delivery tracking,
 * targeting, and engagement analytics.
 */
@Entity
@Table(name = "push_notifications", indexes = {
    @Index(name = "idx_notification_type", columnList = "notification_type"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_device_id", columnList = "device_id"),
    @Index(name = "idx_scheduled_at", columnList = "scheduled_at"),
    @Index(name = "idx_sent_at", columnList = "sent_at")
})
@EntityListeners(AuditingEntityListener.class)
public class PushNotification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id")
    private UUID userId; // null for broadcast notifications
    
    @Column(name = "device_id")
    private UUID deviceId; // null for user-wide notifications
    
    @Column(name = "notification_type", nullable = false, length = 50)
    @NotBlank(message = "Notification type is required")
    private String notificationType;
    
    @Column(nullable = false, length = 200)
    @NotBlank(message = "Title is required")
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String message;
    
    @ElementCollection
    @CollectionTable(name = "notification_data", joinColumns = @JoinColumn(name = "notification_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private Map<String, String> data = new HashMap<>();
    
    @Column(name = "deep_link")
    private String deepLink;
    
    @Column(name = "image_url")
    private String imageUrl;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationPriority priority = NotificationPriority.NORMAL;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status = NotificationStatus.SCHEDULED;
    
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;
    
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    
    @Column(name = "opened_at")
    private LocalDateTime openedAt;
    
    @Column(name = "push_token")
    private String pushToken;
    
    @Column(name = "platform", length = 20)
    private String platform; // iOS, Android, Web
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "retry_count")
    private Integer retryCount = 0;
    
    @Column(name = "max_retries")
    private Integer maxRetries = 3;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "campaign_id")
    private String campaignId;
    
    @Column(name = "batch_id")
    private String batchId;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public PushNotification() {}
    
    public PushNotification(UUID userId, String notificationType, String title, String message) {
        this.userId = userId;
        this.notificationType = notificationType;
        this.title = title;
        this.message = message;
        this.scheduledAt = LocalDateTime.now();
    }
    
    // Business Methods
    public boolean canSend() {
        return status == NotificationStatus.SCHEDULED && 
               (scheduledAt == null || scheduledAt.isBefore(LocalDateTime.now())) &&
               (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
    }
    
    public boolean canRetry() {
        return status == NotificationStatus.FAILED && 
               retryCount < maxRetries &&
               (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
    }
    
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
    
    public void markAsSent(String pushToken) {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.pushToken = pushToken;
    }
    
    public void markAsDelivered() {
        this.status = NotificationStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }
    
    public void markAsOpened() {
        this.status = NotificationStatus.OPENED;
        this.openedAt = LocalDateTime.now();
    }
    
    public void markAsFailed(String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
    }
    
    public void addData(String key, String value) {
        this.data.put(key, value);
    }
    
    public String getData(String key) {
        return this.data.get(key);
    }
    
    public boolean isHighPriority() {
        return priority == NotificationPriority.HIGH || priority == NotificationPriority.CRITICAL;
    }
    
    public boolean isTransactional() {
        return notificationType.contains("TRANSACTION") || 
               notificationType.contains("PAYMENT") ||
               notificationType.contains("SECURITY");
    }
    
    public double getEngagementRate() {
        if (sentAt == null) return 0.0;
        if (openedAt != null) return 1.0;  // 100% engagement if opened
        if (deliveredAt != null) return 0.5; // 50% engagement if delivered but not opened
        return 0.0; // 0% engagement if not delivered
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    
    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public Map<String, String> getData() { return data; }
    public void setData(Map<String, String> data) { this.data = data; }
    
    public String getDeepLink() { return deepLink; }
    public void setDeepLink(String deepLink) { this.deepLink = deepLink; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    public NotificationPriority getPriority() { return priority; }
    public void setPriority(NotificationPriority priority) { this.priority = priority; }
    
    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }
    
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
    
    public String getPushToken() { return pushToken; }
    public void setPushToken(String pushToken) { this.pushToken = pushToken; }
    
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    
    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
    
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    
    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }
    
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

enum NotificationPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

enum NotificationStatus {
    SCHEDULED,
    SENT,
    DELIVERED,
    OPENED,
    FAILED,
    EXPIRED,
    CANCELLED
}
