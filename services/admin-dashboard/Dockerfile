# Use OpenJDK 17 as base image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy the built JAR file
COPY build/libs/admin-dashboard-1.0.0.jar app.jar

# Create non-root user for security
RUN addgroup --system --gid 1001 fintech && \
    adduser --system --uid 1001 --gid 1001 fintech

# Create logs directory
RUN mkdir -p /app/logs && chown -R fintech:fintech /app

# Switch to non-root user
USER fintech

# Expose port
EXPOSE 8090

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8090/admin-dashboard/actuator/health || exit 1

# Set JVM options for production
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
