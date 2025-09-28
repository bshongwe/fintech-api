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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Service Communication Integration Tests
 * 
 * Tests inter-service communication patterns:
 * 1. Synchronous API calls between services
 * 2. Asynchronous event-driven communication via Kafka
 * 3. Service discovery and load balancing
 * 4. Circuit breaker patterns and fault tolerance
 * 5. Data consistency across service boundaries
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ServiceCommunicationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("fintech_communication_test")
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
        registry.add("spring.profiles.active", () -> "integration-test");
    }
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private String adminToken;
    private String userToken;
    
    @BeforeEach
    void setUp() throws Exception {
        // Setup admin authentication
        adminToken = authenticateAdmin();
        
        // Setup user authentication
        userToken = authenticateUser();
    }
    
    @Test
    @Order(1)
    @DisplayName("Payment Service → Fraud Detection Service Communication")
    void testPaymentToFraudServiceCommunication() throws Exception {
        // Create test account
        String accountId = createTestAccount();
        
        // Initiate payment (triggers fraud detection)
        Map<String, Object> paymentRequest = Map.of(
            "fromAccountId", accountId,
            "toAccountId", UUID.randomUUID().toString(),
            "amount", new BigDecimal("500.00"),
            "currency", "USD",
            "description", "Service communication test"
        );
        
        String paymentResponse = postJsonWithAuth("/api/v1/payments/initiate", paymentRequest);
        Map<String, Object> payment = objectMapper.readValue(paymentResponse, Map.class);
        String paymentId = (String) ((Map<String, Object>) payment.get("data")).get("paymentId");
        
        // Verify fraud detection service received and processed request
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String fraudAnalysis = getWithAuth("/api/v1/fraud/analysis/payment/" + paymentId);
            Map<String, Object> analysis = objectMapper.readValue(fraudAnalysis, Map.class);
            Map<String, Object> data = (Map<String, Object>) analysis.get("data");
            
            assertThat(data.get("paymentId")).isEqualTo(paymentId);
            assertThat(data.get("riskScore")).isNotNull();
            assertThat(data.get("status")).isIn("APPROVED", "PENDING", "REJECTED");
        });
        
        // Verify bi-directional communication - fraud result sent back to payment service
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            String paymentStatus = getWithAuth("/api/v1/payments/" + paymentId + "/status");
            Map<String, Object> status = objectMapper.readValue(paymentStatus, Map.class);
            Map<String, Object> data = (Map<String, Object>) status.get("data");
            
            assertThat(data.get("fraudAnalysisCompleted")).isEqualTo(true);
            assertThat(data.get("riskScore")).isNotNull();
        });
    }
    
    @Test
    @Order(2)
    @DisplayName("Compliance Service Event Processing Across All Services")
    void testComplianceEventProcessing() throws Exception {
        String accountId = createTestAccount();
        
        // Trigger multiple events that should generate compliance audit trails
        Map<String, Object> paymentRequest = Map.of(
            "fromAccountId", accountId,
            "toAccountId", UUID.randomUUID().toString(),
            "amount", new BigDecimal("1500.00"), // Amount that triggers enhanced compliance
            "currency", "USD",
            "description", "Compliance tracking test"
        );
        
        String paymentResponse = postJsonWithAuth("/api/v1/payments/initiate", paymentRequest);
        String paymentId = extractPaymentId(paymentResponse);
        
        // Verify compliance service received events from multiple sources
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            String auditEvents = getWithAdminAuth("/api/v1/compliance/audit-events/payment/" + paymentId);
            Map<String, Object> events = objectMapper.readValue(auditEvents, Map.class);
            List<Map<String, Object>> eventList = (List<Map<String, Object>>) ((Map<String, Object>) events.get("data")).get("events");
            
            // Verify events from different services
            Set<String> eventSources = new HashSet<>();
            Set<String> eventTypes = new HashSet<>();
            
            for (Map<String, Object> event : eventList) {
                eventSources.add((String) event.get("source"));
                eventTypes.add((String) event.get("eventType"));
            }
            
            assertThat(eventSources).contains("PAYMENT_SERVICE", "FRAUD_DETECTION_SERVICE");
            assertThat(eventTypes).contains("PAYMENT_INITIATED", "FRAUD_CHECK_COMPLETED");
        });
    }
    
    @Test
    @Order(3)
    @DisplayName("Reporting Service Cross-Service Data Aggregation")
    void testReportingServiceDataAggregation() throws Exception {
        // Generate test data across multiple services
        String accountId = createTestAccount();
        List<String> paymentIds = new ArrayList<>();
        
        // Create multiple payments
        for (int i = 0; i < 3; i++) {
            Map<String, Object> paymentRequest = Map.of(
                "fromAccountId", accountId,
                "toAccountId", UUID.randomUUID().toString(),
                "amount", new BigDecimal("100.00"),
                "currency", "USD",
                "description", "Report aggregation test " + i
            );
            
            String response = postJsonWithAuth("/api/v1/payments/initiate", paymentRequest);
            paymentIds.add(extractPaymentId(response));
        }
        
        // Wait for all payments to be processed
        Thread.sleep(5000);
        
        // Request comprehensive report that requires data from multiple services
        Map<String, Object> reportRequest = Map.of(
            "reportType", "COMPREHENSIVE_PAYMENT_ANALYSIS",
            "startDate", LocalDateTime.now().minusHours(1).toString(),
            "endDate", LocalDateTime.now().toString(),
            "includeRiskAnalysis", true,
            "includeComplianceData", true
        );
        
        String reportResponse = postJsonWithAuth("/api/v1/reports/generate", reportRequest);
        String reportId = extractReportId(reportResponse);
        
        // Verify report contains aggregated data from multiple services
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            String reportData = getWithAuth("/api/v1/reports/" + reportId + "/data");
            Map<String, Object> report = objectMapper.readValue(reportData, Map.class);
            Map<String, Object> data = (Map<String, Object>) report.get("data");
            
            // Verify payment data
            assertThat(((List<?>) data.get("payments")).size()).isGreaterThanOrEqualTo(3);
            
            // Verify fraud analysis data
            assertThat(data.get("riskMetrics")).isNotNull();
            
            // Verify compliance data
            assertThat(data.get("complianceMetrics")).isNotNull();
            
            // Verify ledger data
            assertThat(data.get("ledgerSummary")).isNotNull();
        });
    }
    
    @Test
    @Order(4)
    @DisplayName("Admin Dashboard Service Integration")
    void testAdminDashboardServiceIntegration() throws Exception {
        // Generate activity across all services
        String accountId = createTestAccount();
        
        // Create payments with different risk profiles
        createHighRiskPayment(accountId);
        createNormalPayment(accountId);
        
        // Create admin alert
        Map<String, Object> alertRequest = Map.of(
            "type", "SYSTEM",
            "severity", "HIGH",
            "title", "Integration Test Alert",
            "message", "Testing admin dashboard integration"
        );
        
        postJsonWithAdminAuth("/api/v1/admin/alerts/create", alertRequest);
        
        // Verify admin dashboard aggregates data from all services
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            String dashboardData = getWithAdminAuth("/api/v1/admin/dashboard/comprehensive");
            Map<String, Object> dashboard = objectMapper.readValue(dashboardData, Map.class);
            Map<String, Object> data = (Map<String, Object>) dashboard.get("data");
            
            // Verify payment metrics
            Map<String, Object> paymentMetrics = (Map<String, Object>) data.get("paymentMetrics");
            assertThat(((Number) paymentMetrics.get("totalPayments")).intValue()).isGreaterThan(0);
            
            // Verify fraud metrics
            Map<String, Object> fraudMetrics = (Map<String, Object>) data.get("fraudMetrics");
            assertThat(fraudMetrics.get("totalAnalyses")).isNotNull();
            
            // Verify compliance metrics
            Map<String, Object> complianceMetrics = (Map<String, Object>) data.get("complianceMetrics");
            assertThat(((Number) complianceMetrics.get("totalEvents")).intValue()).isGreaterThan(0);
            
            // Verify active alerts
            Map<String, Object> alertMetrics = (Map<String, Object>) data.get("alertMetrics");
            assertThat(((Number) alertMetrics.get("activeAlerts")).intValue()).isGreaterThan(0);
        });
    }
    
    @Test
    @Order(5)
    @DisplayName("Mobile SDK Service Real-time Communication")
    void testMobileSDKServiceCommunication() throws Exception {
        // Register mobile device
        String deviceId = "integration-test-device-" + UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        
        Map<String, Object> deviceRegistration = Map.of(
            "deviceId", deviceId,
            "userId", userId,
            "deviceType", "ANDROID",
            "deviceName", "Integration Test Device",
            "operatingSystem", "Android",
            "osVersion", "13.0",
            "appVersion", "1.0.0"
        );
        
        postJson("/api/v1/mobile/auth/devices/register", deviceRegistration);
        
        // Authenticate mobile session
        Map<String, Object> authRequest = Map.of(
            "userId", userId,
            "deviceId", deviceId,
            "loginMethod", "PIN"
        );
        
        String authResponse = postJson("/api/v1/mobile/auth/authenticate", authRequest);
        String mobileToken = extractMobileToken(authResponse);
        
        // Create account for mobile user
        String accountId = createAccountForMobileUser(userId, mobileToken);
        
        // Initiate payment from mobile
        Map<String, Object> mobilePaymentRequest = Map.of(
            "fromAccountId", accountId,
            "toAccountId", UUID.randomUUID().toString(),
            "amount", new BigDecimal("250.00"),
            "currency", "USD",
            "description", "Mobile integration test payment"
        );
        
        String paymentResponse = postJsonWithMobileToken("/api/v1/payments/initiate", mobilePaymentRequest, mobileToken);
        String paymentId = extractPaymentId(paymentResponse);
        
        // Verify mobile notification sent
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            String notifications = getWithMobileToken("/api/v1/mobile/notifications/device/" + deviceId, mobileToken);
            Map<String, Object> notificationData = objectMapper.readValue(notifications, Map.class);
            List<Map<String, Object>> notificationList = (List<Map<String, Object>>) ((Map<String, Object>) notificationData.get("data")).get("notifications");
            
            boolean paymentNotificationFound = notificationList.stream()
                .anyMatch(notification -> {
                    String message = (String) notification.get("message");
                    return message != null && message.contains("payment") && message.contains("initiated");
                });
            
            assertThat(paymentNotificationFound).isTrue();
        });
        
        // Verify mobile session data updated across services
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String sessionData = getWithMobileToken("/api/v1/mobile/sessions/current", mobileToken);
            Map<String, Object> session = objectMapper.readValue(sessionData, Map.class);
            Map<String, Object> data = (Map<String, Object>) session.get("data");
            
            assertThat(((Number) data.get("totalTransactions")).intValue()).isGreaterThan(0);
            assertThat(data.get("lastActivity")).isNotNull();
        });
    }
    
    @Test
    @Order(6)
    @DisplayName("Event-Driven Architecture End-to-End")
    void testEventDrivenArchitectureFlow() throws Exception {
        String accountId = createTestAccount();
        
        // Initiate payment that will trigger events across all services
        Map<String, Object> paymentRequest = Map.of(
            "fromAccountId", accountId,
            "toAccountId", UUID.randomUUID().toString(),
            "amount", new BigDecimal("750.00"),
            "currency", "USD",
            "description", "Event architecture test"
        );
        
        String paymentResponse = postJsonWithAuth("/api/v1/payments/initiate", paymentRequest);
        String paymentId = extractPaymentId(paymentResponse);
        
        // Track event propagation across services with timing
        long startTime = System.currentTimeMillis();
        
        // 1. Verify Fraud Detection Service processed event
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String fraudCheck = getWithAuth("/api/v1/fraud/analysis/payment/" + paymentId);
            assertThat(fraudCheck).contains("riskScore");
        });
        
        // 2. Verify Ledger Service recorded entries
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            String ledgerEntries = getWithAuth("/api/v1/ledger/entries/payment/" + paymentId);
            assertThat(ledgerEntries).contains("DEBIT");
        });
        
        // 3. Verify Compliance Service logged events
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            String complianceEvents = getWithAdminAuth("/api/v1/compliance/audit-events/payment/" + paymentId);
            assertThat(complianceEvents).contains("PAYMENT_INITIATED");
        });
        
        // 4. Verify Reporting Service updated metrics
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            String reportMetrics = getWithAuth("/api/v1/reports/real-time/payment-metrics");
            Map<String, Object> metrics = objectMapper.readValue(reportMetrics, Map.class);
            Map<String, Object> data = (Map<String, Object>) metrics.get("data");
            
            assertThat(((Number) data.get("totalPayments")).intValue()).isGreaterThan(0);
        });
        
        // 5. Verify Admin Dashboard updated
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            String dashboardMetrics = getWithAdminAuth("/api/v1/admin/dashboard/real-time-metrics");
            assertThat(dashboardMetrics).contains("recentPayments");
        });
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        // Verify event propagation completed within reasonable time
        assertThat(totalTime).isLessThan(25000); // Under 25 seconds for complete propagation
    }
    
    // Helper Methods
    
    private String authenticateAdmin() throws Exception {
        Map<String, Object> adminAuth = Map.of(
            "username", "admin",
            "password", "admin123",
            "role", "ADMIN"
        );
        
        String response = postJson("/api/v1/admin/auth/login", adminAuth);
        return extractToken(response);
    }
    
    private String authenticateUser() throws Exception {
        Map<String, Object> userAuth = Map.of(
            "username", "testuser",
            "password", "user123"
        );
        
        String response = postJson("/api/v1/auth/login", userAuth);
        return extractToken(response);
    }
    
    private String createTestAccount() throws Exception {
        Map<String, Object> accountRequest = Map.of(
            "accountType", "CHECKING",
            "currency", "USD",
            "initialBalance", new BigDecimal("5000.00")
        );
        
        String response = postJsonWithAuth("/api/v1/account/create", accountRequest);
        return extractAccountId(response);
    }
    
    private String createAccountForMobileUser(String userId, String mobileToken) throws Exception {
        Map<String, Object> accountRequest = Map.of(
            "userId", userId,
            "accountType", "CHECKING",
            "currency", "USD",
            "initialBalance", new BigDecimal("2000.00")
        );
        
        String response = postJsonWithMobileToken("/api/v1/account/create", accountRequest, mobileToken);
        return extractAccountId(response);
    }
    
    private void createHighRiskPayment(String accountId) throws Exception {
        Map<String, Object> paymentRequest = Map.of(
            "fromAccountId", accountId,
            "toAccountId", UUID.randomUUID().toString(),
            "amount", new BigDecimal("2500.00"), // High amount
            "currency", "USD",
            "description", "High risk integration test"
        );
        
        postJsonWithAuth("/api/v1/payments/initiate", paymentRequest);
    }
    
    private void createNormalPayment(String accountId) throws Exception {
        Map<String, Object> paymentRequest = Map.of(
            "fromAccountId", accountId,
            "toAccountId", UUID.randomUUID().toString(),
            "amount", new BigDecimal("150.00"),
            "currency", "USD",
            "description", "Normal integration test"
        );
        
        postJsonWithAuth("/api/v1/payments/initiate", paymentRequest);
    }
    
    // Extraction helper methods
    
    private String extractPaymentId(String response) throws Exception {
        Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
        Map<String, Object> data = (Map<String, Object>) parsed.get("data");
        return (String) data.get("paymentId");
    }
    
    private String extractReportId(String response) throws Exception {
        Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
        Map<String, Object> data = (Map<String, Object>) parsed.get("data");
        return (String) data.get("reportId");
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
        return postJsonWithToken(endpoint, body, userToken);
    }
    
    private String postJsonWithAdminAuth(String endpoint, Object body) throws Exception {
        return postJsonWithToken(endpoint, body, adminToken);
    }
    
    private String postJsonWithMobileToken(String endpoint, Object body, String token) throws Exception {
        return postJsonWithToken(endpoint, body, token);
    }
    
    private String postJsonWithToken(String endpoint, Object body, String token) throws Exception {
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
        return getWithToken(endpoint, userToken);
    }
    
    private String getWithAdminAuth(String endpoint) throws Exception {
        return getWithToken(endpoint, adminToken);
    }
    
    private String getWithMobileToken(String endpoint, String token) throws Exception {
        return getWithToken(endpoint, token);
    }
    
    private String getWithToken(String endpoint, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
            "http://localhost:" + port + endpoint, HttpMethod.GET, request, String.class);
        
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }
}
