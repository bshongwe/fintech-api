package com.fintech.mobilesdk.application;

import com.fintech.mobilesdk.domain.PushNotification;
import com.fintech.mobilesdk.domain.NotificationType;
import com.fintech.mobilesdk.domain.NotificationStatus;
import com.fintech.mobilesdk.domain.DeliveryStatus;
import com.fintech.mobilesdk.infrastructure.PushNotificationRepository;
import com.fintech.mobilesdk.infrastructure.MobileDeviceRepository;
import com.fintech.mobilesdk.domain.MobileDevice;
import com.fintech.commons.ApiResponse;
import com.fintech.commons.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestTemplate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Push Notification Service
 * 
 * Handles sending push notifications to mobile devices, managing delivery status,
 * and providing analytics on notification performance.
 */
@Service
@Validated
@Transactional
public class PushNotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(PushNotificationService.class);
    
    private final PushNotificationRepository pushNotificationRepository;
    private final MobileDeviceRepository mobileDeviceRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Executor notificationExecutor;
    
    @Value("${app.push.fcm.server-key}")
    private String fcmServerKey;
    
    @Value("${app.push.fcm.url:https://fcm.googleapis.com/fcm/send}")
    private String fcmUrl;
    
    @Value("${app.push.apns.certificate-path}")
    private String apnsCertificatePath;
    
    @Value("${app.push.apns.environment:sandbox}")
    private String apnsEnvironment;
    
    @Value("${app.push.batch-size:100}")
    private int batchSize;
    
    @Value("${app.push.retry-attempts:3}")
    private int retryAttempts;
    
    @Autowired
    public PushNotificationService(PushNotificationRepository pushNotificationRepository,
                                 MobileDeviceRepository mobileDeviceRepository,
                                 KafkaTemplate<String, Object> kafkaTemplate,
                                 RestTemplate restTemplate,
                                 ObjectMapper objectMapper) {
        this.pushNotificationRepository = pushNotificationRepository;
        this.mobileDeviceRepository = mobileDeviceRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.notificationExecutor = Executors.newFixedThreadPool(10);
    }
    
    /**
     * Send push notification to a single user
     */
    public ApiResponse<PushNotificationResponse> sendNotification(@Valid SendNotificationRequest request) {
        try {
            // Get user's active devices
            List<MobileDevice> devices = mobileDeviceRepository.findActiveDevicesByUserId(request.getUserId());
            
            if (devices.isEmpty()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("No active devices found for user")
                    .build());
            }
            
            List<UUID> notificationIds = new ArrayList<>();
            
            for (MobileDevice device : devices) {
                if (device.getPushToken() != null && device.isPushEnabled()) {
                    // Create notification record
                    PushNotification notification = new PushNotification(
                        request.getUserId(),
                        device.getId(),
                        request.getType(),
                        request.getTitle(),
                        request.getBody()
                    );
                    
                    notification.setData(request.getData());
                    notification.setBadgeCount(request.getBadgeCount());
                    notification.setSound(request.getSound());
                    notification.setCategory(request.getCategory());
                    notification.setDeepLink(request.getDeepLink());
                    notification.setExpiresAt(request.getExpiresAt());
                    notification.setPriority(request.getPriority() != null ? request.getPriority() : "normal");
                    
                    PushNotification savedNotification = pushNotificationRepository.save(notification);
                    notificationIds.add(savedNotification.getId());
                    
                    // Send notification asynchronously
                    CompletableFuture.runAsync(() -> 
                        sendPushNotification(savedNotification, device), notificationExecutor);
                }
            }
            
            if (notificationIds.isEmpty()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("No push-enabled devices found")
                    .build());
            }
            
            logger.info("Push notifications queued for user: {} to {} devices", 
                       request.getUserId(), notificationIds.size());
            
            PushNotificationResponse response = new PushNotificationResponse(
                notificationIds,
                notificationIds.size(),
                "Notifications queued for delivery"
            );
            
            return ApiResponse.success(response);
            
        } catch (Exception e) {
            logger.error("Error sending push notification", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to send notification")
                .build());
        }
    }
    
    /**
     * Send bulk notifications to multiple users
     */
    public ApiResponse<BulkNotificationResponse> sendBulkNotifications(@Valid BulkNotificationRequest request) {
        try {
            List<UUID> allNotificationIds = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;
            
            for (UUID userId : request.getUserIds()) {
                try {
                    List<MobileDevice> devices = mobileDeviceRepository.findActiveDevicesByUserId(userId);
                    
                    for (MobileDevice device : devices) {
                        if (device.getPushToken() != null && device.isPushEnabled()) {
                            PushNotification notification = new PushNotification(
                                userId,
                                device.getId(),
                                request.getType(),
                                request.getTitle(),
                                request.getBody()
                            );
                            
                            notification.setData(request.getData());
                            notification.setBadgeCount(request.getBadgeCount());
                            notification.setSound(request.getSound());
                            notification.setCategory(request.getCategory());
                            notification.setDeepLink(request.getDeepLink());
                            notification.setExpiresAt(request.getExpiresAt());
                            notification.setPriority(request.getPriority() != null ? request.getPriority() : "normal");
                            
                            PushNotification savedNotification = pushNotificationRepository.save(notification);
                            allNotificationIds.add(savedNotification.getId());
                            
                            // Send notification asynchronously
                            CompletableFuture.runAsync(() -> 
                                sendPushNotification(savedNotification, device), notificationExecutor);
                            
                            successCount++;
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error processing bulk notification for user: {}", userId, e);
                    failureCount++;
                }
            }
            
            logger.info("Bulk notifications queued: {} successful, {} failed", successCount, failureCount);
            
            BulkNotificationResponse response = new BulkNotificationResponse(
                allNotificationIds,
                successCount,
                failureCount,
                "Bulk notifications queued for delivery"
            );
            
            return ApiResponse.success(response);
            
        } catch (Exception e) {
            logger.error("Error sending bulk notifications", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to send bulk notifications")
                .build());
        }
    }
    
    /**
     * Send broadcast notification to all active users
     */
    public ApiResponse<BroadcastNotificationResponse> sendBroadcastNotification(@Valid BroadcastNotificationRequest request) {
        try {
            // Get all active devices with push tokens
            List<MobileDevice> devices = mobileDeviceRepository.findDevicesWithPushTokens();
            
            if (devices.isEmpty()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("No devices with push tokens found")
                    .build());
            }
            
            List<UUID> notificationIds = new ArrayList<>();
            int processedCount = 0;
            
            // Process devices in batches
            for (int i = 0; i < devices.size(); i += batchSize) {
                List<MobileDevice> batch = devices.subList(i, Math.min(i + batchSize, devices.size()));
                
                for (MobileDevice device : batch) {
                    try {
                        PushNotification notification = new PushNotification(
                            device.getUserId(),
                            device.getId(),
                            request.getType(),
                            request.getTitle(),
                            request.getBody()
                        );
                        
                        notification.setData(request.getData());
                        notification.setBadgeCount(request.getBadgeCount());
                        notification.setSound(request.getSound());
                        notification.setCategory(request.getCategory());
                        notification.setDeepLink(request.getDeepLink());
                        notification.setExpiresAt(request.getExpiresAt());
                        notification.setPriority(request.getPriority() != null ? request.getPriority() : "high");
                        
                        PushNotification savedNotification = pushNotificationRepository.save(notification);
                        notificationIds.add(savedNotification.getId());
                        
                        // Send notification asynchronously
                        CompletableFuture.runAsync(() -> 
                            sendPushNotification(savedNotification, device), notificationExecutor);
                        
                        processedCount++;
                        
                    } catch (Exception e) {
                        logger.error("Error processing broadcast notification for device: {}", device.getId(), e);
                    }
                }
                
                // Small delay between batches to avoid overwhelming the system
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            logger.info("Broadcast notification queued for {} devices", processedCount);
            
            BroadcastNotificationResponse response = new BroadcastNotificationResponse(
                notificationIds,
                processedCount,
                devices.size(),
                "Broadcast notification queued for delivery"
            );
            
            return ApiResponse.success(response);
            
        } catch (Exception e) {
            logger.error("Error sending broadcast notification", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to send broadcast notification")
                .build());
        }
    }
    
    /**
     * Get notification status
     */
    @Cacheable(value = "notificationStatus", key = "#notificationId")
    public Optional<NotificationStatusInfo> getNotificationStatus(@NotNull UUID notificationId) {
        return pushNotificationRepository.findById(notificationId)
            .map(notification -> new NotificationStatusInfo(
                notification.getId(),
                notification.getStatus(),
                notification.getDeliveryStatus(),
                notification.getSentAt(),
                notification.getDeliveredAt(),
                notification.getReadAt(),
                notification.getFailureReason(),
                notification.getRetryCount()
            ));
    }
    
    /**
     * Get notification analytics for a user
     */
    public NotificationAnalytics getUserNotificationAnalytics(@NotNull UUID userId, 
                                                            LocalDateTime fromDate, 
                                                            LocalDateTime toDate) {
        List<Object[]> stats = pushNotificationRepository.getUserNotificationStats(userId, fromDate, toDate);
        
        Map<String, Long> statusCounts = new HashMap<>();
        Map<String, Long> typeCounts = new HashMap<>();
        
        for (Object[] row : stats) {
            String status = (String) row[0];
            String type = (String) row[1];
            Long count = (Long) row[2];
            
            statusCounts.put(status, statusCounts.getOrDefault(status, 0L) + count);
            typeCounts.put(type, typeCounts.getOrDefault(type, 0L) + count);
        }
        
        long totalSent = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long delivered = statusCounts.getOrDefault("DELIVERED", 0L);
        long read = statusCounts.getOrDefault("READ", 0L);
        long failed = statusCounts.getOrDefault("FAILED", 0L);
        
        double deliveryRate = totalSent > 0 ? (double) delivered / totalSent * 100 : 0.0;
        double readRate = delivered > 0 ? (double) read / delivered * 100 : 0.0;
        double failureRate = totalSent > 0 ? (double) failed / totalSent * 100 : 0.0;
        
        return new NotificationAnalytics(
            totalSent,
            delivered,
            read,
            failed,
            deliveryRate,
            readRate,
            failureRate,
            statusCounts,
            typeCounts
        );
    }
    
    /**
     * Update device push token
     */
    public ApiResponse<Void> updatePushToken(@NotBlank String deviceId, @NotBlank String pushToken) {
        try {
            Optional<MobileDevice> optionalDevice = mobileDeviceRepository.findByDeviceId(deviceId);
            
            if (optionalDevice.isEmpty()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Device not found")
                    .build());
            }
            
            MobileDevice device = optionalDevice.get();
            device.setPushToken(pushToken);
            device.setPushEnabled(true);
            device.updateActivity(null, null);
            
            mobileDeviceRepository.save(device);
            
            logger.info("Push token updated for device: {}", deviceId);
            
            return ApiResponse.success(null);
            
        } catch (Exception e) {
            logger.error("Error updating push token", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to update push token")
                .build());
        }
    }
    
    /**
     * Handle notification read receipt
     */
    public ApiResponse<Void> markNotificationAsRead(@NotNull UUID notificationId) {
        try {
            Optional<PushNotification> optionalNotification = pushNotificationRepository.findById(notificationId);
            
            if (optionalNotification.isEmpty()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Notification not found")
                    .build());
            }
            
            PushNotification notification = optionalNotification.get();
            notification.markAsRead();
            
            pushNotificationRepository.save(notification);
            
            // Publish read event
            publishNotificationEvent("NOTIFICATION_READ", notification);
            
            logger.debug("Notification marked as read: {}", notificationId);
            
            return ApiResponse.success(null);
            
        } catch (Exception e) {
            logger.error("Error marking notification as read", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to mark notification as read")
                .build());
        }
    }
    
    /**
     * Listen to payment events and send relevant notifications
     */
    @KafkaListener(topics = "payment-events")
    public void handlePaymentEvent(PaymentEvent event) {
        try {
            String title = "";
            String body = "";
            Map<String, String> data = new HashMap<>();
            data.put("paymentId", event.getPaymentId().toString());
            data.put("amount", event.getAmount().toString());
            
            switch (event.getEventType()) {
                case "PAYMENT_INITIATED":
                    title = "Payment Initiated";
                    body = String.format("Your payment of %s %s has been initiated", 
                                       event.getAmount(), event.getCurrency());
                    break;
                case "PAYMENT_COMPLETED":
                    title = "Payment Successful";
                    body = String.format("Your payment of %s %s was successful", 
                                       event.getAmount(), event.getCurrency());
                    break;
                case "PAYMENT_FAILED":
                    title = "Payment Failed";
                    body = String.format("Your payment of %s %s failed. Please try again", 
                                       event.getAmount(), event.getCurrency());
                    break;
                default:
                    return; // Don't send notification for other events
            }
            
            SendNotificationRequest request = new SendNotificationRequest();
            request.setUserId(event.getUserId());
            request.setType(NotificationType.TRANSACTION);
            request.setTitle(title);
            request.setBody(body);
            request.setData(data);
            request.setSound("default");
            request.setPriority("high");
            
            sendNotification(request);
            
        } catch (Exception e) {
            logger.error("Error handling payment event", e);
        }
    }
    
    /**
     * Listen to fraud detection alerts and send notifications
     */
    @KafkaListener(topics = "fraud-alerts")
    public void handleFraudAlert(FraudAlert alert) {
        try {
            String title = "Security Alert";
            String body = String.format("Suspicious activity detected on your account: %s", 
                                      alert.getReason());
            
            Map<String, String> data = new HashMap<>();
            data.put("alertId", alert.getAlertId().toString());
            data.put("riskScore", alert.getRiskScore().toString());
            data.put("transactionId", alert.getTransactionId() != null ? alert.getTransactionId().toString() : "");
            
            SendNotificationRequest request = new SendNotificationRequest();
            request.setUserId(alert.getUserId());
            request.setType(NotificationType.SECURITY);
            request.setTitle(title);
            request.setBody(body);
            request.setData(data);
            request.setSound("alert");
            request.setPriority("high");
            request.setCategory("SECURITY_ALERT");
            
            sendNotification(request);
            
        } catch (Exception e) {
            logger.error("Error handling fraud alert", e);
        }
    }
    
    private void sendPushNotification(PushNotification notification, MobileDevice device) {
        try {
            notification.markAsSending();
            pushNotificationRepository.save(notification);
            
            boolean success = false;
            String failureReason = null;
            
            switch (device.getDeviceType()) {
                case ANDROID:
                    success = sendFcmNotification(notification, device);
                    break;
                case IOS:
                    success = sendApnsNotification(notification, device);
                    break;
                default:
                    failureReason = "Unsupported device type";
            }
            
            if (success) {
                notification.markAsDelivered();
                publishNotificationEvent("NOTIFICATION_SENT", notification);
            } else {
                notification.markAsFailed(failureReason != null ? failureReason : "Unknown error");
                
                // Retry logic
                if (notification.getRetryCount() < retryAttempts) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(5000 * (notification.getRetryCount() + 1)); // Exponential backoff
                            sendPushNotification(notification, device);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }, notificationExecutor);
                }
            }
            
            pushNotificationRepository.save(notification);
            
        } catch (Exception e) {
            logger.error("Error sending push notification", e);
            notification.markAsFailed(e.getMessage());
            pushNotificationRepository.save(notification);
        }
    }
    
    private boolean sendFcmNotification(PushNotification notification, MobileDevice device) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("to", device.getPushToken());
            
            Map<String, Object> notificationPayload = new HashMap<>();
            notificationPayload.put("title", notification.getTitle());
            notificationPayload.put("body", notification.getBody());
            if (notification.getSound() != null) {
                notificationPayload.put("sound", notification.getSound());
            }
            
            message.put("notification", notificationPayload);
            
            if (notification.getData() != null) {
                message.put("data", notification.getData());
            }
            
            Map<String, Object> androidConfig = new HashMap<>();
            androidConfig.put("priority", "high".equals(notification.getPriority()) ? "high" : "normal");
            message.put("android", androidConfig);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "key=" + fcmServerKey);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(fcmUrl, request, Map.class);
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            logger.error("Error sending FCM notification", e);
            return false;
        }
    }
    
    private boolean sendApnsNotification(PushNotification notification, MobileDevice device) {
        try {
            // APNS implementation would go here
            // This would typically use a library like java-apns or pushy
            // For now, return true as placeholder
            
            logger.info("APNS notification sent (placeholder implementation)");
            return true;
            
        } catch (Exception e) {
            logger.error("Error sending APNS notification", e);
            return false;
        }
    }
    
    private void publishNotificationEvent(String eventType, PushNotification notification) {
        try {
            NotificationEvent event = new NotificationEvent(
                eventType,
                notification.getId(),
                notification.getUserId(),
                notification.getDeviceId(),
                notification.getType(),
                notification.getStatus(),
                notification.getDeliveryStatus()
            );
            kafkaTemplate.send("push-notification-events", event);
        } catch (Exception e) {
            logger.error("Failed to publish notification event", e);
        }
    }
    
    // Request/Response DTOs
    public static class SendNotificationRequest {
        private UUID userId;
        private NotificationType type;
        private String title;
        private String body;
        private Map<String, String> data;
        private Integer badgeCount;
        private String sound;
        private String category;
        private String deepLink;
        private LocalDateTime expiresAt;
        private String priority;
        
        // Getters and setters
        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        
        public NotificationType getType() { return type; }
        public void setType(NotificationType type) { this.type = type; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        
        public Map<String, String> getData() { return data; }
        public void setData(Map<String, String> data) { this.data = data; }
        
        public Integer getBadgeCount() { return badgeCount; }
        public void setBadgeCount(Integer badgeCount) { this.badgeCount = badgeCount; }
        
        public String getSound() { return sound; }
        public void setSound(String sound) { this.sound = sound; }
        
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        
        public String getDeepLink() { return deepLink; }
        public void setDeepLink(String deepLink) { this.deepLink = deepLink; }
        
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
        
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
    }
    
    public static class BulkNotificationRequest extends SendNotificationRequest {
        private List<UUID> userIds;
        
        public List<UUID> getUserIds() { return userIds; }
        public void setUserIds(List<UUID> userIds) { this.userIds = userIds; }
    }
    
    public static class BroadcastNotificationRequest {
        private NotificationType type;
        private String title;
        private String body;
        private Map<String, String> data;
        private Integer badgeCount;
        private String sound;
        private String category;
        private String deepLink;
        private LocalDateTime expiresAt;
        private String priority;
        
        // Getters and setters
        public NotificationType getType() { return type; }
        public void setType(NotificationType type) { this.type = type; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        
        public Map<String, String> getData() { return data; }
        public void setData(Map<String, String> data) { this.data = data; }
        
        public Integer getBadgeCount() { return badgeCount; }
        public void setBadgeCount(Integer badgeCount) { this.badgeCount = badgeCount; }
        
        public String getSound() { return sound; }
        public void setSound(String sound) { this.sound = sound; }
        
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        
        public String getDeepLink() { return deepLink; }
        public void setDeepLink(String deepLink) { this.deepLink = deepLink; }
        
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
        
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
    }
    
    public static class PushNotificationResponse {
        private final List<UUID> notificationIds;
        private final Integer count;
        private final String message;
        
        public PushNotificationResponse(List<UUID> notificationIds, Integer count, String message) {
            this.notificationIds = notificationIds;
            this.count = count;
            this.message = message;
        }
        
        // Getters
        public List<UUID> getNotificationIds() { return notificationIds; }
        public Integer getCount() { return count; }
        public String getMessage() { return message; }
    }
    
    public static class BulkNotificationResponse {
        private final List<UUID> notificationIds;
        private final Integer successCount;
        private final Integer failureCount;
        private final String message;
        
        public BulkNotificationResponse(List<UUID> notificationIds, Integer successCount, 
                                      Integer failureCount, String message) {
            this.notificationIds = notificationIds;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.message = message;
        }
        
        // Getters
        public List<UUID> getNotificationIds() { return notificationIds; }
        public Integer getSuccessCount() { return successCount; }
        public Integer getFailureCount() { return failureCount; }
        public String getMessage() { return message; }
    }
    
    public static class BroadcastNotificationResponse {
        private final List<UUID> notificationIds;
        private final Integer processedCount;
        private final Integer totalDevices;
        private final String message;
        
        public BroadcastNotificationResponse(List<UUID> notificationIds, Integer processedCount,
                                           Integer totalDevices, String message) {
            this.notificationIds = notificationIds;
            this.processedCount = processedCount;
            this.totalDevices = totalDevices;
            this.message = message;
        }
        
        // Getters
        public List<UUID> getNotificationIds() { return notificationIds; }
        public Integer getProcessedCount() { return processedCount; }
        public Integer getTotalDevices() { return totalDevices; }
        public String getMessage() { return message; }
    }
    
    public static class NotificationStatusInfo {
        private final UUID notificationId;
        private final NotificationStatus status;
        private final DeliveryStatus deliveryStatus;
        private final LocalDateTime sentAt;
        private final LocalDateTime deliveredAt;
        private final LocalDateTime readAt;
        private final String failureReason;
        private final Integer retryCount;
        
        public NotificationStatusInfo(UUID notificationId, NotificationStatus status, 
                                    DeliveryStatus deliveryStatus, LocalDateTime sentAt,
                                    LocalDateTime deliveredAt, LocalDateTime readAt,
                                    String failureReason, Integer retryCount) {
            this.notificationId = notificationId;
            this.status = status;
            this.deliveryStatus = deliveryStatus;
            this.sentAt = sentAt;
            this.deliveredAt = deliveredAt;
            this.readAt = readAt;
            this.failureReason = failureReason;
            this.retryCount = retryCount;
        }
        
        // Getters
        public UUID getNotificationId() { return notificationId; }
        public NotificationStatus getStatus() { return status; }
        public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
        public LocalDateTime getSentAt() { return sentAt; }
        public LocalDateTime getDeliveredAt() { return deliveredAt; }
        public LocalDateTime getReadAt() { return readAt; }
        public String getFailureReason() { return failureReason; }
        public Integer getRetryCount() { return retryCount; }
    }
    
    public static class NotificationAnalytics {
        private final Long totalSent;
        private final Long delivered;
        private final Long read;
        private final Long failed;
        private final Double deliveryRate;
        private final Double readRate;
        private final Double failureRate;
        private final Map<String, Long> statusBreakdown;
        private final Map<String, Long> typeBreakdown;
        
        public NotificationAnalytics(Long totalSent, Long delivered, Long read, Long failed,
                                   Double deliveryRate, Double readRate, Double failureRate,
                                   Map<String, Long> statusBreakdown, Map<String, Long> typeBreakdown) {
            this.totalSent = totalSent;
            this.delivered = delivered;
            this.read = read;
            this.failed = failed;
            this.deliveryRate = deliveryRate;
            this.readRate = readRate;
            this.failureRate = failureRate;
            this.statusBreakdown = statusBreakdown;
            this.typeBreakdown = typeBreakdown;
        }
        
        // Getters
        public Long getTotalSent() { return totalSent; }
        public Long getDelivered() { return delivered; }
        public Long getRead() { return read; }
        public Long getFailed() { return failed; }
        public Double getDeliveryRate() { return deliveryRate; }
        public Double getReadRate() { return readRate; }
        public Double getFailureRate() { return failureRate; }
        public Map<String, Long> getStatusBreakdown() { return statusBreakdown; }
        public Map<String, Long> getTypeBreakdown() { return typeBreakdown; }
    }
    
    // Event DTOs
    public static class PaymentEvent {
        private String eventType;
        private UUID paymentId;
        private UUID userId;
        private java.math.BigDecimal amount;
        private String currency;
        
        // Getters and setters
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        
        public UUID getPaymentId() { return paymentId; }
        public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
        
        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        
        public java.math.BigDecimal getAmount() { return amount; }
        public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
    }
    
    public static class FraudAlert {
        private UUID alertId;
        private UUID userId;
        private UUID transactionId;
        private String reason;
        private Double riskScore;
        
        // Getters and setters
        public UUID getAlertId() { return alertId; }
        public void setAlertId(UUID alertId) { this.alertId = alertId; }
        
        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        
        public UUID getTransactionId() { return transactionId; }
        public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public Double getRiskScore() { return riskScore; }
        public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }
    }
    
    public static class NotificationEvent {
        private final String eventType;
        private final UUID notificationId;
        private final UUID userId;
        private final UUID deviceId;
        private final NotificationType type;
        private final NotificationStatus status;
        private final DeliveryStatus deliveryStatus;
        private final LocalDateTime timestamp;
        
        public NotificationEvent(String eventType, UUID notificationId, UUID userId, UUID deviceId,
                               NotificationType type, NotificationStatus status, DeliveryStatus deliveryStatus) {
            this.eventType = eventType;
            this.notificationId = notificationId;
            this.userId = userId;
            this.deviceId = deviceId;
            this.type = type;
            this.status = status;
            this.deliveryStatus = deliveryStatus;
            this.timestamp = LocalDateTime.now();
        }
        
        // Getters
        public String getEventType() { return eventType; }
        public UUID getNotificationId() { return notificationId; }
        public UUID getUserId() { return userId; }
        public UUID getDeviceId() { return deviceId; }
        public NotificationType getType() { return type; }
        public NotificationStatus getStatus() { return status; }
        public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}
