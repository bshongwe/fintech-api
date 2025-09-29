package com.fintech.mobilesdk.api;

import com.fintech.mobilesdk.application.MobileDeviceService;
import com.fintech.mobilesdk.application.MobileDeviceService.*;
import com.fintech.mobilesdk.domain.DeviceStatus;
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
    @PreAuthorize("hasRole('MOBILE_USER')")
    public ResponseEntity<ApiResponse<List<DeviceInfo>>> getUserDevices(
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {
        
        List<DeviceInfo> devices = deviceService.getUserDevices(userId);
        
        return ResponseEntity.ok(ApiResponse.success(devices));
    }
    
    // Additional methods would be here...
    
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
}