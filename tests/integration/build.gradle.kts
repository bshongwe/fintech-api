plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

group = 'com.fintech'
version = '1.0.0'
sourceCompatibility = '21'

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Test Starters
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    testImplementation 'org.springframework.boot:spring-boot-starter-data-redis'
    testImplementation 'org.springframework.kafka:spring-kafka-test'
    
    // Security
    testImplementation 'org.springframework.boot:spring-boot-starter-security'
    testImplementation 'org.springframework.security:spring-security-test'
    
    // Database
    testImplementation 'org.postgresql:postgresql'
    testImplementation 'org.flywaydb:flyway-core'
    
    // Testcontainers
    testImplementation 'org.testcontainers:junit-jupiter:1.19.3'
    testImplementation 'org.testcontainers:postgresql:1.19.3'
    testImplementation 'org.testcontainers:kafka:1.19.3'
    testImplementation 'org.testcontainers:redis:1.19.3'
    
    // Jackson for JSON processing
    testImplementation 'com.fasterxml.jackson.core:jackson-databind'
    testImplementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
    
    // Kafka
    testImplementation 'org.springframework.kafka:spring-kafka'
    
    // Awaitility for async testing
    testImplementation 'org.awaitility:awaitility:4.2.0'
    
    // AssertJ for fluent assertions
    testImplementation 'org.assertj:assertj-core'
    
    // WireMock for service mocking
    testImplementation 'com.github.tomakehurst:wiremock-jre8:2.35.0'
    
    // JWT
    testImplementation 'io.jsonwebtoken:jjwt-api:0.12.3'
    testImplementation 'io.jsonwebtoken:jjwt-impl:0.12.3'
    testImplementation 'io.jsonwebtoken:jjwt-jackson:0.12.3'
    
    // Commons libraries for utilities
    testImplementation 'org.apache.commons:commons-lang3:3.14.0'
    
    // Service dependencies (these would normally come from service modules)
    testImplementation project(':services:account-service')
    testImplementation project(':services:payment-service')
    testImplementation project(':services:fraud-detection-service')
    testImplementation project(':services:compliance-service')
    testImplementation project(':services:reporting-service')
    testImplementation project(':services:admin-dashboard')
    testImplementation project(':services:mobile-sdk')
    testImplementation project(':libs:commons')
}

test {
    useJUnitPlatform()
    
    // Test configuration
    testLogging {
        events "passed", "skipped", "failed"
        exceptionFormat "full"
    }
    
    // System properties for integration tests
    systemProperty "spring.profiles.active", "integration-test"
    systemProperty "spring.jpa.hibernate.ddl-auto", "create-drop"
    systemProperty "logging.level.com.fintech", "DEBUG"
    systemProperty "logging.level.org.testcontainers", "INFO"
    
    // Memory settings for integration tests
    minHeapSize = "512m"
    maxHeapSize = "2g"
    
    // Parallel execution
    maxParallelForks = Runtime.runtime.availableProcessors().intdiv(2) ?: 1
    
    // Test timeouts
    timeout = Duration.ofMinutes(30)
}

// Integration test task
task integrationTest(type: Test) {
    description = 'Runs integration tests'
    group = 'verification'
    
    useJUnitPlatform {
        includeTags 'integration'
    }
    
    shouldRunAfter test
    
    // Additional memory for heavy integration tests
    maxHeapSize = '4g'
    
    // Longer timeout for integration tests
    timeout = Duration.ofMinutes(45)
}

// End-to-end test task
task e2eTest(type: Test) {
    description = 'Runs end-to-end tests'
    group = 'verification'
    
    useJUnitPlatform {
        includeTags 'e2e'
    }
    
    shouldRunAfter integrationTest
    
    // Maximum resources for E2E tests
    maxHeapSize = '6g'
    timeout = Duration.ofHours(1)
}

// Security test task
task securityTest(type: Test) {
    description = 'Runs security integration tests'
    group = 'verification'
    
    useJUnitPlatform {
        includeTags 'security'
    }
    
    shouldRunAfter test
    
    // Security tests with enhanced logging
    systemProperty "logging.level.org.springframework.security", "DEBUG"
    systemProperty "fintech.security.test.mode", "true"
}

// Performance test task
task performanceTest(type: Test) {
    description = 'Runs performance integration tests'
    group = 'verification'
    
    useJUnitPlatform {
        includeTags 'performance'
    }
    
    shouldRunAfter integrationTest
    
    // Performance test configuration
    maxHeapSize = '8g'
    systemProperty "fintech.performance.test.load", "high"
    systemProperty "fintech.performance.test.duration", "300" // 5 minutes
}

// Full test suite
task fullTestSuite {
    description = 'Runs complete test suite including all integration tests'
    group = 'verification'
    
    dependsOn test, integrationTest, securityTest, e2eTest
}

// Test reporting
tasks.withType(Test) {
    reports {
        junitXml.required = true
        html.required = true
    }
    
    finalizedBy jacocoTestReport
}

// Code coverage
apply plugin: 'jacoco'

jacoco {
    toolVersion = "0.8.8"
}

jacocoTestReport {
    dependsOn test, integrationTest
    
    reports {
        xml.required = true
        html.required = true
        csv.required = false
    }
    
    executionData fileTree(dir: "${buildDir}/jacoco", include: "**/*.exec")
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.80 // 80% coverage requirement
            }
        }
        
        rule {
            enabled = true
            element = 'CLASS'
            excludes = [
                '*.configuration.*',
                '*.config.*',
                '*.*Application',
                '*.dto.*',
                '*.entity.*'
            ]
            
            limit {
                counter = 'LINE'
                value = 'COVEREDRATIO'
                minimum = 0.75
            }
        }
    }
}

// Docker integration for tests
task startTestInfrastructure {
    description = 'Starts test infrastructure using Docker Compose'
    group = 'docker'
    
    doLast {
        exec {
            commandLine 'docker-compose', '-f', 'docker/test-infrastructure.yml', 'up', '-d'
        }
    }
}

task stopTestInfrastructure {
    description = 'Stops test infrastructure'
    group = 'docker'
    
    doLast {
        exec {
            commandLine 'docker-compose', '-f', 'docker/test-infrastructure.yml', 'down'
        }
    }
}

// Clean task enhancements
clean {
    delete "${buildDir}/test-results"
    delete "${buildDir}/reports"
    delete "${buildDir}/jacoco"
}

// Quality gates
task qualityGate {
    description = 'Runs quality gate checks including tests and coverage'
    group = 'verification'
    
    dependsOn test, integrationTest, jacocoTestCoverageVerification
    
    doLast {
        println "Quality gate passed - all tests and coverage requirements met"
    }
}
