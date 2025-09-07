#!/usr/bin/env bash
set -euo pipefail
echo "Building all services..."
./gradlew :services:auth-service:build :services:account-service:build -x test
echo "Starting docker-compose..."
docker-compose -f docker/docker-compose.yml up --build
