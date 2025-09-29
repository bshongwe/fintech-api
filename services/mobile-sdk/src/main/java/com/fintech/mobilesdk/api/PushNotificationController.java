package com.fintech.mobilesdk.api;

import com.fintech.mobilesdk.application.PushNotificationService;
import com.fintech.mobilesdk.application.PushNotificationService.*;
import com.fintech.commons.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Push Notification Controller
 * 
 * Provides REST API endpoints for sending push notifications, managing notification
 * preferences, and retrieving notification analytics.
 */
@RestController
@RequestMapping("/api/v1/mobile/notifications")
@Tag(name = "Push Notifications", description = "Mobile push notification management and delivery")
@Validated
public class PushNotificationController {
    
    private static final Logger logger = LoggerFactory.getLogger(PushNotificationController.class);
    
    private final PushNotificationService notificationService;
    
    @Autowired
    public PushNotificationController(PushNotificationService notificationService) {
        this.notificationService = notificationService;
    }
    
    /**
     * Send push notification to a user
     */
    @PostMapping("/send")
    @Operation(summary = "Send notification", description = "Send push notification to a specific user")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Notification sent successfully"),
        @SwaggerApiResponse(responseCode = "400", description = "Invalid request"),
        @SwaggerApiResponse(responseCode = "404", description = "No active devices found")
    })
    @PreAuthorize("hasRole('ADMIN') or hasRole('NOTIFICATION_SENDER')")
    public ResponseEntity<ApiResponse<PushNotificationResponse>> sendNotification(
            @Valid @RequestBody SendNotificationRequest request) {
        
        logger.info("Sending notification to user: {} - {}", request.getUserId(), request.getTitle());
        
        ApiResponse<PushNotificationResponse> response = notificationService.sendNotification(request);
        
        HttpStatus status = response.getSuccess() ? HttpStatus.OK : 
                          response.getError().getMessage().contains("No active devices") ? 
                          HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Send bulk notifications to multiple users
     */
    @PostMapping("/send-bulk")
    @Operation(summary = "Send bulk notifications", description = "Send push notification to multiple users")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Bulk notifications queued successfully"),
        @SwaggerApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PreAuthorize("hasRole('ADMIN') or hasRole('NOTIFICATION_SENDER')")
    public ResponseEntity<ApiResponse<BulkNotificationResponse>> sendBulkNotifications(
            @Valid @RequestBody BulkNotificationRequest request) {
        
        logger.info("Sending bulk notification to {} users - {}", request.getUserIds().size(), request.getTitle());
        
        ApiResponse<BulkNotificationResponse> response = notificationService.sendBulkNotifications(request);
        
        HttpStatus status = response.getSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Send broadcast notification to all users
     */
    @PostMapping("/broadcast")
    @Operation(summary = "Broadcast notification", description = "Send push notification to all active users")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Broadcast notification queued successfully"),
        @SwaggerApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BroadcastNotificationResponse>> sendBroadcastNotification(
            @Valid @RequestBody BroadcastNotificationRequest request) {
        
        logger.info("Sending broadcast notification - {}", request.getTitle());
        
        ApiResponse<BroadcastNotificationResponse> response = notificationService.sendBroadcastNotification(request);
        
        HttpStatus status = response.getSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Get notification status
     */
    @GetMapping("/{notificationId}/status")
    @Operation(summary = "Get notification status", description = "Get delivery status of a specific notification")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Notification status retrieved successfully"),
        @SwaggerApiResponse(responseCode = "404", description = "Notification not found")
    })
    @PreAuthorize("hasRole('MOBILE_USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<NotificationStatusInfo>> getNotificationStatus(
            @PathVariable @NotNull UUID notificationId) {
        
        Optional<NotificationStatusInfo> status = notificationService.getNotificationStatus(notificationId);
        
        if (status.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(status.get()));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(com.fintech.commons.ErrorResponse.builder()
                    .message("Notification not found")
                    .build()));
        }
    }
    
    /**
     * Update device push token
     */
    @PutMapping("/device/{deviceId}/token")
    @Operation(summary = "Update push token", description = "Update push notification token for a device")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Push token updated successfully"),
        @SwaggerApiResponse(responseCode = "404", description = "Device not found")
    })
    @PreAuthorize("hasRole('MOBILE_USER')")
    public ResponseEntity<ApiResponse<Void>> updatePushToken(
            @PathVariable @NotBlank String deviceId,
            @Valid @RequestBody PushTokenUpdateRequest request) {
        
        logger.info("Updating push token for device: {}", deviceId);
        
        ApiResponse<Void> response = notificationService.updatePushToken(deviceId, request.getPushToken());
        
        HttpStatus status = response.getSuccess() ? HttpStatus.OK : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Mark notification as read
     */
    @PostMapping("/{notificationId}/read")
    @Operation(summary = "Mark as read", description = "Mark a notification as read by the user")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Notification marked as read successfully"),
        @SwaggerApiResponse(responseCode = "404", description = "Notification not found")
    })
    @PreAuthorize("hasRole('MOBILE_USER')")
    public ResponseEntity<ApiResponse<Void>> markNotificationAsRead(
            @PathVariable @NotNull UUID notificationId) {
        
        logger.debug("Marking notification as read: {}", notificationId);
        
        ApiResponse<Void> response = notificationService.markNotificationAsRead(notificationId);
        
        HttpStatus status = response.getSuccess() ? HttpStatus.OK : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Get notification analytics for current user
     */
    @GetMapping("/analytics")
    @Operation(summary = "Get notification analytics", description = "Get notification delivery and engagement analytics for current user")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Notification analytics retrieved successfully")
    })
    @PreAuthorize("hasRole('MOBILE_USER')")
    public ResponseEntity<ApiResponse<NotificationAnalytics>> getNotificationAnalytics(
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {
        
        if (fromDate == null) {
            fromDate = LocalDateTime.now().minusDays(30);
        }
        if (toDate == null) {
            toDate = LocalDateTime.now();
        }
        
        NotificationAnalytics analytics = notificationService.getUserNotificationAnalytics(userId, fromDate, toDate);
        
        return ResponseEntity.ok(ApiResponse.success(analytics));
    }
    
    /**
     * Get notification analytics for specific user (admin only)
     */
    @GetMapping("/user/{userId}/analytics")
    @Operation(summary = "Get user notification analytics (Admin)", description = "Get notification analytics for a specific user")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Notification analytics retrieved successfully"),
        @SwaggerApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<NotificationAnalytics>> getNotificationAnalyticsAdmin(
            @PathVariable @NotNull UUID userId,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {
        
        if (fromDate == null) {
            fromDate = LocalDateTime.now().minusDays(30);
        }
        if (toDate == null) {
            toDate = LocalDateTime.now();
        }
        
        NotificationAnalytics analytics = notificationService.getUserNotificationAnalytics(userId, fromDate, toDate);
        
        return ResponseEntity.ok(ApiResponse.success(analytics));
    }
    
    /**
     * Get notification preferences for current user
     */
    @GetMapping("/preferences")
    @Operation(summary = "Get notification preferences", description = "Get notification preferences for current user")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Notification preferences retrieved successfully")
    })
    @PreAuthorize("hasRole('MOBILE_USER')")
    public ResponseEntity<ApiResponse<NotificationPreferences>> getNotificationPreferences(
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {
        
        // This would typically fetch from a user preferences service
        // For now, return default preferences
        NotificationPreferences preferences = new NotificationPreferences(
            true,  // transactionNotifications
            true,  // securityNotifications  
            false, // marketingNotifications
            true,  // systemNotifications
            "22:00", // quietHoursStart
            "08:00"  // quietHoursEnd
        );
        
        return ResponseEntity.ok(ApiResponse.success(preferences));
    }
    
    /**
     * Update notification preferences for current user
     */
    @PutMapping("/preferences")
    @Operation(summary = "Update notification preferences", description = "Update notification preferences for current user")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Notification preferences updated successfully")
    })
    @PreAuthorize("hasRole('MOBILE_USER')")
    public ResponseEntity<ApiResponse<NotificationPreferences>> updateNotificationPreferences(
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId,
            @Valid @RequestBody NotificationPreferencesRequest request) {
        
        logger.info("Updating notification preferences for user: {}", userId);
        
        // This would typically update in a user preferences service
        // For now, return the updated preferences
        NotificationPreferences updatedPreferences = new NotificationPreferences(
            request.getTransactionNotifications(),
            request.getSecurityNotifications(),
            request.getMarketingNotifications(),
            request.getSystemNotifications(),
            request.getQuietHoursStart(),
            request.getQuietHoursEnd()
        );
        
        return ResponseEntity.ok(ApiResponse.success(updatedPreferences));
    }
    
    /**
     * Test notification delivery
     */
    @PostMapping("/test")
    @Operation(summary = "Test notification", description = "Send a test notification to verify delivery")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Test notification sent successfully")
    })
    @PreAuthorize("hasRole('MOBILE_USER')")
    public ResponseEntity<ApiResponse<PushNotificationResponse>> sendTestNotification(
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {
        
        SendNotificationRequest request = new SendNotificationRequest();
        request.setUserId(userId);
        request.setType(com.fintech.mobilesdk.domain.NotificationType.SYSTEM);
        request.setTitle("Test Notification");
        request.setBody("This is a test notification to verify your device can receive push notifications.");
        request.setSound("default");
        request.setPriority("normal");
        
        ApiResponse<PushNotificationResponse> response = notificationService.sendNotification(request);
        
        HttpStatus status = response.getSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }
    
    // Request DTOs
    public static class PushTokenUpdateRequest {
        @NotBlank(message = "Push token is required")
        private String pushToken;
        
        public String getPushToken() { return pushToken; }
        public void setPushToken(String pushToken) { this.pushToken = pushToken; }
    }
    
    public static class NotificationPreferencesRequest {
        private Boolean transactionNotifications;
        private Boolean securityNotifications;
        private Boolean marketingNotifications;
        private Boolean systemNotifications;
        private String quietHoursStart;
        private String quietHoursEnd;
        
        public Boolean getTransactionNotifications() { return transactionNotifications; }
        public void setTransactionNotifications(Boolean transactionNotifications) { this.transactionNotifications = transactionNotifications; }
        
        public Boolean getSecurityNotifications() { return securityNotifications; }
        public void setSecurityNotifications(Boolean securityNotifications) { this.securityNotifications = securityNotifications; }
        
        public Boolean getMarketingNotifications() { return marketingNotifications; }
        public void setMarketingNotifications(Boolean marketingNotifications) { this.marketingNotifications = marketingNotifications; }
        
        public Boolean getSystemNotifications() { return systemNotifications; }
        public void setSystemNotifications(Boolean systemNotifications) { this.systemNotifications = systemNotifications; }
        
        public String getQuietHoursStart() { return quietHoursStart; }
        public void setQuietHoursStart(String quietHoursStart) { this.quietHoursStart = quietHoursStart; }
        
        public String getQuietHoursEnd() { return quietHoursEnd; }
        public void setQuietHoursEnd(String quietHoursEnd) { this.quietHoursEnd = quietHoursEnd; }
    }
    
    // Response DTOs
    public static class NotificationPreferences {
        private final Boolean transactionNotifications;
        private final Boolean securityNotifications;
        private final Boolean marketingNotifications;
        private final Boolean systemNotifications;
        private final String quietHoursStart;
        private final String quietHoursEnd;
        
        public NotificationPreferences(Boolean transactionNotifications, Boolean securityNotifications,
                                     Boolean marketingNotifications, Boolean systemNotifications,
                                     String quietHoursStart, String quietHoursEnd) {
            this.transactionNotifications = transactionNotifications;
            this.securityNotifications = securityNotifications;
            this.marketingNotifications = marketingNotifications;
            this.systemNotifications = systemNotifications;
            this.quietHoursStart = quietHoursStart;
            this.quietHoursEnd = quietHoursEnd;
        }
        
        public Boolean getTransactionNotifications() { return transactionNotifications; }
        public Boolean getSecurityNotifications() { return securityNotifications; }
        public Boolean getMarketingNotifications() { return marketingNotifications; }
        public Boolean getSystemNotifications() { return systemNotifications; }
        public String getQuietHoursStart() { return quietHoursStart; }
        public String getQuietHoursEnd() { return quietHoursEnd; }
    }
}
