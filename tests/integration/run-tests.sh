#!/bin/bash

# Integration Test Runner Script
# Automates the complete integration testing process

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
TEST_INFRASTRUCTURE_COMPOSE="docker/test-infrastructure.yml"
GRADLE_BUILD_FILE="build.gradle.kts"
TEST_RESULTS_DIR="build/test-results"
REPORTS_DIR="build/reports"

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_banner() {
    echo -e "${BLUE}"
    echo "=================================================================="
    echo "                  FINTECH API INTEGRATION TESTS"
    echo "=================================================================="
    echo -e "${NC}"
}

cleanup_test_infrastructure() {
    log_info "Cleaning up test infrastructure..."
    if [ -f "$TEST_INFRASTRUCTURE_COMPOSE" ]; then
        docker-compose -f "$TEST_INFRASTRUCTURE_COMPOSE" down --volumes --remove-orphans 2>/dev/null || true
    fi
    
    # Clean up any leftover containers
    docker container prune -f 2>/dev/null || true
    docker volume prune -f 2>/dev/null || true
}

start_test_infrastructure() {
    log_info "Starting test infrastructure..."
    
    if [ ! -f "$TEST_INFRASTRUCTURE_COMPOSE" ]; then
        log_error "Test infrastructure compose file not found: $TEST_INFRASTRUCTURE_COMPOSE"
        exit 1
    fi
    
    # Start infrastructure services
    docker-compose -f "$TEST_INFRASTRUCTURE_COMPOSE" up -d
    
    # Wait for services to be healthy
    log_info "Waiting for services to be ready..."
    
    # PostgreSQL
    log_info "Waiting for PostgreSQL..."
    timeout 60 bash -c 'until docker-compose -f '"$TEST_INFRASTRUCTURE_COMPOSE"' exec -T postgres-test pg_isready -U test -d fintech_integration_test; do sleep 2; done'
    
    # Redis
    log_info "Waiting for Redis..."
    timeout 30 bash -c 'until docker-compose -f '"$TEST_INFRASTRUCTURE_COMPOSE"' exec -T redis-test redis-cli ping | grep PONG; do sleep 2; done'
    
    # Kafka
    log_info "Waiting for Kafka..."
    timeout 60 bash -c 'until docker-compose -f '"$TEST_INFRASTRUCTURE_COMPOSE"' exec -T kafka-test kafka-broker-api-versions --bootstrap-server localhost:9092 >/dev/null 2>&1; do sleep 5; done'
    
    log_success "Test infrastructure is ready!"
}

run_unit_tests() {
    log_info "Running unit tests..."
    ./gradlew test --continue
    
    if [ $? -eq 0 ]; then
        log_success "Unit tests passed!"
    else
        log_error "Unit tests failed!"
        return 1
    fi
}

run_integration_tests() {
    log_info "Running integration tests..."
    ./gradlew integrationTest --continue
    
    if [ $? -eq 0 ]; then
        log_success "Integration tests passed!"
    else
        log_error "Integration tests failed!"
        return 1
    fi
}

run_security_tests() {
    log_info "Running security tests..."
    ./gradlew securityTest --continue
    
    if [ $? -eq 0 ]; then
        log_success "Security tests passed!"
    else
        log_error "Security tests failed!"
        return 1
    fi
}

run_e2e_tests() {
    log_info "Running end-to-end tests..."
    ./gradlew e2eTest --continue
    
    if [ $? -eq 0 ]; then
        log_success "End-to-end tests passed!"
    else
        log_error "End-to-end tests failed!"
        return 1
    fi
}

run_performance_tests() {
    log_info "Running performance tests..."
    ./gradlew performanceTest --continue
    
    if [ $? -eq 0 ]; then
        log_success "Performance tests passed!"
    else
        log_warning "Performance tests failed (non-blocking)"
    fi
}

generate_test_reports() {
    log_info "Generating test reports..."
    ./gradlew jacocoTestReport
    
    if [ -d "$REPORTS_DIR" ]; then
        log_success "Test reports generated in $REPORTS_DIR"
        
        # Display coverage summary if available
        if [ -f "$REPORTS_DIR/jacoco/test/html/index.html" ]; then
            log_info "Code coverage report: $REPORTS_DIR/jacoco/test/html/index.html"
        fi
        
        # Display test results summary
        if [ -d "$TEST_RESULTS_DIR" ]; then
            local total_tests=0
            local failed_tests=0
            
            for result_file in "$TEST_RESULTS_DIR"/**/TEST-*.xml; do
                if [ -f "$result_file" ]; then
                    local tests=$(grep -o 'tests="[0-9]*"' "$result_file" | grep -o '[0-9]*' || echo "0")
                    local failures=$(grep -o 'failures="[0-9]*"' "$result_file" | grep -o '[0-9]*' || echo "0")
                    local errors=$(grep -o 'errors="[0-9]*"' "$result_file" | grep -o '[0-9]*' || echo "0")
                    
                    total_tests=$((total_tests + tests))
                    failed_tests=$((failed_tests + failures + errors))
                fi
            done
            
            log_info "Test Summary: $((total_tests - failed_tests))/$total_tests tests passed"
            
            if [ $failed_tests -eq 0 ]; then
                log_success "All tests passed!"
            else
                log_error "$failed_tests tests failed"
            fi
        fi
    fi
}

show_service_logs() {
    log_info "Showing service logs for troubleshooting..."
    docker-compose -f "$TEST_INFRASTRUCTURE_COMPOSE" logs --tail=50
}

print_help() {
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  --unit-only         Run only unit tests"
    echo "  --integration-only  Run only integration tests"
    echo "  --security-only     Run only security tests"
    echo "  --e2e-only         Run only end-to-end tests"
    echo "  --performance-only Run only performance tests"
    echo "  --no-infrastructure Skip starting test infrastructure"
    echo "  --skip-cleanup     Skip cleanup after tests"
    echo "  --show-logs        Show service logs after tests"
    echo "  --help             Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                           # Run full test suite"
    echo "  $0 --integration-only        # Run only integration tests"
    echo "  $0 --no-infrastructure       # Run tests without starting infrastructure"
    echo "  $0 --skip-cleanup --show-logs # Run tests and show logs without cleanup"
}

# Main execution
main() {
    local run_unit=true
    local run_integration=true
    local run_security=true
    local run_e2e=true
    local run_performance=true
    local start_infrastructure=true
    local cleanup_after=true
    local show_logs=false
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --unit-only)
                run_integration=false
                run_security=false
                run_e2e=false
                run_performance=false
                start_infrastructure=false
                shift
                ;;
            --integration-only)
                run_unit=false
                run_security=false
                run_e2e=false
                run_performance=false
                shift
                ;;
            --security-only)
                run_unit=false
                run_integration=false
                run_e2e=false
                run_performance=false
                shift
                ;;
            --e2e-only)
                run_unit=false
                run_integration=false
                run_security=false
                run_performance=false
                shift
                ;;
            --performance-only)
                run_unit=false
                run_integration=false
                run_security=false
                run_e2e=false
                shift
                ;;
            --no-infrastructure)
                start_infrastructure=false
                shift
                ;;
            --skip-cleanup)
                cleanup_after=false
                shift
                ;;
            --show-logs)
                show_logs=true
                shift
                ;;
            --help)
                print_help
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                print_help
                exit 1
                ;;
        esac
    done
    
    print_banner
    
    # Ensure we're in the correct directory
    if [ ! -f "$GRADLE_BUILD_FILE" ]; then
        log_error "Must be run from the integration test directory"
        exit 1
    fi
    
    # Cleanup any existing test infrastructure
    cleanup_test_infrastructure
    
    # Start test infrastructure if requested
    if [ "$start_infrastructure" = true ]; then
        start_test_infrastructure
    fi
    
    local test_exit_code=0
    
    # Run tests based on options
    if [ "$run_unit" = true ]; then
        run_unit_tests || test_exit_code=1
    fi
    
    if [ "$run_integration" = true ]; then
        run_integration_tests || test_exit_code=1
    fi
    
    if [ "$run_security" = true ]; then
        run_security_tests || test_exit_code=1
    fi
    
    if [ "$run_e2e" = true ]; then
        run_e2e_tests || test_exit_code=1
    fi
    
    if [ "$run_performance" = true ]; then
        run_performance_tests || true  # Performance tests are non-blocking
    fi
    
    # Generate reports
    generate_test_reports
    
    # Show logs if requested
    if [ "$show_logs" = true ]; then
        show_service_logs
    fi
    
    # Cleanup if requested
    if [ "$cleanup_after" = true ]; then
        cleanup_test_infrastructure
    fi
    
    # Final status
    echo ""
    if [ $test_exit_code -eq 0 ]; then
        log_success "All requested tests completed successfully!"
    else
        log_error "Some tests failed. Check the reports for details."
    fi
    
    exit $test_exit_code
}

# Trap to ensure cleanup on script exit
trap 'cleanup_test_infrastructure' EXIT

# Run main function with all arguments
main "$@"
