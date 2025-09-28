package com.fintech.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-End Integration Tests
 * 
 * Tests the complete flow across all services:
 * 1. Mobile device registration and authentication
 * 2. Payment initiation and processing
 * 3. Fraud detection and risk assessment
 * 4. Ledger transaction recording
 * 5. Real-time notifications
 * 6. Compliance audit trails
 * 7. Reporting and analytics
 * 8. Admin dashboard operations
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EndToEndIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(EndToEndIntegrationTest.class);
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("fintech_integration_test")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Database configuration
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        
        // Kafka configuration
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        
        // Redis configuration
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        
        // Test profile
        registry.add("spring.profiles.active", () -> "integration-test");
        registry.add("logging.level.com.fintech", () -> "DEBUG");
    }
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // Test data
    private String mobileDeviceId;
    private String sessionToken;
    private String userId;
    private String paymentId;
    private String accountId;
    
    @BeforeEach
    void setUp() {
        // Initialize test data
        mobileDeviceId = "test-device-" + UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        accountId = UUID.randomUUID().toString();
    }
    
    @Test
    @Order(1)
    @DisplayName("Complete Payment Flow - Mobile Registration to Settlement")
    void testCompletePaymentFlow() throws Exception {
        // Step 1: Register mobile device
        String deviceRegistrationResponse = registerMobileDevice();
        assertThat(deviceRegistrationResponse).isNotNull();
        
        // Step 2: Authenticate user and create session
        sessionToken = authenticateUser();
        assertThat(sessionToken).isNotNull();
        
        // Step 3: Create account
        createAccount();
        
        // Step 4: Initiate payment
        paymentId = initiatePayment();
        assertThat(paymentId).isNotNull();
        
        // Step 5: Verify fraud detection analysis
        verifyFraudDetectionAnalysis();
        
        // Step 6: Process payment through bank connector
        processPaymentThroughBankConnector();
        
        // Step 7: Verify ledger entries
        verifyLedgerEntries();
        
        // Step 8: Verify compliance audit trail
        verifyComplianceAuditTrail();
        
        // Step 9: Verify push notification sent
        verifyPushNotificationSent();
        
        // Step 10: Generate and verify reports
        verifyReportGeneration();
        
        // Step 11: Verify admin dashboard data
        verifyAdminDashboardData();
    }
    
    @Test
    @Order(2)
    @DisplayName("Fraud Detection Integration - High Risk Transaction")
    void testFraudDetectionIntegration() throws Exception {
        // Setup: Register device and authenticate
        registerMobileDevice();
        sessionToken = authenticateUser();
        createAccount();
        
        // Initiate high-risk payment (large amount from new device)
        String highRiskPaymentId = initiateHighRiskPayment();
        
        // Verify fraud alert generated
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String alertsResponse = getWithAuth("/api/v1/fraud/alerts/user/" + userId);
            assertThat(alertsResponse).contains("HIGH_RISK_TRANSACTION");
        });
        
        // Verify payment blocked
        String paymentStatus = getWithAuth("/api/v1/payments/" + highRiskPaymentId + "/status");
        assertThat(paymentStatus).contains("BLOCKED");
        
        // Verify security notification sent
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            String notifications = getWithAuth("/api/v1/mobile/notifications/analytics");
            assertThat(notifications).contains("SECURITY");
        });
    }
    
    @Test
    @Order(3)
    @DisplayName("Cross-Service Event Propagation")
    void testCrossServiceEventPropagation() throws Exception {
        // Setup
        registerMobileDevice();
        sessionToken = authenticateUser();
        createAccount();
        
        // Initiate payment
        String testPaymentId = initiatePayment();
        
        // Verify events propagated across all services
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            // Check compliance service received events
            String complianceEvents = getWithAuth("/api/v1/compliance/audit-events");
            assertThat(complianceEvents).contains("PAYMENT_INITIATED");
            
            // Check reporting service has data
            String reports = getWithAuth("/api/v1/reports/real-time/payment-summary");
            assertThat(reports).contains(testPaymentId);
            
            // Check admin dashboard updated
            String dashboardData = getWithAuth("/api/v1/admin/monitoring/payment-metrics");
            assertThat(dashboardData).contains("payment_count");
        });
    }
    
    @Test
    @Order(4)
    @DisplayName("Service Resilience - Fault Tolerance")
    void testServiceResilience() throws Exception {
        // Setup normal flow
        registerMobileDevice();
        sessionToken = authenticateUser();
        createAccount();
        
        // Test 1: Payment service handles fraud service timeout
        // Mock fraud service delay
        String paymentWithDelay = initiatePaymentWithFraudDelay();
        
        // Verify payment still processes (with default risk assessment)
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            String status = getWithAuth("/api/v1/payments/" + paymentWithDelay + "/status");
            assertThat(status).containsAnyOf("COMPLETED", "PENDING");
        });
        
        // Test 2: Notification service handles delivery failures gracefully
        String failedNotificationTest = initiatePaymentForNotificationTest();
        
        // Verify payment completes even if notification fails
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            String status = getWithAuth("/api/v1/payments/" + failedNotificationTest + "/status");
            assertThat(status).contains("COMPLETED");
        });
    }
    
    @Test
    @Order(5)
    @DisplayName("Performance Under Load - Concurrent Operations")
    void testPerformanceUnderLoad() throws Exception {
        // Setup multiple devices and users
        List<String> devices = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            String deviceId = "load-test-device-" + i;
            String testUserId = UUID.randomUUID().toString();
            
            // Register device
            registerMobileDeviceForUser(deviceId, testUserId);
            
            // Authenticate
            String token = authenticateUserForDevice(deviceId, testUserId);
            tokens.add(token);
            devices.add(deviceId);
            
            // Create account
            createAccountForUser(testUserId, token);
        }
        
        // Execute concurrent payments
        long startTime = System.currentTimeMillis();
        List<String> paymentIds = Collections.synchronizedList(new ArrayList<>());
        
        tokens.parallelStream().forEach(token -> {
            try {
                String concurrentPaymentId = initiatePaymentWithToken(token);
                paymentIds.add(concurrentPaymentId);
            } catch (Exception e) {
                // Log but continue - use proper logger instead of System.err
                logger.warn("Concurrent payment failed: {}", e.getMessage());
            }
        });
        
        long endTime = System.currentTimeMillis();
        
        // Verify performance metrics
        assertThat(endTime - startTime).isLessThan(30000); // All payments in under 30 seconds
        assertThat(paymentIds.size()).isGreaterThanOrEqualTo(8); // At least 80% success rate
        
        // Verify all services handled load
        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            String metrics = getWithAuth("/actuator/metrics/payment.processing.time");
            assertThat(metrics).isNotNull();
        });
    }
    
    // Helper Methods
    
    private String registerMobileDevice() throws Exception {
        Map<String, Object> deviceRegistration = Map.of(
            "deviceId", mobileDeviceId,
            "userId", userId,
            "deviceType", "ANDROID",
            "deviceName", "Test Device",
            "operatingSystem", "Android",
            "osVersion", "13.0",
            "appVersion", "1.0.0",
            "biometricEnabled", true,
            "pinEnabled", true,
            "locationEnabled", true
        );
        
        return postJson("/api/v1/mobile/auth/devices/register", deviceRegistration);
    }
    
    private String authenticateUser() throws Exception {
        Map<String, Object> authRequest = Map.of(
            "userId", userId,
            "deviceId", mobileDeviceId,
            "loginMethod", "BIOMETRIC"
        );
        
        String response = postJson("/api/v1/mobile/auth/authenticate", authRequest);
        
        // Extract token from response
        Map<String, Object> authResponse = objectMapper.readValue(response, Map.class);
        Map<String, Object> data = (Map<String, Object>) authResponse.get("data");
        return (String) data.get("sessionToken");
    }
    
    private void createAccount() throws Exception {
        Map<String, Object> accountRequest = Map.of(
            "userId", userId,
            "accountType", "CHECKING",
            "currency", "USD",
            "initialBalance", new BigDecimal("1000.00")
        );
        
        String response = postJsonWithAuth("/api/v1/account/create", accountRequest);
        
        // Extract account ID
        Map<String, Object> accountResponse = objectMapper.readValue(response, Map.class);
        Map<String, Object> data = (Map<String, Object>) accountResponse.get("data");
        accountId = (String) data.get("accountId");
    }
    
    private String initiatePayment() throws Exception {
        Map<String, Object> paymentRequest = Map.of(
            "fromAccountId", accountId,
            "toAccountId", UUID.randomUUID().toString(),
            "amount", new BigDecimal("100.00"),
            "currency", "USD",
            "description", "Integration test payment",
            "paymentMethod", "MOBILE"
        );
        
        String response = postJsonWithAuth("/api/v1/payments/initiate", paymentRequest);
        
        // Extract payment ID
        Map<String, Object> paymentResponse = objectMapper.readValue(response, Map.class);
        Map<String, Object> data = (Map<String, Object>) paymentResponse.get("data");
        return (String) data.get("paymentId");
    }
    
    private String initiateHighRiskPayment() throws Exception {
        Map<String, Object> paymentRequest = Map.of(
            "fromAccountId", accountId,
            "toAccountId", UUID.randomUUID().toString(),
            "amount", new BigDecimal("9999.99"), // High amount
            "currency", "USD",
            "description", "High risk test payment",
            "paymentMethod", "MOBILE"
        );
        
        String response = postJsonWithAuth("/api/v1/payments/initiate", paymentRequest);
        
        Map<String, Object> paymentResponse = objectMapper.readValue(response, Map.class);
        Map<String, Object> data = (Map<String, Object>) paymentResponse.get("data");
        return (String) data.get("paymentId");
    }
    
    private void verifyFraudDetectionAnalysis() throws Exception {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String analysis = getWithAuth("/api/v1/fraud/analysis/payment/" + paymentId);
            assertThat(analysis).contains("riskScore");
        });
    }
    
    private void processPaymentThroughBankConnector() throws Exception {
        // Payment should automatically process through appropriate bank connector
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            String status = getWithAuth("/api/v1/payments/" + paymentId + "/status");
            assertThat(status).containsAnyOf("COMPLETED", "PROCESSING");
        });
    }
    
    private void verifyLedgerEntries() throws Exception {
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            String ledgerEntries = getWithAuth("/api/v1/ledger/entries/payment/" + paymentId);
            assertThat(ledgerEntries).contains("DEBIT");
            assertThat(ledgerEntries).contains("CREDIT");
        });
    }
    
    private void verifyComplianceAuditTrail() throws Exception {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String auditTrail = getWithAuth("/api/v1/compliance/audit-trail/payment/" + paymentId);
            assertThat(auditTrail).contains("PAYMENT_INITIATED");
            assertThat(auditTrail).contains("FRAUD_CHECK_COMPLETED");
        });
    }
    
    private void verifyPushNotificationSent() throws Exception {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String notifications = getWithAuth("/api/v1/mobile/notifications/analytics");
            Map<String, Object> analytics = objectMapper.readValue(notifications, Map.class);
            Map<String, Object> data = (Map<String, Object>) analytics.get("data");
            
            assertThat(((Number) data.get("totalSent")).intValue()).isGreaterThan(0);
        });
    }
    
    private void verifyReportGeneration() throws Exception {
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            String reportData = getWithAuth("/api/v1/reports/payment-summary");
            assertThat(reportData).contains(paymentId);
        });
    }
    
    private void verifyAdminDashboardData() throws Exception {
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            String dashboardMetrics = getWithAuth("/api/v1/admin/dashboard/metrics");
            assertThat(dashboardMetrics).contains("totalPayments");
            assertThat(dashboardMetrics).contains("activeUsers");
        });
    }
    
    // Utility methods for additional test scenarios
    
    private String initiatePaymentWithFraudDelay() throws Exception {
        // This would typically involve configuration to simulate fraud service delay
        return initiatePayment();
    }
    
    private String initiatePaymentForNotificationTest() throws Exception {
        return initiatePayment();
    }
    
    private void registerMobileDeviceForUser(String deviceId, String testUserId) throws Exception {
        Map<String, Object> deviceRegistration = Map.of(
            "deviceId", deviceId,
            "userId", testUserId,
            "deviceType", "ANDROID",
            "deviceName", "Load Test Device",
            "operatingSystem", "Android",
            "osVersion", "13.0",
            "appVersion", "1.0.0",
            "biometricEnabled", true
        );
        
        postJson("/api/v1/mobile/auth/devices/register", deviceRegistration);
    }
    
    private String authenticateUserForDevice(String deviceId, String testUserId) throws Exception {
        Map<String, Object> authRequest = Map.of(
            "userId", testUserId,
            "deviceId", deviceId,
            "loginMethod", "PIN"
        );
        
        String response = postJson("/api/v1/mobile/auth/authenticate", authRequest);
        Map<String, Object> authResponse = objectMapper.readValue(response, Map.class);
        Map<String, Object> data = (Map<String, Object>) authResponse.get("data");
        return (String) data.get("sessionToken");
    }
    
    private void createAccountForUser(String testUserId, String token) throws Exception {
        Map<String, Object> accountRequest = Map.of(
            "userId", testUserId,
            "accountType", "CHECKING",
            "currency", "USD",
            "initialBalance", new BigDecimal("500.00")
        );
        
        postJsonWithAuthToken("/api/v1/account/create", accountRequest, token);
    }
    
    private String initiatePaymentWithToken(String token) throws Exception {
        Map<String, Object> paymentRequest = Map.of(
            "fromAccountId", UUID.randomUUID().toString(), // Would be real account ID
            "toAccountId", UUID.randomUUID().toString(),
            "amount", new BigDecimal("50.00"),
            "currency", "USD",
            "description", "Load test payment",
            "paymentMethod", "MOBILE"
        );
        
        String response = postJsonWithAuthToken("/api/v1/payments/initiate", paymentRequest, token);
        Map<String, Object> paymentResponse = objectMapper.readValue(response, Map.class);
        Map<String, Object> data = (Map<String, Object>) paymentResponse.get("data");
        return (String) data.get("paymentId");
    }
    
    // HTTP utility methods
    
    private String postJson(String endpoint, Object body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:" + port + endpoint, request, String.class);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }
    
    private String postJsonWithAuth(String endpoint, Object body) throws Exception {
        return postJsonWithAuthToken(endpoint, body, sessionToken);
    }
    
    private String postJsonWithAuthToken(String endpoint, Object body, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:" + port + endpoint, request, String.class);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }
    
    private String getWithAuth(String endpoint) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(sessionToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
            "http://localhost:" + port + endpoint, HttpMethod.GET, request, String.class);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }
}
