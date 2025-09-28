# Integration Tests

This directory contains comprehensive integration tests for the Fintech API platform.

## Overview

The integration test suite validates:
- Cross-service communication and data flow
- End-to-end business processes
- Security implementations across all services
- Performance under load
- Service resilience and fault tolerance

## Test Structure

```
tests/integration/
├── src/test/java/com/fintech/integration/
│   ├── EndToEndIntegrationTest.java      # Complete payment flow testing
│   ├── ServiceCommunicationTest.java     # Inter-service communication
│   └── SecurityIntegrationTest.java      # Security across all services
├── docker/
│   └── test-infrastructure.yml           # Test infrastructure services
├── src/test/resources/
│   └── application.yml                   # Test configuration
├── build.gradle.kts                      # Build and test configuration
├── run-tests.sh                         # Test runner script
└── README.md                            # This file
```

## Test Categories

### 1. End-to-End Integration Tests (`EndToEndIntegrationTest`)
- **Complete Payment Flow**: Mobile registration → Authentication → Payment → Settlement
- **Fraud Detection Integration**: High-risk transaction handling
- **Cross-Service Event Propagation**: Event-driven architecture validation
- **Service Resilience**: Fault tolerance testing
- **Performance Under Load**: Concurrent operations testing

### 2. Service Communication Tests (`ServiceCommunicationTest`)
- **Payment ↔ Fraud Detection**: Synchronous API calls and async events
- **Compliance Event Processing**: Cross-service audit trail validation
- **Reporting Data Aggregation**: Multi-service data collection
- **Admin Dashboard Integration**: Management interface validation
- **Mobile SDK Communication**: Real-time mobile interactions
- **Event-Driven Architecture**: Complete event flow testing

### 3. Security Integration Tests (`SecurityIntegrationTest`)
- **Authentication Flow**: JWT token validation across services
- **Role-Based Access Control**: RBAC implementation validation
- **API Rate Limiting**: Throttling and security controls
- **Data Encryption**: PII protection verification
- **Mobile Security**: Device attestation and biometric authentication
- **Audit Trail Integrity**: Security audit validation
- **Cross-Service Token Propagation**: Security context maintenance

## Test Infrastructure

The tests use **Testcontainers** for infrastructure services:

- **PostgreSQL**: Primary database
- **Redis**: Caching layer
- **Kafka**: Event streaming
- **WireMock**: External service mocking
- **Elasticsearch**: Logging and search
- **Prometheus**: Metrics collection

## Running Tests

### Prerequisites

- Java 21+
- Docker and Docker Compose
- Gradle 8.5+

### Quick Start

```bash
# Run all integration tests
./run-tests.sh

# Run specific test categories
./run-tests.sh --integration-only
./run-tests.sh --security-only
./run-tests.sh --e2e-only
./run-tests.sh --performance-only
```

### Advanced Usage

```bash
# Run without starting infrastructure (for CI/CD)
./run-tests.sh --no-infrastructure

# Run tests and show logs for troubleshooting
./run-tests.sh --skip-cleanup --show-logs

# Run only unit tests
./run-tests.sh --unit-only
```

### Gradle Tasks

```bash
# Individual test tasks
./gradlew integrationTest      # Core integration tests
./gradlew securityTest         # Security-focused tests
./gradlew e2eTest             # End-to-end scenarios
./gradlew performanceTest     # Performance validation

# Test reporting
./gradlew jacocoTestReport    # Generate coverage reports
./gradlew qualityGate         # Run quality checks
```

## Test Configuration

### Profiles

- **integration-test**: Default integration test configuration
- **security-test**: Enhanced security testing with stricter controls
- **performance-test**: Optimized for load testing
- **e2e-test**: Real external service integration

### Key Configuration

```yaml
fintech:
  security:
    jwt.enabled: true
    rate-limiting.enabled: true
    encryption.enabled: true
  
  integration-test:
    mock-external-services: true
    performance-testing: false
    security-testing: true
```

## Test Scenarios

### Complete Payment Flow Test
1. Mobile device registration with security features
2. User authentication with biometric validation
3. Account creation and balance verification
4. Payment initiation with risk assessment
5. Fraud detection analysis and scoring
6. Bank connector processing
7. Ledger entry recording
8. Compliance audit trail creation
9. Push notification delivery
10. Report generation and dashboard updates

### Security Validation Test
1. JWT token generation and validation
2. Role-based access control enforcement
3. API rate limiting and throttling
4. PII data encryption verification
5. Mobile device attestation
6. Audit trail integrity checks
7. Cross-service security context propagation

### Service Communication Test
1. Synchronous API calls between services
2. Asynchronous event processing via Kafka
3. Data consistency across service boundaries
4. Circuit breaker and fault tolerance
5. Service discovery and load balancing

## Monitoring and Observability

The tests validate monitoring capabilities:

- **Metrics**: Prometheus metrics collection
- **Logging**: Structured logging with correlation IDs
- **Tracing**: Distributed tracing across services
- **Health Checks**: Service health monitoring
- **Alerts**: Automated alert generation

## Performance Benchmarks

Performance tests validate:

- **Throughput**: 100+ concurrent transactions
- **Response Time**: < 2 seconds for payment processing
- **Scalability**: Linear scaling with load
- **Resource Usage**: Memory and CPU efficiency

## CI/CD Integration

The test suite integrates with CI/CD pipelines:

```bash
# CI pipeline integration
./run-tests.sh --no-infrastructure --unit-only
./gradlew integrationTest --profile=ci

# Quality gates
./gradlew qualityGate
```

## Troubleshooting

### Common Issues

1. **Infrastructure Startup**: Ensure Docker is running and ports are available
2. **Test Timeouts**: Increase timeout values for slower environments
3. **Resource Limits**: Adjust JVM heap size for memory-intensive tests
4. **Network Issues**: Check Docker network configuration

### Debug Mode

Enable debug logging:

```bash
export FINTECH_LOG_LEVEL=DEBUG
./run-tests.sh --show-logs
```

### Test Data

Test data is automatically created and cleaned up:

- **Users**: Test users with various roles
- **Accounts**: Different account types and balances
- **Transactions**: Sample transaction data
- **Devices**: Mock mobile devices

## Coverage Requirements

- **Line Coverage**: Minimum 80%
- **Branch Coverage**: Minimum 75%
- **Integration Coverage**: All service interactions tested

## Security Testing

Security tests validate:

- **OWASP Top 10**: Common vulnerability testing
- **PCI DSS**: Payment card industry compliance
- **PSD2**: European payment services directive
- **Data Protection**: GDPR compliance validation

## Contributing

When adding new integration tests:

1. Follow the existing test structure
2. Use meaningful test names and descriptions
3. Include proper cleanup and resource management
4. Add appropriate timeouts and retries
5. Document test scenarios and expected outcomes

## Test Reports

After running tests, reports are available in:

- **HTML Reports**: `build/reports/tests/`
- **Coverage Reports**: `build/reports/jacoco/`
- **XML Results**: `build/test-results/`

## Contact

For questions about integration testing:
- Team Lead: Principal Software Engineer
- Documentation: See `/docs/testing/integration-testing.md`
- Issues: Create GitHub issue with `integration-test` label
