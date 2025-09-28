#!/bin/bash

# Development entrypoint script for Mobile SDK Service
# Provides development-friendly startup with debugging options

set -e

echo "🚀 Starting Mobile SDK Service in development mode..."

# Wait for dependencies to be ready
echo "⏳ Waiting for dependencies..."

# Wait for PostgreSQL
if [ ! -z "$DB_URL" ]; then
    echo "Waiting for PostgreSQL..."
    while ! nc -z ${DB_HOST:-localhost} ${DB_PORT:-5432}; do
        sleep 1
    done
    echo "✅ PostgreSQL is ready"
fi

# Wait for Redis
if [ ! -z "$REDIS_HOST" ]; then
    echo "Waiting for Redis..."
    while ! nc -z ${REDIS_HOST:-localhost} ${REDIS_PORT:-6379}; do
        sleep 1
    done
    echo "✅ Redis is ready"
fi

# Wait for Kafka
if [ ! -z "$KAFKA_BROKERS" ]; then
    echo "Waiting for Kafka..."
    KAFKA_HOST=$(echo $KAFKA_BROKERS | cut -d':' -f1)
    KAFKA_PORT=$(echo $KAFKA_BROKERS | cut -d':' -f2)
    while ! nc -z ${KAFKA_HOST:-localhost} ${KAFKA_PORT:-9092}; do
        sleep 1
    done
    echo "✅ Kafka is ready"
fi

# Set development-specific JVM options
export JAVA_OPTS="$JAVA_OPTS \
    -Xdebug \
    -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=*:5005 \
    -Dspring.profiles.active=development \
    -Dspring.devtools.restart.enabled=true \
    -Dspring.devtools.livereload.enabled=true"

echo "📝 JVM Options: $JAVA_OPTS"
echo "🏃 Starting application..."

# Start the application
exec java $JAVA_OPTS -jar app.jar
