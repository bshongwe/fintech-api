package com.fintech.mobilesdk.api;

import com.fintech.mobilesdk.application.MobileDeviceService;
import com.fintech.mobilesdk.application.MobileDeviceService.*;
import com.fintech.mobilesdk.domain.DeviceStatus;
import com.fintech.commons.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mobile Device Management Controller
 * 
 * Provides REST API endpoints for managing mobile devices, security settings,
 * and device analytics.
 */
@RestController
@RequestMapping("/api/v1/mobile/devices")
@Tag(name = "Mobile Device Management", description = "Mobile device registration, management, and security")
@Validated
public class MobileDeviceController {
    
    private static final Logger logger = LoggerFactory.getLogger(MobileDeviceController.class);
    
    private final MobileDeviceService deviceService;
    
    @Autowired
    public MobileDeviceController(MobileDeviceService deviceService) {
        this.deviceService = deviceService;
    }
    
    /**
     * Get device information
     */
    @GetMapping("/{deviceId}")
    @Operation(summary = "Get device info", description = "Get detailed information about a mobile device")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Device information retrieved successfully"),
        @SwaggerApiResponse(responseCode = "404", description = "Device not found")
    })
    @PreAuthorize("hasRole('MOBILE_USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DeviceInfo>> getDeviceInfo(
            @PathVariable @NotBlank String deviceId) {
        
        Optional<DeviceInfo> deviceInfo = deviceService.getDeviceInfo(deviceId);
        
        if (deviceInfo.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(deviceInfo.get()));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(com.fintech.commons.ErrorResponse.builder()
                    .message("Device not found")
                    .build()));
        }
    }
    
    /**
     * Get all devices for current user
     */
    @GetMapping("/my-devices")
    @Operation(summary = "Get user devices", description = "Get all registered devices for current user")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "User devices retrieved successfully")
    })
    @PreAuthorize("hasRole('MOBILE_USER')")
    public ResponseEntity<ApiResponse<List<DeviceInfo>>> getUserDevices(
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {
        
        List<DeviceInfo> devices = deviceService.getUserDevices(userId);
        
        return ResponseEntity.ok(ApiResponse.success(devices));
    }
    
    /**
     * Get devices for specific user (admin only)
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user devices (Admin)", description = "Get all registered devices for a specific user")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "User devices retrieved successfully"),
        @SwaggerApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<DeviceInfo>>> getUserDevicesAdmin(
            @PathVariable @NotNull UUID userId) {
        
        List<DeviceInfo> devices = deviceService.getUserDevices(userId);
        
        return ResponseEntity.ok(ApiResponse.success(devices));
    }
    
    /**
     * Update device information
     */
    @PutMapping("/{deviceId}")
    @Operation(summary = "Update device", description = "Update mobile device information and settings")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Device updated successfully"),
        @SwaggerApiResponse(responseCode = "404", description = "Device not found"),
        @SwaggerApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PreAuthorize("hasRole('MOBILE_USER')")
    public ResponseEntity<ApiResponse<DeviceInfo>> updateDevice(
            @PathVariable @NotBlank String deviceId,
            @Valid @RequestBody DeviceUpdateRequest request) {
        
        logger.info("Updating device: {}", deviceId);
        
        ApiResponse<DeviceInfo> response = deviceService.updateDevice(deviceId, request);
        
        HttpStatus status = response.getSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Set device trust status
     */
    @PostMapping("/{deviceId}/trust")
    @Operation(summary = "Set device trust", description = "Trust or untrust a mobile device")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Device trust status updated successfully"),
        @SwaggerApiResponse(responseCode = "404", description = "Device not found")
    })
    @PreAuthorize("hasRole('MOBILE_USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> setDeviceTrust(
            @PathVariable @NotBlank String deviceId,
            @Valid @RequestBody DeviceTrustRequest request) {
        
        logger.info("Setting device trust: {} - {}", deviceId, request.getTrusted());
        
        ApiResponse<Void> response = deviceService.setDeviceTrust(
            deviceId, 
            request.getTrusted(), 
            request.getReason()
        );
        
        HttpStatus status = response.getSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Block or unblock device
     */
    @PostMapping("/{deviceId}/status")
    @Operation(summary = "Set device status", description = "Change device status (block/unblock/etc.)")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Device status updated successfully"),
        @SwaggerApiResponse(responseCode = "404", description = "Device not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> setDeviceStatus(
            @PathVariable @NotBlank String deviceId,
            @Valid @RequestBody DeviceStatusRequest request) {
        
        logger.info("Setting device status: {} - {}", deviceId, request.getStatus());
        
        ApiResponse<Void> response = deviceService.setDeviceStatus(
            deviceId, 
            request.getStatus(), 
            request.getReason()
        );
        
        HttpStatus status = response.getSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Remove device from account
     */
    @DeleteMapping("/{deviceId}")
    @Operation(summary = "Remove device", description = "Remove device from user account")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Device removed successfully"),
        @SwaggerApiResponse(responseCode = "404", description = "Device not found"),
        @SwaggerApiResponse(responseCode = "403", description = "Not authorized to remove this device")
    })
    @PreAuthorize("hasRole('MOBILE_USER')")
    public ResponseEntity<ApiResponse<Void>> removeDevice(
            @PathVariable @NotBlank String deviceId,
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {
        
        logger.info("Removing device: {} for user: {}", deviceId, userId);
        
        ApiResponse<Void> response = deviceService.removeDevice(deviceId, userId);
        
        HttpStatus status = response.getSuccess() ? HttpStatus.OK : 
                          response.getError().getMessage().contains("not found") ? 
                          HttpStatus.NOT_FOUND : HttpStatus.FORBIDDEN;
        
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Get device security report
     */
    @GetMapping("/{deviceId}/security-report")
    @Operation(summary = "Get security report", description = "Get comprehensive security report for a device")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Security report generated successfully"),
        @SwaggerApiResponse(responseCode = "404", description = "Device not found")
    })
    @PreAuthorize("hasRole('MOBILE_USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DeviceSecurityReport>> getDeviceSecurityReport(
            @PathVariable @NotBlank String deviceId) {
        
        DeviceSecurityReport report = deviceService.getDeviceSecurityReport(deviceId);
        
        if (report != null) {
            return ResponseEntity.ok(ApiResponse.success(report));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(com.fintech.commons.ErrorResponse.builder()
                    .message("Device not found")
                    .build()));
        }
    }
    
    /**
     * Get device analytics for current user
     */
    @GetMapping("/analytics")
    @Operation(summary = "Get device analytics", description = "Get device usage and security analytics for current user")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Device analytics retrieved successfully")
    })
    @PreAuthorize("hasRole('MOBILE_USER')")
    public ResponseEntity<ApiResponse<DeviceAnalytics>> getDeviceAnalytics(
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
        
        DeviceAnalytics analytics = deviceService.getDeviceAnalytics(userId, fromDate, toDate);
        
        return ResponseEntity.ok(ApiResponse.success(analytics));
    }
    
    /**
     * Get device analytics for specific user (admin only)
     */
    @GetMapping("/user/{userId}/analytics")
    @Operation(summary = "Get user device analytics (Admin)", description = "Get device analytics for a specific user")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Device analytics retrieved successfully"),
        @SwaggerApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DeviceAnalytics>> getDeviceAnalyticsAdmin(
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
        
        DeviceAnalytics analytics = deviceService.getDeviceAnalytics(userId, fromDate, toDate);
        
        return ResponseEntity.ok(ApiResponse.success(analytics));
    }
    
    /**
     * Bulk device operations (admin only)
     */
    @PostMapping("/bulk-operation")
    @Operation(summary = "Bulk device operation", description = "Perform bulk operations on multiple devices")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Bulk operation completed"),
        @SwaggerApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BulkOperationResult>> bulkDeviceOperation(
            @Valid @RequestBody BulkDeviceOperationRequest request) {
        
        logger.info("Bulk device operation: {} on {} devices", request.getOperation(), request.getDeviceIds().size());
        
        int successCount = 0;
        int failureCount = 0;
        
        for (String deviceId : request.getDeviceIds()) {
            try {
                switch (request.getOperation()) {
                    case BLOCK:
                        deviceService.setDeviceStatus(deviceId, DeviceStatus.BLOCKED, request.getReason());
                        break;
                    case UNBLOCK:
                        deviceService.setDeviceStatus(deviceId, DeviceStatus.ACTIVE, request.getReason());
                        break;
                    case TRUST:
                        deviceService.setDeviceTrust(deviceId, true, request.getReason());
                        break;
                    case UNTRUST:
                        deviceService.setDeviceTrust(deviceId, false, request.getReason());
                        break;
                }
                successCount++;
            } catch (Exception e) {
                logger.error("Failed bulk operation on device: {}", deviceId, e);
                failureCount++;
            }
        }
        
        BulkOperationResult result = new BulkOperationResult(
            request.getDeviceIds().size(),
            successCount,
            failureCount,
            "Bulk operation completed"
        );
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    // Request DTOs
    public static class DeviceTrustRequest {
        @NotNull(message = "Trusted status is required")
        private Boolean trusted;
        private String reason;
        
        public Boolean getTrusted() { return trusted; }
        public void setTrusted(Boolean trusted) { this.trusted = trusted; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    public static class DeviceStatusRequest {
        @NotNull(message = "Status is required")
        private DeviceStatus status;
        private String reason;
        
        public DeviceStatus getStatus() { return status; }
        public void setStatus(DeviceStatus status) { this.status = status; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    public static class BulkDeviceOperationRequest {
        @NotNull(message = "Operation is required")
        private BulkOperation operation;
        
        @NotNull(message = "Device IDs are required")
        private List<String> deviceIds;
        
        private String reason;
        
        public BulkOperation getOperation() { return operation; }
        public void setOperation(BulkOperation operation) { this.operation = operation; }
        
        public List<String> getDeviceIds() { return deviceIds; }
        public void setDeviceIds(List<String> deviceIds) { this.deviceIds = deviceIds; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    public enum BulkOperation {
        BLOCK, UNBLOCK, TRUST, UNTRUST
    }
    
    // Response DTOs
    public static class BulkOperationResult {
        private final Integer totalRequested;
        private final Integer successCount;
        private final Integer failureCount;
        private final String message;
        
        public BulkOperationResult(Integer totalRequested, Integer successCount, 
                                 Integer failureCount, String message) {
            this.totalRequested = totalRequested;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.message = message;
        }
        
        public Integer getTotalRequested() { return totalRequested; }
        public Integer getSuccessCount() { return successCount; }
        public Integer getFailureCount() { return failureCount; }
        public String getMessage() { return message; }
    }
}
