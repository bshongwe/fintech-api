# Multi-stage Docker build for Mobile SDK Service
FROM eclipse-temurin:17-jdk-alpine AS builder

# Set working directory
WORKDIR /app

# Copy gradle files
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle ./gradle

# Copy source code
COPY src ./src

# Copy commons library
COPY libs ./libs

# Make gradlew executable
RUN chmod +x ./gradlew

# Build the application
RUN ./gradlew build -x test --no-daemon

# Production stage
FROM eclipse-temurin:17-jre-alpine AS production

# Add labels for metadata
LABEL maintainer="FinTech API Team <api-support@fintech.com>"
LABEL description="Mobile SDK Service - Enterprise mobile authentication and device management"
LABEL version="1.0.0"

# Create non-root user for security
RUN addgroup -g 1001 -S mobilesdk && \
    adduser -u 1001 -S mobilesdk -G mobilesdk

# Set working directory
WORKDIR /app

# Install required packages
RUN apk add --no-cache \
    curl \
    ca-certificates \
    tzdata && \
    update-ca-certificates

# Copy application JAR from builder stage
COPY --from=builder /app/build/libs/mobile-sdk-service.jar app.jar

# Create logs directory with proper permissions
RUN mkdir -p /app/logs && \
    chown -R mobilesdk:mobilesdk /app

# Switch to non-root user
USER mobilesdk

# Expose port
EXPOSE 8085

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8085/actuator/health || exit 1

# Environment variables with defaults
ENV SPRING_PROFILES_ACTIVE=production
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport"
ENV TZ=UTC

# JVM tuning for containers
ENV JAVA_OPTS="$JAVA_OPTS \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+UseCGroupMemoryLimitForHeap \
    -XX:MaxRAMFraction=2 \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/logs/ \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.backgroundpreinitializer.ignore=true"

# Add New Relic agent (optional)
# COPY --from=builder /app/newrelic/newrelic.jar /app/newrelic.jar
# ENV JAVA_OPTS="$JAVA_OPTS -javaagent:/app/newrelic.jar"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Development stage (for local development)
FROM production AS development

# Switch back to root for development tools
USER root

# Install development tools
RUN apk add --no-cache \
    bash \
    git \
    vim \
    htop \
    postgresql-client \
    redis

# Install debugging tools
RUN apk add --no-cache \
    strace \
    tcpdump \
    netcat-openbsd

# Copy development configuration
COPY docker/dev-entrypoint.sh /usr/local/bin/dev-entrypoint.sh
RUN chmod +x /usr/local/bin/dev-entrypoint.sh

# Switch back to application user
USER mobilesdk

# Override entrypoint for development
ENTRYPOINT ["/usr/local/bin/dev-entrypoint.sh"]

# Testing stage (for running tests in CI/CD)
FROM builder AS testing

# Run tests
RUN ./gradlew test jacocoTestReport --no-daemon

# Copy test results for extraction
RUN mkdir -p /app/test-results && \
    cp -r build/reports /app/test-results/ && \
    cp -r build/test-results /app/test-results/

# Security scanning stage
FROM production AS security-scan

# Switch to root for security scanning
USER root

# Install security scanning tools
RUN apk add --no-cache \
    trivy \
    grype

# Run security scans (these would typically be run in CI/CD)
# RUN trivy fs --exit-code 1 --severity HIGH,CRITICAL /app
# RUN grype /app

# Switch back to application user
USER mobilesdk
