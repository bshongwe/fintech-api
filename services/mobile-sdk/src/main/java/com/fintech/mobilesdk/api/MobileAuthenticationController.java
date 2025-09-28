package com.fintech.mobilesdk.api;

import com.fintech.mobilesdk.application.MobileAuthenticationService;
import com.fintech.mobilesdk.application.MobileAuthenticationService.*;
import com.fintech.commons.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mobile Authentication Controller
 * 
 * Provides REST API endpoints for mobile device registration, user authentication,
 * session management, and token operations.
 */
@RestController
@RequestMapping("/api/v1/mobile/auth")
@Tag(name = "Mobile Authentication", description = "Mobile device authentication and session management")
@Validated
public class MobileAuthenticationController {
    
    private static final Logger logger = LoggerFactory.getLogger(MobileAuthenticationController.class);
    
    private final MobileAuthenticationService authenticationService;
    
    @Autowired
    public MobileAuthenticationController(MobileAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }
    
    /**
     * Register a new mobile device
     */
    @PostMapping("/devices/register")
    @Operation(summary = "Register mobile device", description = "Register a new mobile device for a user")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Device registered successfully"),
        @SwaggerApiResponse(responseCode = "400", description = "Invalid request"),
        @SwaggerApiResponse(responseCode = "409", description = "Device already registered")
    })
    public ResponseEntity<ApiResponse<DeviceRegistrationResponse>> registerDevice(
            @Valid @RequestBody DeviceRegistrationRequest request,
            HttpServletRequest httpRequest) {
        
        // Enrich request with IP address
        String clientIp = getClientIpAddress(httpRequest);
        request.setIpAddress(clientIp);
        
        logger.info("Device registration request from IP: {} for user: {}", clientIp, request.getUserId());
        
        ApiResponse<DeviceRegistrationResponse> response = authenticationService.registerDevice(request);
        
        HttpStatus status = response.getSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Authenticate user and create session
     */
    @PostMapping("/authenticate")
    @Operation(summary = "Authenticate user", description = "Authenticate user and create mobile session")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Authentication successful"),
        @SwaggerApiResponse(responseCode = "401", description = "Authentication failed"),
        @SwaggerApiResponse(responseCode = "403", description = "Device not authorized")
    })
    public ResponseEntity<ApiResponse<AuthenticationResponse>> authenticateUser(
            @Valid @RequestBody AuthenticationRequest request,
            HttpServletRequest httpRequest) {
        
        // Enrich request with client information
        String clientIp = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        
        request.setIpAddress(clientIp);
        request.setUserAgent(userAgent);
        
        logger.info("Authentication request from IP: {} for user: {} on device: {}", 
                   clientIp, request.getUserId(), request.getDeviceId());
        
        ApiResponse<AuthenticationResponse> response = authenticationService.authenticateUser(request);
        
        if (response.getSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            HttpStatus status = response.getError().getMessage().contains("not registered") ? 
                              HttpStatus.NOT_FOUND : HttpStatus.UNAUTHORIZED;
            return ResponseEntity.status(status).body(response);
        }
    }
    
    /**
     * Refresh session token
     */
    @PostMapping("/token/refresh")
    @Operation(summary = "Refresh token", description = "Refresh session token using refresh token")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Token refreshed successfully"),
        @SwaggerApiResponse(responseCode = "401", description = "Invalid refresh token"),
        @SwaggerApiResponse(responseCode = "403", description = "Token cannot be refreshed")
    })
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refreshToken(
            @RequestBody RefreshTokenRequest request) {
        
        logger.debug("Token refresh request");
        
        ApiResponse<TokenRefreshResponse> response = authenticationService.refreshToken(request.getRefreshToken());
        
        HttpStatus status = response.getSuccess() ? HttpStatus.OK : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Validate session token
     */
    @GetMapping("/session/validate")
    @Operation(summary = "Validate session", description = "Validate current session token")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Session is valid"),
        @SwaggerApiResponse(responseCode = "401", description = "Invalid session")
    })
    @PreAuthorize("hasRole('MOBILE_USER')")
    public ResponseEntity<ApiResponse<SessionValidationResult>> validateSession(
            @RequestHeader("Authorization") String authHeader) {
        
        String token = extractTokenFromHeader(authHeader);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(com.fintech.commons.ErrorResponse.builder()
                    .message("Invalid authorization header")
                    .build()));
        }
        
        Optional<SessionValidationResult> result = authenticationService.validateSession(token);
        
        if (result.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(result.get()));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(com.fintech.commons.ErrorResponse.builder()
                    .message("Invalid session")
                    .build()));
        }
    }
    
    /**
     * Terminate current session
     */
    @PostMapping("/session/terminate")
    @Operation(summary = "Terminate session", description = "Terminate current user session")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Session terminated successfully"),
        @SwaggerApiResponse(responseCode = "401", description = "Invalid session")
    })
    @PreAuthorize("hasRole('MOBILE_USER')")
    public ResponseEntity<ApiResponse<Void>> terminateSession(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) SessionTerminationRequest request) {
        
        String token = extractTokenFromHeader(authHeader);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(com.fintech.commons.ErrorResponse.builder()
                    .message("Invalid authorization header")
                    .build()));
        }
        
        String reason = request != null ? request.getReason() : "User logout";
        
        ApiResponse<Void> response = authenticationService.terminateSession(token, reason);
        
        HttpStatus status = response.getSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Terminate all user sessions
     */
    @PostMapping("/sessions/terminate-all")
    @Operation(summary = "Terminate all sessions", description = "Terminate all sessions for current user")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "All sessions terminated successfully"),
        @SwaggerApiResponse(responseCode = "400", description = "Failed to terminate sessions")
    })
    @PreAuthorize("hasRole('MOBILE_USER')")
    public ResponseEntity<ApiResponse<Void>> terminateAllUserSessions(
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId,
            @RequestBody(required = false) SessionTerminationRequest request) {
        
        String reason = request != null ? request.getReason() : "User action - terminate all sessions";
        
        logger.info("Terminating all sessions for user: {}", userId);
        
        ApiResponse<Void> response = authenticationService.terminateAllUserSessions(userId, reason);
        
        HttpStatus status = response.getSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Get active sessions for current user
     */
    @GetMapping("/sessions/active")
    @Operation(summary = "Get active sessions", description = "Get all active sessions for current user")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Active sessions retrieved successfully")
    })
    @PreAuthorize("hasRole('MOBILE_USER')")
    public ResponseEntity<ApiResponse<List<ActiveSessionInfo>>> getActiveSessions(
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {
        
        List<ActiveSessionInfo> sessions = authenticationService.getActiveUserSessions(userId);
        
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }
    
    /**
     * Perform step-up authentication
     */
    @PostMapping("/step-up")
    @Operation(summary = "Step-up authentication", description = "Perform additional authentication for high-security operations")
    @ApiResponses(value = {
        @SwaggerApiResponse(responseCode = "200", description = "Step-up authentication successful"),
        @SwaggerApiResponse(responseCode = "401", description = "Step-up authentication failed")
    })
    @PreAuthorize("hasRole('MOBILE_USER')")
    public ResponseEntity<ApiResponse<StepUpResponse>> stepUpAuthentication(
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId,
            @Parameter(hidden = true) @RequestAttribute("deviceId") String deviceId,
            @Valid @RequestBody StepUpRequest request,
            HttpServletRequest httpRequest) {
        
        // Create authentication request for step-up
        AuthenticationRequest authRequest = new AuthenticationRequest();
        authRequest.setUserId(userId);
        authRequest.setDeviceId(deviceId);
        authRequest.setLoginMethod(request.getAuthenticationMethod());
        authRequest.setMfaToken(request.getMfaToken());
        authRequest.setIpAddress(getClientIpAddress(httpRequest));
        authRequest.setUserAgent(httpRequest.getHeader("User-Agent"));
        
        ApiResponse<AuthenticationResponse> authResponse = authenticationService.authenticateUser(authRequest);
        
        if (authResponse.getSuccess()) {
            StepUpResponse stepUpResponse = new StepUpResponse(
                authResponse.getData().getSessionToken(),
                authResponse.getData().getSecurityLevel(),
                true
            );
            return ResponseEntity.ok(ApiResponse.success(stepUpResponse));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(authResponse.getError()));
        }
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    private String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
    
    // Request DTOs
    public static class RefreshTokenRequest {
        @NotBlank(message = "Refresh token is required")
        private String refreshToken;
        
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }
    
    public static class SessionTerminationRequest {
        private String reason;
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    public static class StepUpRequest {
        @NotBlank(message = "Authentication method is required")
        private String authenticationMethod;
        private String mfaToken;
        private String biometricData;
        
        public String getAuthenticationMethod() { return authenticationMethod; }
        public void setAuthenticationMethod(String authenticationMethod) { this.authenticationMethod = authenticationMethod; }
        
        public String getMfaToken() { return mfaToken; }
        public void setMfaToken(String mfaToken) { this.mfaToken = mfaToken; }
        
        public String getBiometricData() { return biometricData; }
        public void setBiometricData(String biometricData) { this.biometricData = biometricData; }
    }
    
    // Response DTOs
    public static class StepUpResponse {
        private final String elevatedToken;
        private final Integer securityLevel;
        private final Boolean success;
        
        public StepUpResponse(String elevatedToken, Integer securityLevel, Boolean success) {
            this.elevatedToken = elevatedToken;
            this.securityLevel = securityLevel;
            this.success = success;
        }
        
        public String getElevatedToken() { return elevatedToken; }
        public Integer getSecurityLevel() { return securityLevel; }
        public Boolean getSuccess() { return success; }
    }
}
