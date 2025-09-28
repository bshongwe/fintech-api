package com.fintech.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Security Integration Tests
 * 
 * Tests security aspects across all services:
 * 1. Authentication and authorization flows
 * 2. JWT token validation across services
 * 3. API security and rate limiting
 * 4. Data encryption and PII protection
 * 5. Audit trail security
 * 6. Mobile security and device attestation
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SecurityIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("fintech_security_test")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.profiles.active", () -> "integration-test,security-test");
        
        // Enable security features for testing
        registry.add("fintech.security.jwt.enabled", () -> "true");
        registry.add("fintech.security.rate-limiting.enabled", () -> "true");
        registry.add("fintech.security.encryption.enabled", () -> "true");
    }
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private String validUserToken;
    private String validAdminToken;
    private String expiredToken;
    private String malformedToken;
    
    @BeforeEach
    void setUp() throws Exception {
        // Setup valid tokens
        validUserToken = authenticateUser("testuser", "password123");
        validAdminToken = authenticateAdmin("admin", "admin123");
        
        // Setup invalid tokens for testing
        expiredToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJleHAiOjE1MTYyMzkwMjJ9.expired";
        malformedToken = "invalid.jwt.token";
    }
    
    @Test
    @Order(1)
    @DisplayName("Authentication Flow Across All Services")
    void testAuthenticationFlow() throws Exception {
        // Test 1: Valid authentication
        Map<String, Object> authRequest = Map.of(
            "username", "integrationuser",
            "password", "secure123"
        );
        
        String authResponse = postJson("/api/v1/auth/login", authRequest);
        Map<String, Object> authData = objectMapper.readValue(authResponse, Map.class);
        String token = (String) ((Map<String, Object>) authData.get("data")).get("token");
        
        assertThat(token).isNotNull();
        
        // Test 2: Token validation across services
        String[] serviceEndpoints = {
            "/api/v1/account/profile",
            "/api/v1/payments/history",
            "/api/v1/fraud/user-risk-profile",
            "/api/v1/compliance/user-audit-trail",
            "/api/v1/reports/user-summary"
        };
        
        for (String endpoint : serviceEndpoints) {
            String response = getWithToken(endpoint, token);
            assertThat(response).doesNotContain("Unauthorized");
            assertThat(response).doesNotContain("Invalid token");
        }
        
        // Test 3: Invalid token rejection
        for (String endpoint : serviceEndpoints) {
            ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + endpoint,
                HttpMethod.GET,
                createRequestWithToken(malformedToken),
                String.class
            );
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
    
    @Test
    @Order(2)
    @DisplayName("Role-Based Access Control (RBAC)")
    void testRoleBasedAccessControl() throws Exception {
        // Create user and admin tokens
        String userToken = authenticateUser("regularuser", "password123");
        String adminToken = authenticateAdmin("superadmin", "admin123");
        
        // Test 1: User access to user endpoints (should succeed)
        String[] userEndpoints = {
            "/api/v1/account/profile",
            "/api/v1/payments/history",
            "/api/v1/mobile/sessions/current"
        };
        
        for (String endpoint : userEndpoints) {
            ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + endpoint,
                HttpMethod.GET,
                createRequestWithToken(userToken),
                String.class
            );
            
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        }
        
        // Test 2: User access to admin endpoints (should fail)
        String[] adminEndpoints = {
            "/api/v1/admin/dashboard/metrics",
            "/api/v1/admin/users/all",
            "/api/v1/admin/system-config"
        };
        
        for (String endpoint : adminEndpoints) {
            ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + endpoint,
                HttpMethod.GET,
                createRequestWithToken(userToken),
                String.class
            );
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        
        // Test 3: Admin access to admin endpoints (should succeed)
        for (String endpoint : adminEndpoints) {
            ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + endpoint,
                HttpMethod.GET,
                createRequestWithToken(adminToken),
                String.class
            );
            
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        }
    }
    
    @Test
    @Order(3)
    @DisplayName("API Rate Limiting and Throttling")
    void testRateLimitingAcrossServices() throws Exception {
        String userToken = authenticateUser("ratelimituser", "password123");
        
        // Test rate limiting on payment initiation (high-security endpoint)
        String accountId = createTestAccount(userToken);
        
        List<CompletableFuture<ResponseEntity<String>>> futures = new ArrayList<>();
        
        // Attempt to make 20 rapid payment requests (should hit rate limit)
        for (int i = 0; i < 20; i++) {
            Map<String, Object> paymentRequest = Map.of(
                "fromAccountId", accountId,
                "toAccountId", UUID.randomUUID().toString(),
                "amount", new BigDecimal("10.00"),
                "currency", "USD",
                "description", "Rate limit test " + i
            );
            
            CompletableFuture<ResponseEntity<String>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return restTemplate.postForEntity(
                        "http://localhost:" + port + "/api/v1/payments/initiate",
                        createJsonRequestWithToken(paymentRequest, userToken),
                        String.class
                    );
                } catch (Exception e) {
                    return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
                }
            });
            
            futures.add(future);
        }
        
        // Wait for all requests to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // Count successful vs rate-limited responses
        long successfulRequests = futures.stream()
            .mapToLong(future -> {
                try {
                    ResponseEntity<String> response = future.get();
                    return response.getStatusCode().is2xxSuccessful() ? 1 : 0;
                } catch (Exception e) {
                    return 0;
                }
            })
            .sum();
        
        long rateLimitedRequests = futures.stream()
            .mapToLong(future -> {
                try {
                    ResponseEntity<String> response = future.get();
                    return response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS ? 1 : 0;
                } catch (Exception e) {
                    return 0;
                }
            })
            .sum();
        
        // Verify rate limiting is working
        assertThat(rateLimitedRequests).isGreaterThan(0);
        assertThat(successfulRequests).isLessThan(20);
    }
    
    @Test
    @Order(4)
    @DisplayName("Data Encryption and PII Protection")
    void testDataEncryptionAndPIIProtection() throws Exception {
        String userToken = authenticateUser("piiuser", "password123");
        
        // Create account with PII data
        Map<String, Object> accountRequest = Map.of(
            "accountType", "CHECKING",
            "currency", "USD",
            "initialBalance", new BigDecimal("1000.00"),
            "personalInfo", Map.of(
                "ssn", "123-45-6789",
                "bankAccountNumber", "9876543210",
                "routingNumber", "021000021"
            )
        );
        
        String accountResponse = postJsonWithToken("/api/v1/account/create", accountRequest, userToken);
        String accountId = extractAccountId(accountResponse);
        
        // Verify PII is encrypted in database (indirect test via API responses)
        String accountDetails = getWithToken("/api/v1/account/" + accountId, userToken);
        Map<String, Object> account = objectMapper.readValue(accountDetails, Map.class);
        Map<String, Object> data = (Map<String, Object>) account.get("data");
        
        // PII should be masked or encrypted in API responses
        if (data.containsKey("personalInfo")) {
            Map<String, Object> personalInfo = (Map<String, Object>) data.get("personalInfo");
            String ssn = (String) personalInfo.get("ssn");
            String accountNumber = (String) personalInfo.get("bankAccountNumber");
            
            // Verify SSN is masked
            if (ssn != null) {
                assertThat(ssn).matches("\\*\\*\\*-\\*\\*-\\d{4}"); // Should be masked like ***-**-6789
            }
            
            // Verify account number is masked
            if (accountNumber != null) {
                assertThat(accountNumber).matches("\\*{6}\\d{4}"); // Should be masked like ******3210
            }
        }
        
        // Test audit trail doesn't expose PII
        String auditTrail = getWithToken("/api/v1/compliance/audit-trail/account/" + accountId, userToken);
        assertThat(auditTrail).doesNotContain("123-45-6789");
        assertThat(auditTrail).doesNotContain("9876543210");
    }
    
    @Test
    @Order(5)
    @DisplayName("Mobile Security and Device Attestation")
    void testMobileSecurityAndDeviceAttestation() throws Exception {
        String deviceId = "security-test-device-" + UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        
        // Test 1: Device registration with security features
        Map<String, Object> deviceRegistration = Map.of(
            "deviceId", deviceId,
            "userId", userId,
            "deviceType", "ANDROID",
            "deviceName", "Security Test Device",
            "operatingSystem", "Android",
            "osVersion", "13.0",
            "appVersion", "1.0.0",
            "biometricEnabled", true,
            "pinEnabled", true,
            "jailbroken", false,
            "attestationData", Map.of(
                "nonce", "security-nonce-123",
                "timestamp", System.currentTimeMillis(),
                "signature", "mock-attestation-signature"
            )
        );
        
        String registrationResponse = postJson("/api/v1/mobile/auth/devices/register", deviceRegistration);
        assertThat(registrationResponse).contains("success");
        
        // Test 2: Secure authentication with biometric
        Map<String, Object> authRequest = Map.of(
            "userId", userId,
            "deviceId", deviceId,
            "loginMethod", "BIOMETRIC",
            "biometricData", "mock-biometric-hash"
        );
        
        String authResponse = postJson("/api/v1/mobile/auth/authenticate", authRequest);
        String mobileToken = extractMobileToken(authResponse);
        
        // Test 3: Session security validation
        String sessionValidation = getWithToken("/api/v1/mobile/sessions/validate", mobileToken);
        Map<String, Object> validation = objectMapper.readValue(sessionValidation, Map.class);
        Map<String, Object> sessionData = (Map<String, Object>) validation.get("data");
        
        assertThat(sessionData.get("deviceId")).isEqualTo(deviceId);
        assertThat(sessionData.get("securityLevel")).isEqualTo("HIGH"); // Due to biometric auth
        
        // Test 4: Detect potential security threats (jailbroken device)
        Map<String, Object> maliciousDeviceRegistration = Map.of(
            "deviceId", "malicious-device-" + UUID.randomUUID().toString(),
            "userId", UUID.randomUUID().toString(),
            "deviceType", "IOS",
            "deviceName", "Suspicious Device",
            "operatingSystem", "iOS",
            "osVersion", "16.0",
            "appVersion", "1.0.0",
            "jailbroken", true // This should trigger security alert
        );
        
        ResponseEntity<String> maliciousResponse = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v1/mobile/auth/devices/register",
            createJsonRequest(maliciousDeviceRegistration),
            String.class
        );
        
        // Should either reject or flag for review
        assertThat(maliciousResponse.getStatusCode()).isIn(
            HttpStatus.BAD_REQUEST, 
            HttpStatus.FORBIDDEN, 
            HttpStatus.ACCEPTED // Accepted but flagged
        );
    }
    
    @Test
    @Order(6)
    @DisplayName("Security Audit Trail Integrity")
    void testSecurityAuditTrailIntegrity() throws Exception {
        String userToken = authenticateUser("audituser", "password123");
        String accountId = createTestAccount(userToken);
        
        // Perform various actions that should be audited
        Map<String, Object> paymentRequest = Map.of(
            "fromAccountId", accountId,
            "toAccountId", UUID.randomUUID().toString(),
            "amount", new BigDecimal("500.00"),
            "currency", "USD",
            "description", "Security audit test payment"
        );
        
        String paymentResponse = postJsonWithToken("/api/v1/payments/initiate", paymentRequest, userToken);
        String paymentId = extractPaymentId(paymentResponse);
        
        // Wait for audit events to be recorded
        Thread.sleep(3000);
        
        // Retrieve audit trail
        String auditTrail = getWithToken("/api/v1/compliance/audit-trail/payment/" + paymentId, userToken);
        Map<String, Object> audit = objectMapper.readValue(auditTrail, Map.class);
        List<Map<String, Object>> events = (List<Map<String, Object>>) ((Map<String, Object>) audit.get("data")).get("events");
        
        // Verify audit trail integrity
        for (Map<String, Object> event : events) {
            // Each event should have required security fields
            assertThat(event.get("timestamp")).isNotNull();
            assertThat(event.get("userId")).isNotNull();
            assertThat(event.get("eventType")).isNotNull();
            assertThat(event.get("source")).isNotNull();
            assertThat(event.get("ipAddress")).isNotNull();
            assertThat(event.get("userAgent")).isNotNull();
            
            // Should have integrity hash
            assertThat(event.get("integrityHash")).isNotNull();
            
            // Should not contain sensitive data in plain text
            String eventData = event.toString();
            assertThat(eventData).doesNotContainIgnoringCase("password");
            assertThat(eventData).doesNotContainIgnoringCase("ssn");
            assertThat(eventData).doesNotContainIgnoringCase("credit card");
        }
        
        // Verify chronological order
        List<Long> timestamps = events.stream()
            .map(event -> ((Number) event.get("timestamp")).longValue())
            .toList();
        
        for (int i = 1; i < timestamps.size(); i++) {
            assertThat(timestamps.get(i)).isGreaterThanOrEqualTo(timestamps.get(i - 1));
        }
    }
    
    @Test
    @Order(7)
    @DisplayName("Cross-Service Security Token Propagation")
    void testCrossServiceSecurityTokenPropagation() throws Exception {
        String userToken = authenticateUser("tokenuser", "password123");
        String accountId = createTestAccount(userToken);
        
        // Initiate payment that will trigger security checks across services
        Map<String, Object> paymentRequest = Map.of(
            "fromAccountId", accountId,
            "toAccountId", UUID.randomUUID().toString(),
            "amount", new BigDecimal("1200.00"),
            "currency", "USD",
            "description", "Cross-service security test"
        );
        
        String paymentResponse = postJsonWithToken("/api/v1/payments/initiate", paymentRequest, userToken);
        String paymentId = extractPaymentId(paymentResponse);
        
        // Verify security context propagated to fraud detection service
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String fraudAnalysis = getWithToken("/api/v1/fraud/analysis/payment/" + paymentId, userToken);
            Map<String, Object> analysis = objectMapper.readValue(fraudAnalysis, Map.class);
            Map<String, Object> data = (Map<String, Object>) analysis.get("data");
            
            // Should contain user context from original token
            assertThat(data.get("userId")).isNotNull();
            assertThat(data.get("securityContext")).isNotNull();
        });
        
        // Verify security context propagated to compliance service
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String complianceData = getWithToken("/api/v1/compliance/security-context/payment/" + paymentId, userToken);
            Map<String, Object> compliance = objectMapper.readValue(complianceData, Map.class);
            Map<String, Object> data = (Map<String, Object>) compliance.get("data");
            
            // Should maintain security context across service calls
            assertThat(data.get("originalUserId")).isNotNull();
            assertThat(data.get("authenticationMethod")).isNotNull();
            assertThat(data.get("securityLevel")).isNotNull();
        });
    }
    
    // Helper Methods
    
    private String authenticateUser(String username, String password) throws Exception {
        Map<String, Object> authRequest = Map.of(
            "username", username,
            "password", password
        );
        
        String response = postJson("/api/v1/auth/login", authRequest);
        return extractToken(response);
    }
    
    private String authenticateAdmin(String username, String password) throws Exception {
        Map<String, Object> authRequest = Map.of(
            "username", username,
            "password", password,
            "role", "ADMIN"
        );
        
        String response = postJson("/api/v1/admin/auth/login", authRequest);
        return extractToken(response);
    }
    
    private String createTestAccount(String token) throws Exception {
        Map<String, Object> accountRequest = Map.of(
            "accountType", "CHECKING",
            "currency", "USD",
            "initialBalance", new BigDecimal("2000.00")
        );
        
        String response = postJsonWithToken("/api/v1/account/create", accountRequest, token);
        return extractAccountId(response);
    }
    
    private String extractToken(String response) throws Exception {
        Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
        Map<String, Object> data = (Map<String, Object>) parsed.get("data");
        return (String) data.get("token");
    }
    
    private String extractMobileToken(String response) throws Exception {
        Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
        Map<String, Object> data = (Map<String, Object>) parsed.get("data");
        return (String) data.get("sessionToken");
    }
    
    private String extractAccountId(String response) throws Exception {
        Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
        Map<String, Object> data = (Map<String, Object>) parsed.get("data");
        return (String) data.get("accountId");
    }
    
    private String extractPaymentId(String response) throws Exception {
        Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
        Map<String, Object> data = (Map<String, Object>) parsed.get("data");
        return (String) data.get("paymentId");
    }
    
    // HTTP utility methods
    
    private String postJson(String endpoint, Object body) throws Exception {
        HttpEntity<String> request = createJsonRequest(body);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:" + port + endpoint, request, String.class);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }
    
    private String postJsonWithToken(String endpoint, Object body, String token) throws Exception {
        HttpEntity<String> request = createJsonRequestWithToken(body, token);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:" + port + endpoint, request, String.class);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }
    
    private String getWithToken(String endpoint, String token) throws Exception {
        HttpEntity<String> request = createRequestWithToken(token);
        ResponseEntity<String> response = restTemplate.exchange(
            "http://localhost:" + port + endpoint, HttpMethod.GET, request, String.class);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }
    
    private HttpEntity<String> createJsonRequest(Object body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
    }
    
    private HttpEntity<String> createJsonRequestWithToken(Object body, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
    }
    
    private HttpEntity<String> createRequestWithToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}
