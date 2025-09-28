#!/bin/bash
set -e

echo "Building fintech-api services..."

# Build all services
./gradlew build

echo "Starting services with Docker Compose..."

# Start services
docker-compose -f docker/docker-compose.yml up --build -d

echo "Services started successfully!"
echo "Auth service: http://localhost:9000"
echo "Account service: http://localhost:8081"