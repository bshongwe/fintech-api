import java.time.Instant
import java.net.URL
import java.net.HttpURLConnection

plugins {
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    id("java")
    id("jacoco")
    id("org.sonarqube") version "4.4.1.3373"
}

group = "com.fintech"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    
    // Kafka
    implementation("org.springframework.kafka:spring-kafka")
    
    // Database
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.zaxxer:HikariCP")
    
    // Redis
    implementation("io.lettuce:lettuce-core")
    
    // JWT and Security
    implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.security:spring-security-config")
    
    // HTTP Client
    implementation("org.apache.httpcomponents.client5:httpclient5:5.2.1")
    
    // JSON Processing
    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    // OpenAPI Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")
    
    // Monitoring and Metrics
    implementation("io.micrometer:micrometer-registry-prometheus")
    
    // Utilities
    implementation("org.apache.commons:commons-lang3:3.13.0")
    implementation("commons-codec:commons-codec:1.16.0")
    
    // Common Libraries (internal)
    implementation(project(":libs:commons"))
    
    // Development Tools
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    
    // Annotation Processing
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    
    // Test Dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("com.redis.testcontainers:testcontainers-redis:1.6.4")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("com.h2database:h2")
}

// Test Configuration
tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
    
    systemProperty("spring.profiles.active", "test")
}

// Code Coverage
jacoco {
    toolVersion = "0.8.10"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    
    finalizedBy(tasks.jacocoTestCoverageVerification)
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

// SonarQube Configuration
sonar {
    properties {
        property("sonar.projectKey", "fintech-mobile-sdk")
        property("sonar.projectName", "FinTech Mobile SDK Service")
        property("sonar.host.url", "http://localhost:9000")
        property("sonar.sources", "src/main/java")
        property("sonar.tests", "src/test/java")
        property("sonar.java.coveragePlugin", "jacoco")
        property("sonar.coverage.jacoco.xmlReportPaths", "${project.buildDir}/reports/jacoco/test/jacocoTestReport.xml")
        property("sonar.exclusions", "**/*Application.java,**/*Config.java,**/domain/**,**/dto/**")
    }
}

// Spring Boot Configuration
springBoot {
    buildInfo()
}

// Build Configuration
tasks.jar {
    enabled = false
}

tasks.bootJar {
    archiveFileName.set("mobile-sdk-service.jar")
    
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Built-By" to System.getProperty("user.name"),
            "Built-JDK" to System.getProperty("java.version"),
            "Build-Time" to Instant.now().toString()
        )
    }
}

// Custom Tasks
tasks.register("generateApiDocs") {
    group = "documentation"
    description = "Generate API documentation"
    
    doLast {
        println("API documentation will be available at http://localhost:8085/swagger-ui.html when service is running")
    }
}

tasks.register("dockerBuild") {
    group = "docker"
    description = "Build Docker image for mobile SDK service"
    dependsOn(tasks.bootJar)
    
    doLast {
        exec {
            commandLine("docker", "build", "-t", "fintech/mobile-sdk-service:${project.version}", ".")
        }
        exec {
            commandLine("docker", "tag", "fintech/mobile-sdk-service:${project.version}", "fintech/mobile-sdk-service:latest")
        }
    }
}

tasks.register("dockerPush") {
    group = "docker"
    description = "Push Docker image to registry"
    dependsOn("dockerBuild")
    
    doLast {
        exec {
            commandLine("docker", "push", "fintech/mobile-sdk-service:${project.version}")
        }
        exec {
            commandLine("docker", "push", "fintech/mobile-sdk-service:latest")
        }
    }
}

// Quality Gates
tasks.build {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// Health Check
tasks.register("healthCheck") {
    group = "verification"
    description = "Perform health check on running service"
    
    doLast {
        try {
            val url = "http://localhost:8085/actuator/health"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                println("✅ Mobile SDK Service is healthy")
            } else {
                println("❌ Mobile SDK Service health check failed: HTTP $responseCode")
            }
        } catch (e: Exception) {
            println("❌ Mobile SDK Service is not accessible: ${e.message}")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
