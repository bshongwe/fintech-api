package com.fintech.mobilesdk.application;

import com.fintech.mobilesdk.domain.MobileDevice;
import com.fintech.mobilesdk.domain.MobileSession;
import com.fintech.mobilesdk.domain.DeviceType;
import com.fintech.mobilesdk.domain.DeviceStatus;
import com.fintech.mobilesdk.infrastructure.MobileDeviceRepository;
import com.fintech.mobilesdk.infrastructure.MobileSessionRepository;
import com.fintech.commons.ApiResponse;
import com.fintech.commons.ErrorResponse;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.security.interfaces.RSAPrivateKey;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mobile Authentication Service
 * 
 * Handles secure mobile authentication, device registration, session management,
 * and token generation for mobile applications.
 */
@Service
@Validated
@Transactional
public class MobileAuthenticationService {
    
    private static final Logger logger = LoggerFactory.getLogger(MobileAuthenticationService.class);
    
    private final MobileDeviceRepository mobileDeviceRepository;
    private final MobileSessionRepository mobileSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RSAPrivateKey jwtSigningKey;
    
    @Value("${app.mobile.session-duration-minutes:480}")
    private int sessionDurationMinutes;
    
    @Value("${app.mobile.refresh-token-duration-days:30}")
    private int refreshTokenDurationDays;
    
    @Value("${app.mobile.max-devices-per-user:5}")
    private int maxDevicesPerUser;
    
    @Autowired
    public MobileAuthenticationService(MobileDeviceRepository mobileDeviceRepository,
                                     MobileSessionRepository mobileSessionRepository,
                                     PasswordEncoder passwordEncoder,
                                     KafkaTemplate<String, Object> kafkaTemplate,
                                     RSAPrivateKey jwtSigningKey) {
        this.mobileDeviceRepository = mobileDeviceRepository;
        this.mobileSessionRepository = mobileSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.kafkaTemplate = kafkaTemplate;
        this.jwtSigningKey = jwtSigningKey;
    }
    
    /**
     * Register new mobile device
     */
    public ApiResponse<DeviceRegistrationResponse> registerDevice(@Valid DeviceRegistrationRequest request) {
        try {
            // Check if device already exists
            Optional<MobileDevice> existingDevice = mobileDeviceRepository.findByDeviceId(request.getDeviceId());
            if (existingDevice.isPresent()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Device already registered")
                    .field("deviceId")
                    .build());
            }
            
            // Check device limit per user
            long userDeviceCount = mobileDeviceRepository.countByUserIdAndStatus(
                request.getUserId(), DeviceStatus.ACTIVE);
            
            if (userDeviceCount >= maxDevicesPerUser) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Maximum device limit reached")
                    .build());
            }
            
            // Create new device
            MobileDevice device = new MobileDevice(
                request.getDeviceId(),
                request.getUserId(),
                request.getDeviceType(),
                request.getDeviceName()
            );
            
            device.setOperatingSystem(request.getOperatingSystem());
            device.setOsVersion(request.getOsVersion());
            device.setAppVersion(request.getAppVersion());
            device.setPushToken(request.getPushToken());
            device.setDeviceFingerprint(request.getDeviceFingerprint());
            device.setRegistrationIp(request.getIpAddress());
            device.setRegistrationLocation(request.getLocation());
            
            // Set initial security attributes
            device.setBiometricEnabled(request.getBiometricEnabled());
            device.setPinEnabled(request.getPinEnabled());
            device.setLocationEnabled(request.getLocationEnabled());
            device.setIsRootedOrJailbroken(detectRootedOrJailbroken(request));
            
            // Calculate initial risk score
            double riskScore = calculateInitialRiskScore(device, request);
            device.setRiskScore(riskScore);
            
            // Set initial trust level based on risk
            device.setIsTrusted(riskScore < 5.0);
            
            // Set status based on risk assessment
            if (riskScore > 8.0) {
                device.setStatus(DeviceStatus.PENDING_VERIFICATION);
            } else {
                device.setStatus(DeviceStatus.ACTIVE);
            }
            
            MobileDevice savedDevice = mobileDeviceRepository.save(device);
            
            // Publish device registration event
            publishDeviceEvent("DEVICE_REGISTERED", savedDevice);
            
            logger.info("Device registered: {} for user: {}", savedDevice.getDeviceId(), savedDevice.getUserId());
            
            DeviceRegistrationResponse response = new DeviceRegistrationResponse(
                savedDevice.getId(),
                savedDevice.getDeviceId(),
                savedDevice.getStatus(),
                savedDevice.getIsTrusted(),
                savedDevice.getRiskScore()
            );
            
            return ApiResponse.success(response);
            
        } catch (Exception e) {
            logger.error("Error registering device", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to register device")
                .build());
        }
    }
    
    /**
     * Authenticate user and create session
     */
    public ApiResponse<AuthenticationResponse> authenticateUser(@Valid AuthenticationRequest request) {
        try {
            // Verify device exists and is active
            Optional<MobileDevice> optionalDevice = mobileDeviceRepository.findByDeviceId(request.getDeviceId());
            if (optionalDevice.isEmpty()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Device not registered")
                    .build());
            }
            
            MobileDevice device = optionalDevice.get();
            
            if (!device.isActive()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Device is not active")
                    .build());
            }
            
            // Update device activity
            device.updateActivity(request.getIpAddress(), request.getLocation());
            
            // Calculate session risk score
            double sessionRiskScore = calculateSessionRiskScore(device, request);
            
            // Create session
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(sessionDurationMinutes);
            LocalDateTime refreshExpiresAt = LocalDateTime.now().plusDays(refreshTokenDurationDays);
            
            String sessionToken = generateSessionToken(request.getUserId(), device.getId());
            String refreshToken = generateRefreshToken(request.getUserId(), device.getId());
            
            MobileSession session = new MobileSession(sessionToken, request.getUserId(), device.getId(), expiresAt);
            session.setRefreshToken(refreshToken);
            session.setRefreshExpiresAt(refreshExpiresAt);
            session.setLoginMethod(request.getLoginMethod());
            session.setIpAddress(request.getIpAddress());
            session.setLocation(request.getLocation());
            session.setUserAgent(request.getUserAgent());
            session.setRiskScore(sessionRiskScore);
            
            // Set security level based on authentication method
            int securityLevel = determineSecurityLevel(request, device);
            session.setSecurityLevel(securityLevel);
            
            // Mark MFA verification if provided
            if (request.getMfaToken() != null) {
                session.setMfaVerified(true);
                session.setSecurityLevel(Math.max(securityLevel, 2));
            }
            
            // Mark biometric verification if used
            if ("BIOMETRIC".equals(request.getLoginMethod())) {
                session.setBiometricVerified(true);
                session.setSecurityLevel(Math.max(securityLevel, 2));
            }
            
            MobileSession savedSession = mobileSessionRepository.save(session);
            mobileDeviceRepository.save(device);
            
            // Publish authentication event
            publishAuthenticationEvent("USER_AUTHENTICATED", savedSession);
            
            logger.info("User authenticated: {} on device: {}", request.getUserId(), device.getDeviceId());
            
            AuthenticationResponse response = new AuthenticationResponse(
                savedSession.getSessionToken(),
                savedSession.getRefreshToken(),
                savedSession.getExpiresAt(),
                savedSession.getSecurityLevel(),
                device.getIsTrusted(),
                sessionRiskScore
            );
            
            return ApiResponse.success(response);
            
        } catch (Exception e) {
            logger.error("Error authenticating user", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Authentication failed")
                .build());
        }
    }
    
    /**
     * Refresh session token
     */
    public ApiResponse<TokenRefreshResponse> refreshToken(@NotBlank String refreshToken) {
        try {
            Optional<MobileSession> optionalSession = mobileSessionRepository.findByRefreshToken(refreshToken);
            if (optionalSession.isEmpty()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Invalid refresh token")
                    .build());
            }
            
            MobileSession session = optionalSession.get();
            
            if (!session.canRefresh()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Cannot refresh session")
                    .build());
            }
            
            // Generate new tokens
            String newSessionToken = generateSessionToken(session.getUserId(), session.getDeviceId());
            String newRefreshToken = generateRefreshToken(session.getUserId(), session.getDeviceId());
            
            // Update session
            session.setSessionToken(newSessionToken);
            session.setRefreshToken(newRefreshToken);
            session.extend(LocalDateTime.now().plusMinutes(sessionDurationMinutes));
            session.setRefreshExpiresAt(LocalDateTime.now().plusDays(refreshTokenDurationDays));
            
            MobileSession updatedSession = mobileSessionRepository.save(session);
            
            logger.info("Session refreshed for user: {}", session.getUserId());
            
            TokenRefreshResponse response = new TokenRefreshResponse(
                updatedSession.getSessionToken(),
                updatedSession.getRefreshToken(),
                updatedSession.getExpiresAt()
            );
            
            return ApiResponse.success(response);
            
        } catch (Exception e) {
            logger.error("Error refreshing token", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Token refresh failed")
                .build());
        }
    }
    
    /**
     * Validate session token
     */
    @Cacheable(value = "sessionValidation", key = "#sessionToken")
    public Optional<SessionValidationResult> validateSession(@NotBlank String sessionToken) {
        try {
            Optional<MobileSession> optionalSession = mobileSessionRepository.findBySessionToken(sessionToken);
            
            if (optionalSession.isEmpty()) {
                return Optional.empty();
            }
            
            MobileSession session = optionalSession.get();
            
            if (!session.isActive()) {
                return Optional.empty();
            }
            
            // Get device information
            Optional<MobileDevice> optionalDevice = mobileDeviceRepository.findById(session.getDeviceId());
            if (optionalDevice.isEmpty()) {
                return Optional.empty();
            }
            
            MobileDevice device = optionalDevice.get();
            
            SessionValidationResult result = new SessionValidationResult(
                session.getUserId(),
                session.getDeviceId(),
                device.getDeviceId(),
                session.getSecurityLevel(),
                session.isFullyAuthenticated(),
                session.getRiskScore()
            );
            
            return Optional.of(result);
            
        } catch (Exception e) {
            logger.error("Error validating session", e);
            return Optional.empty();
        }
    }
    
    /**
     * Terminate session
     */
    @CacheEvict(value = "sessionValidation", key = "#sessionToken")
    public ApiResponse<Void> terminateSession(@NotBlank String sessionToken, String reason) {
        try {
            Optional<MobileSession> optionalSession = mobileSessionRepository.findBySessionToken(sessionToken);
            
            if (optionalSession.isEmpty()) {
                return ApiResponse.error(ErrorResponse.builder()
                    .message("Session not found")
                    .build());
            }
            
            MobileSession session = optionalSession.get();
            session.terminate(reason != null ? reason : "User logout");
            
            mobileSessionRepository.save(session);
            
            // Publish session termination event
            publishSessionEvent("SESSION_TERMINATED", session);
            
            logger.info("Session terminated for user: {}", session.getUserId());
            
            return ApiResponse.success(null);
            
        } catch (Exception e) {
            logger.error("Error terminating session", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to terminate session")
                .build());
        }
    }
    
    /**
     * Terminate all user sessions
     */
    @CacheEvict(value = "sessionValidation", allEntries = true)
    public ApiResponse<Void> terminateAllUserSessions(@NotNull UUID userId, String reason) {
        try {
            mobileSessionRepository.terminateAllUserSessions(
                userId, 
                reason != null ? reason : "Security action", 
                LocalDateTime.now()
            );
            
            logger.info("All sessions terminated for user: {}", userId);
            
            return ApiResponse.success(null);
            
        } catch (Exception e) {
            logger.error("Error terminating user sessions", e);
            return ApiResponse.error(ErrorResponse.builder()
                .message("Failed to terminate sessions")
                .build());
        }
    }
    
    /**
     * Get active sessions for user
     */
    public List<ActiveSessionInfo> getActiveUserSessions(@NotNull UUID userId) {
        List<MobileSession> sessions = mobileSessionRepository.findActiveSessionsByUser(userId, LocalDateTime.now());
        
        return sessions.stream()
            .map(session -> {
                Optional<MobileDevice> device = mobileDeviceRepository.findById(session.getDeviceId());
                return new ActiveSessionInfo(
                    session.getId(),
                    device.map(MobileDevice::getDeviceName).orElse("Unknown Device"),
                    device.map(MobileDevice::getDeviceType).orElse(null),
                    session.getLastActivityAt(),
                    session.getLocation(),
                    session.getRiskScore()
                );
            })
            .toList();
    }
    
    private String generateSessionToken(UUID userId, UUID deviceId) throws Exception {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
            .subject(userId.toString())
            .claim("deviceId", deviceId.toString())
            .claim("type", "session")
            .issueTime(new Date())
            .expirationTime(new Date(System.currentTimeMillis() + (sessionDurationMinutes * 60 * 1000)))
            .build();
        
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
        JWSSigner signer = new RSASSASigner(jwtSigningKey);
        signedJWT.sign(signer);
        
        return signedJWT.serialize();
    }
    
    private String generateRefreshToken(UUID userId, UUID deviceId) throws Exception {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
            .subject(userId.toString())
            .claim("deviceId", deviceId.toString())
            .claim("type", "refresh")
            .issueTime(new Date())
            .expirationTime(new Date(System.currentTimeMillis() + (refreshTokenDurationDays * 24 * 60 * 60 * 1000)))
            .build();
        
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
        JWSSigner signer = new RSASSASigner(jwtSigningKey);
        signedJWT.sign(signer);
        
        return signedJWT.serialize();
    }
    
    private boolean detectRootedOrJailbroken(DeviceRegistrationRequest request) {
        // Implement device security checks based on metadata
        String fingerprint = request.getDeviceFingerprint();
        if (fingerprint != null) {
            return fingerprint.contains("root") || 
                   fingerprint.contains("jailbreak") ||
                   fingerprint.contains("xposed") ||
                   fingerprint.contains("cydia");
        }
        return false;
    }
    
    private double calculateInitialRiskScore(MobileDevice device, DeviceRegistrationRequest request) {
        double score = 0.0;
        
        // Base score for new device
        score += 2.0;
        
        // Rooted/jailbroken devices are high risk
        if (Boolean.TRUE.equals(device.getIsRootedOrJailbroken())) {
            score += 4.0;
        }
        
        // No security features enabled
        if (!device.hasSecurityFeatures()) {
            score += 2.0;
        }
        
        // Old OS versions are riskier
        if (isOldOsVersion(device.getOsVersion())) {
            score += 1.5;
        }
        
        // Check for suspicious patterns in device metadata
        if (hasSuspiciousMetadata(request)) {
            score += 2.0;
        }
        
        return Math.min(score, 10.0);
    }
    
    private double calculateSessionRiskScore(MobileDevice device, AuthenticationRequest request) {
        double score = device.getRiskScore() * 0.5; // Start with half of device risk
        
        // Location-based risk
        if (isDifferentLocation(device.getLastLocation(), request.getLocation())) {
            score += 1.5;
        }
        
        // IP-based risk
        if (isDifferentIp(device.getLastIpAddress(), request.getIpAddress())) {
            score += 1.0;
        }
        
        // Time-based risk (unusual login times)
        if (isUnusualLoginTime()) {
            score += 0.5;
        }
        
        return Math.min(score, 10.0);
    }
    
    private int determineSecurityLevel(AuthenticationRequest request, MobileDevice device) {
        int level = 1; // Basic
        
        if (device.hasSecurityFeatures()) {
            level = 2; // Enhanced
        }
        
        if (request.getMfaToken() != null || "BIOMETRIC".equals(request.getLoginMethod())) {
            level = 3; // High
        }
        
        return level;
    }
    
    private boolean isOldOsVersion(String osVersion) {
        // Implement OS version checking logic
        return osVersion != null && osVersion.compareTo("10.0") < 0;
    }
    
    private boolean hasSuspiciousMetadata(DeviceRegistrationRequest request) {
        // Implement suspicious metadata detection
        return false;
    }
    
    private boolean isDifferentLocation(String lastLocation, String currentLocation) {
        if (lastLocation == null || currentLocation == null) return false;
        return !lastLocation.equals(currentLocation);
    }
    
    private boolean isDifferentIp(String lastIp, String currentIp) {
        if (lastIp == null || currentIp == null) return false;
        return !lastIp.equals(currentIp);
    }
    
    private boolean isUnusualLoginTime() {
        // Check if current time is outside normal business hours
        int hour = LocalDateTime.now().getHour();
        return hour < 6 || hour > 22;
    }
    
    private void publishDeviceEvent(String eventType, MobileDevice device) {
        try {
            DeviceEvent event = new DeviceEvent(
                eventType,
                device.getUserId(),
                device.getId(),
                device.getDeviceId(),
                device.getStatus().toString(),
                device.getRiskScore()
            );
            kafkaTemplate.send("mobile-device-events", event);
        } catch (Exception e) {
            logger.error("Failed to publish device event", e);
        }
    }
    
    private void publishAuthenticationEvent(String eventType, MobileSession session) {
        try {
            AuthenticationEvent event = new AuthenticationEvent(
                eventType,
                session.getUserId(),
                session.getDeviceId(),
                session.getLoginMethod(),
                session.getSecurityLevel(),
                session.getRiskScore(),
                session.getIpAddress(),
                session.getLocation()
            );
            kafkaTemplate.send("mobile-auth-events", event);
        } catch (Exception e) {
            logger.error("Failed to publish authentication event", e);
        }
    }
    
    private void publishSessionEvent(String eventType, MobileSession session) {
        try {
            SessionEvent event = new SessionEvent(
                eventType,
                session.getUserId(),
                session.getDeviceId(),
                session.getId(),
                session.getSessionDurationMinutes(),
                session.getActivityCount()
            );
            kafkaTemplate.send("mobile-session-events", event);
        } catch (Exception e) {
            logger.error("Failed to publish session event", e);
        }
    }
    
    // Request/Response DTOs
    public static class DeviceRegistrationRequest {
        private String deviceId;
        private UUID userId;
        private DeviceType deviceType;
        private String deviceName;
        private String operatingSystem;
        private String osVersion;
        private String appVersion;
        private String pushToken;
        private String deviceFingerprint;
        private String ipAddress;
        private String location;
        private Boolean biometricEnabled;
        private Boolean pinEnabled;
        private Boolean locationEnabled;
        
        // Getters and setters
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        
        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        
        public DeviceType getDeviceType() { return deviceType; }
        public void setDeviceType(DeviceType deviceType) { this.deviceType = deviceType; }
        
        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
        
        public String getOperatingSystem() { return operatingSystem; }
        public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }
        
        public String getOsVersion() { return osVersion; }
        public void setOsVersion(String osVersion) { this.osVersion = osVersion; }
        
        public String getAppVersion() { return appVersion; }
        public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
        
        public String getPushToken() { return pushToken; }
        public void setPushToken(String pushToken) { this.pushToken = pushToken; }
        
        public String getDeviceFingerprint() { return deviceFingerprint; }
        public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
        
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        
        public Boolean getBiometricEnabled() { return biometricEnabled; }
        public void setBiometricEnabled(Boolean biometricEnabled) { this.biometricEnabled = biometricEnabled; }
        
        public Boolean getPinEnabled() { return pinEnabled; }
        public void setPinEnabled(Boolean pinEnabled) { this.pinEnabled = pinEnabled; }
        
        public Boolean getLocationEnabled() { return locationEnabled; }
        public void setLocationEnabled(Boolean locationEnabled) { this.locationEnabled = locationEnabled; }
    }
    
    public static class DeviceRegistrationResponse {
        private final UUID deviceInternalId;
        private final String deviceId;
        private final DeviceStatus status;
        private final Boolean isTrusted;
        private final Double riskScore;
        
        public DeviceRegistrationResponse(UUID deviceInternalId, String deviceId, DeviceStatus status,
                                        Boolean isTrusted, Double riskScore) {
            this.deviceInternalId = deviceInternalId;
            this.deviceId = deviceId;
            this.status = status;
            this.isTrusted = isTrusted;
            this.riskScore = riskScore;
        }
        
        // Getters
        public UUID getDeviceInternalId() { return deviceInternalId; }
        public String getDeviceId() { return deviceId; }
        public DeviceStatus getStatus() { return status; }
        public Boolean getIsTrusted() { return isTrusted; }
        public Double getRiskScore() { return riskScore; }
    }
    
    public static class AuthenticationRequest {
        private UUID userId;
        private String deviceId;
        private String loginMethod;
        private String mfaToken;
        private String ipAddress;
        private String location;
        private String userAgent;
        
        // Getters and setters
        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        
        public String getLoginMethod() { return loginMethod; }
        public void setLoginMethod(String loginMethod) { this.loginMethod = loginMethod; }
        
        public String getMfaToken() { return mfaToken; }
        public void setMfaToken(String mfaToken) { this.mfaToken = mfaToken; }
        
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    }
    
    public static class AuthenticationResponse {
        private final String sessionToken;
        private final String refreshToken;
        private final LocalDateTime expiresAt;
        private final Integer securityLevel;
        private final Boolean deviceTrusted;
        private final Double riskScore;
        
        public AuthenticationResponse(String sessionToken, String refreshToken, LocalDateTime expiresAt,
                                    Integer securityLevel, Boolean deviceTrusted, Double riskScore) {
            this.sessionToken = sessionToken;
            this.refreshToken = refreshToken;
            this.expiresAt = expiresAt;
            this.securityLevel = securityLevel;
            this.deviceTrusted = deviceTrusted;
            this.riskScore = riskScore;
        }
        
        // Getters
        public String getSessionToken() { return sessionToken; }
        public String getRefreshToken() { return refreshToken; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public Integer getSecurityLevel() { return securityLevel; }
        public Boolean getDeviceTrusted() { return deviceTrusted; }
        public Double getRiskScore() { return riskScore; }
    }
    
    public static class TokenRefreshResponse {
        private final String sessionToken;
        private final String refreshToken;
        private final LocalDateTime expiresAt;
        
        public TokenRefreshResponse(String sessionToken, String refreshToken, LocalDateTime expiresAt) {
            this.sessionToken = sessionToken;
            this.refreshToken = refreshToken;
            this.expiresAt = expiresAt;
        }
        
        // Getters
        public String getSessionToken() { return sessionToken; }
        public String getRefreshToken() { return refreshToken; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
    }
    
    public static class SessionValidationResult {
        private final UUID userId;
        private final UUID deviceInternalId;
        private final String deviceId;
        private final Integer securityLevel;
        private final Boolean fullyAuthenticated;
        private final Double riskScore;
        
        public SessionValidationResult(UUID userId, UUID deviceInternalId, String deviceId,
                                     Integer securityLevel, Boolean fullyAuthenticated, Double riskScore) {
            this.userId = userId;
            this.deviceInternalId = deviceInternalId;
            this.deviceId = deviceId;
            this.securityLevel = securityLevel;
            this.fullyAuthenticated = fullyAuthenticated;
            this.riskScore = riskScore;
        }
        
        // Getters
        public UUID getUserId() { return userId; }
        public UUID getDeviceInternalId() { return deviceInternalId; }
        public String getDeviceId() { return deviceId; }
        public Integer getSecurityLevel() { return securityLevel; }
        public Boolean getFullyAuthenticated() { return fullyAuthenticated; }
        public Double getRiskScore() { return riskScore; }
    }
    
    public static class ActiveSessionInfo {
        private final UUID sessionId;
        private final String deviceName;
        private final DeviceType deviceType;
        private final LocalDateTime lastActivity;
        private final String location;
        private final Double riskScore;
        
        public ActiveSessionInfo(UUID sessionId, String deviceName, DeviceType deviceType,
                               LocalDateTime lastActivity, String location, Double riskScore) {
            this.sessionId = sessionId;
            this.deviceName = deviceName;
            this.deviceType = deviceType;
            this.lastActivity = lastActivity;
            this.location = location;
            this.riskScore = riskScore;
        }
        
        // Getters
        public UUID getSessionId() { return sessionId; }
        public String getDeviceName() { return deviceName; }
        public DeviceType getDeviceType() { return deviceType; }
        public LocalDateTime getLastActivity() { return lastActivity; }
        public String getLocation() { return location; }
        public Double getRiskScore() { return riskScore; }
    }
    
    // Event DTOs
    public static class DeviceEvent {
        private final String eventType;
        private final UUID userId;
        private final UUID deviceInternalId;
        private final String deviceId;
        private final String status;
        private final Double riskScore;
        private final LocalDateTime timestamp;
        
        public DeviceEvent(String eventType, UUID userId, UUID deviceInternalId, String deviceId,
                          String status, Double riskScore) {
            this.eventType = eventType;
            this.userId = userId;
            this.deviceInternalId = deviceInternalId;
            this.deviceId = deviceId;
            this.status = status;
            this.riskScore = riskScore;
            this.timestamp = LocalDateTime.now();
        }
        
        // Getters
        public String getEventType() { return eventType; }
        public UUID getUserId() { return userId; }
        public UUID getDeviceInternalId() { return deviceInternalId; }
        public String getDeviceId() { return deviceId; }
        public String getStatus() { return status; }
        public Double getRiskScore() { return riskScore; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    public static class AuthenticationEvent {
        private final String eventType;
        private final UUID userId;
        private final UUID deviceId;
        private final String loginMethod;
        private final Integer securityLevel;
        private final Double riskScore;
        private final String ipAddress;
        private final String location;
        private final LocalDateTime timestamp;
        
        public AuthenticationEvent(String eventType, UUID userId, UUID deviceId, String loginMethod,
                                 Integer securityLevel, Double riskScore, String ipAddress, String location) {
            this.eventType = eventType;
            this.userId = userId;
            this.deviceId = deviceId;
            this.loginMethod = loginMethod;
            this.securityLevel = securityLevel;
            this.riskScore = riskScore;
            this.ipAddress = ipAddress;
            this.location = location;
            this.timestamp = LocalDateTime.now();
        }
        
        // Getters
        public String getEventType() { return eventType; }
        public UUID getUserId() { return userId; }
        public UUID getDeviceId() { return deviceId; }
        public String getLoginMethod() { return loginMethod; }
        public Integer getSecurityLevel() { return securityLevel; }
        public Double getRiskScore() { return riskScore; }
        public String getIpAddress() { return ipAddress; }
        public String getLocation() { return location; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    public static class SessionEvent {
        private final String eventType;
        private final UUID userId;
        private final UUID deviceId;
        private final UUID sessionId;
        private final Long durationMinutes;
        private final Long activityCount;
        private final LocalDateTime timestamp;
        
        public SessionEvent(String eventType, UUID userId, UUID deviceId, UUID sessionId,
                           Long durationMinutes, Long activityCount) {
            this.eventType = eventType;
            this.userId = userId;
            this.deviceId = deviceId;
            this.sessionId = sessionId;
            this.durationMinutes = durationMinutes;
            this.activityCount = activityCount;
            this.timestamp = LocalDateTime.now();
        }
        
        // Getters
        public String getEventType() { return eventType; }
        public UUID getUserId() { return userId; }
        public UUID getDeviceId() { return deviceId; }
        public UUID getSessionId() { return sessionId; }
        public Long getDurationMinutes() { return durationMinutes; }
        public Long getActivityCount() { return activityCount; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}
