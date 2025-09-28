#!/bin/bash

# Security Vulnerability Scanner
# Scans the fintech API for common security issues

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_DIR="/Users/ernie-dev/Documents/fintech-api"
SCAN_RESULTS_DIR="$PROJECT_DIR/security-scan-results"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

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
    echo "              FINTECH API SECURITY VULNERABILITY SCANNER"
    echo "=================================================================="
    echo -e "${NC}"
}

# Create results directory
mkdir -p "$SCAN_RESULTS_DIR"

# Initialize scan report
SCAN_REPORT="$SCAN_RESULTS_DIR/security_scan_report_$TIMESTAMP.md"

cat > "$SCAN_REPORT" << EOF
# Fintech API Security Scan Report
**Scan Date:** $(date)
**Project:** Fintech API Platform

## Executive Summary

This report contains the results of a comprehensive security vulnerability scan of the Fintech API platform.

---

EOF

# Scan for hardcoded credentials
scan_hardcoded_credentials() {
    log_info "Scanning for hardcoded credentials..."
    
    echo "## 🔒 Hardcoded Credentials Scan" >> "$SCAN_REPORT"
    echo "" >> "$SCAN_REPORT"
    
    # Define patterns to search for
    declare -a patterns=(
        "password.*=.*['\"][^'\"]*['\"]"
        "secret.*=.*['\"][^'\"]*['\"]"
        "key.*=.*['\"][^'\"]*['\"]"
        "token.*=.*['\"][^'\"]*['\"]"
        "api_key.*=.*['\"][^'\"]*['\"]"
        "private_key.*=.*['\"][^'\"]*['\"]"
    )
    
    local found_issues=0
    
    for pattern in "${patterns[@]}"; do
        echo "### Searching for pattern: \`$pattern\`" >> "$SCAN_REPORT"
        echo "" >> "$SCAN_REPORT"
        echo "\`\`\`" >> "$SCAN_REPORT"
        
        # Search in configuration files
        find "$PROJECT_DIR" -type f \( -name "*.yml" -o -name "*.yaml" -o -name "*.properties" -o -name "*.json" \) \
            -not -path "*/node_modules/*" \
            -not -path "*/.git/*" \
            -not -path "*/build/*" \
            -not -path "*/target/*" \
            -exec grep -Hn -i "$pattern" {} \; >> "$SCAN_REPORT" 2>/dev/null || true
        
        # Search in Java files
        find "$PROJECT_DIR" -type f -name "*.java" \
            -not -path "*/.git/*" \
            -not -path "*/build/*" \
            -not -path "*/target/*" \
            -exec grep -Hn -i "$pattern" {} \; >> "$SCAN_REPORT" 2>/dev/null || true
        
        echo "\`\`\`" >> "$SCAN_REPORT"
        echo "" >> "$SCAN_REPORT"
        
        # Count issues
        local count=$(find "$PROJECT_DIR" -type f \( -name "*.yml" -o -name "*.yaml" -o -name "*.properties" -o -name "*.json" -o -name "*.java" \) \
            -not -path "*/node_modules/*" \
            -not -path "*/.git/*" \
            -not -path "*/build/*" \
            -not -path "*/target/*" \
            -exec grep -l -i "$pattern" {} \; 2>/dev/null | wc -l)
        
        if [ "$count" -gt 0 ]; then
            found_issues=$((found_issues + count))
            log_warning "Found $count files with potential credential exposure"
        fi
    done
    
    if [ $found_issues -gt 0 ]; then
        echo "**🚨 CRITICAL:** Found $found_issues potential credential exposures" >> "$SCAN_REPORT"
        log_error "Found $found_issues potential credential exposures"
    else
        echo "**✅ PASSED:** No hardcoded credentials detected" >> "$SCAN_REPORT"
        log_success "No hardcoded credentials detected"
    fi
    
    echo "" >> "$SCAN_REPORT"
}

# Scan for SQL injection vulnerabilities
scan_sql_injection() {
    log_info "Scanning for SQL injection vulnerabilities..."
    
    echo "## 💉 SQL Injection Vulnerability Scan" >> "$SCAN_REPORT"
    echo "" >> "$SCAN_REPORT"
    
    # Search for dangerous SQL patterns
    declare -a sql_patterns=(
        "executeQuery.*\+.*"
        "createStatement.*"
        "prepareStatement.*\+.*"
        "\".*SELECT.*\".*\+.*"
        "\".*INSERT.*\".*\+.*"
        "\".*UPDATE.*\".*\+.*"
        "\".*DELETE.*\".*\+.*"
    )
    
    local found_issues=0
    
    for pattern in "${sql_patterns[@]}"; do
        echo "### Searching for SQL pattern: \`$pattern\`" >> "$SCAN_REPORT"
        echo "" >> "$SCAN_REPORT"
        echo "\`\`\`" >> "$SCAN_REPORT"
        
        find "$PROJECT_DIR" -type f -name "*.java" \
            -not -path "*/.git/*" \
            -not -path "*/build/*" \
            -not -path "*/target/*" \
            -exec grep -Hn "$pattern" {} \; >> "$SCAN_REPORT" 2>/dev/null || true
        
        echo "\`\`\`" >> "$SCAN_REPORT"
        echo "" >> "$SCAN_REPORT"
        
        local count=$(find "$PROJECT_DIR" -type f -name "*.java" \
            -not -path "*/.git/*" \
            -not -path "*/build/*" \
            -not -path "*/target/*" \
            -exec grep -l "$pattern" {} \; 2>/dev/null | wc -l)
        
        if [ "$count" -gt 0 ]; then
            found_issues=$((found_issues + count))
            log_warning "Found $count files with potential SQL injection vulnerabilities"
        fi
    done
    
    if [ $found_issues -gt 0 ]; then
        echo "**🚨 CRITICAL:** Found $found_issues potential SQL injection vulnerabilities" >> "$SCAN_REPORT"
        log_error "Found $found_issues potential SQL injection vulnerabilities"
    else
        echo "**✅ PASSED:** No SQL injection vulnerabilities detected" >> "$SCAN_REPORT"
        log_success "No SQL injection vulnerabilities detected"
    fi
    
    echo "" >> "$SCAN_REPORT"
}

# Scan for SSRF vulnerabilities
scan_ssrf_vulnerabilities() {
    log_info "Scanning for SSRF vulnerabilities..."
    
    echo "## 🌐 Server-Side Request Forgery (SSRF) Scan" >> "$SCAN_REPORT"
    echo "" >> "$SCAN_REPORT"
    
    # Search for HTTP request patterns without validation
    declare -a ssrf_patterns=(
        "HttpURLConnection.*openConnection"
        "RestTemplate.*getForObject"
        "WebClient.*retrieve"
        "URL.*openStream"
        "HttpClient.*execute"
        "OkHttpClient.*newCall"
    )
    
    local found_issues=0
    
    for pattern in "${ssrf_patterns[@]}"; do
        echo "### Searching for HTTP request pattern: \`$pattern\`" >> "$SCAN_REPORT"
        echo "" >> "$SCAN_REPORT"
        echo "\`\`\`" >> "$SCAN_REPORT"
        
        find "$PROJECT_DIR" -type f -name "*.java" \
            -not -path "*/.git/*" \
            -not -path "*/build/*" \
            -not -path "*/target/*" \
            -exec grep -Hn "$pattern" {} \; >> "$SCAN_REPORT" 2>/dev/null || true
        
        echo "\`\`\`" >> "$SCAN_REPORT"
        echo "" >> "$SCAN_REPORT"
        
        local count=$(find "$PROJECT_DIR" -type f -name "*.java" \
            -not -path "*/.git/*" \
            -not -path "*/build/*" \
            -not -path "*/target/*" \
            -exec grep -l "$pattern" {} \; 2>/dev/null | wc -l)
        
        if [ "$count" -gt 0 ]; then
            found_issues=$((found_issues + count))
            log_warning "Found $count files with potential SSRF vulnerabilities"
        fi
    done
    
    if [ $found_issues -gt 0 ]; then
        echo "**⚠️ WARNING:** Found $found_issues potential SSRF vulnerabilities - Review for proper URL validation" >> "$SCAN_REPORT"
        log_warning "Found $found_issues potential SSRF vulnerabilities"
    else
        echo "**✅ PASSED:** No obvious SSRF vulnerabilities detected" >> "$SCAN_REPORT"
        log_success "No obvious SSRF vulnerabilities detected"
    fi
    
    echo "" >> "$SCAN_REPORT"
}

# Scan for insecure logging
scan_insecure_logging() {
    log_info "Scanning for insecure logging practices..."
    
    echo "## 📝 Insecure Logging Scan" >> "$SCAN_REPORT"
    echo "" >> "$SCAN_REPORT"
    
    # Search for System.out.print and potential sensitive data logging
    declare -a logging_patterns=(
        "System\.out\.print"
        "System\.err\.print"
        "printStackTrace"
        "log.*password"
        "log.*secret"
        "log.*token"
        "log.*ssn"
        "log.*credit.*card"
    )
    
    local found_issues=0
    
    for pattern in "${logging_patterns[@]}"; do
        echo "### Searching for logging pattern: \`$pattern\`" >> "$SCAN_REPORT"
        echo "" >> "$SCAN_REPORT"
        echo "\`\`\`" >> "$SCAN_REPORT"
        
        find "$PROJECT_DIR" -type f -name "*.java" \
            -not -path "*/.git/*" \
            -not -path "*/build/*" \
            -not -path "*/target/*" \
            -exec grep -Hn -i "$pattern" {} \; >> "$SCAN_REPORT" 2>/dev/null || true
        
        echo "\`\`\`" >> "$SCAN_REPORT"
        echo "" >> "$SCAN_REPORT"
        
        local count=$(find "$PROJECT_DIR" -type f -name "*.java" \
            -not -path "*/.git/*" \
            -not -path "*/build/*" \
            -not -path "*/target/*" \
            -exec grep -l -i "$pattern" {} \; 2>/dev/null | wc -l)
        
        if [ "$count" -gt 0 ]; then
            found_issues=$((found_issues + count))
            log_warning "Found $count files with potential insecure logging"
        fi
    done
    
    if [ $found_issues -gt 0 ]; then
        echo "**⚠️ WARNING:** Found $found_issues potential insecure logging issues" >> "$SCAN_REPORT"
        log_warning "Found $found_issues potential insecure logging issues"
    else
        echo "**✅ PASSED:** No insecure logging detected" >> "$SCAN_REPORT"
        log_success "No insecure logging detected"
    fi
    
    echo "" >> "$SCAN_REPORT"
}

# Scan for missing input validation
scan_input_validation() {
    log_info "Scanning for missing input validation..."
    
    echo "## 🛡️ Input Validation Scan" >> "$SCAN_REPORT"
    echo "" >> "$SCAN_REPORT"
    
    # Search for endpoints without validation annotations
    echo "### REST Controllers without validation:" >> "$SCAN_REPORT"
    echo "\`\`\`" >> "$SCAN_REPORT"
    
    find "$PROJECT_DIR" -type f -name "*Controller.java" \
        -not -path "*/.git/*" \
        -not -path "*/build/*" \
        -not -path "*/target/*" \
        -exec grep -L "@Valid\|@Validated" {} \; >> "$SCAN_REPORT" 2>/dev/null || true
    
    echo "\`\`\`" >> "$SCAN_REPORT"
    echo "" >> "$SCAN_REPORT"
    
    # Count controllers without validation
    local no_validation_count=$(find "$PROJECT_DIR" -type f -name "*Controller.java" \
        -not -path "*/.git/*" \
        -not -path "*/build/*" \
        -not -path "*/target/*" \
        -exec grep -L "@Valid\|@Validated" {} \; 2>/dev/null | wc -l)
    
    if [ "$no_validation_count" -gt 0 ]; then
        echo "**⚠️ WARNING:** Found $no_validation_count controllers potentially missing input validation" >> "$SCAN_REPORT"
        log_warning "Found $no_validation_count controllers potentially missing input validation"
    else
        echo "**✅ PASSED:** All controllers appear to have validation annotations" >> "$SCAN_REPORT"
        log_success "All controllers appear to have validation annotations"
    fi
    
    echo "" >> "$SCAN_REPORT"
}

# Scan configuration security
scan_configuration_security() {
    log_info "Scanning configuration security..."
    
    echo "## ⚙️ Configuration Security Scan" >> "$SCAN_REPORT"
    echo "" >> "$SCAN_REPORT"
    
    # Check for insecure configurations
    echo "### Configuration files with potential security issues:" >> "$SCAN_REPORT"
    echo "\`\`\`" >> "$SCAN_REPORT"
    
    # Look for debug mode enabled in production
    find "$PROJECT_DIR" -type f \( -name "*.yml" -o -name "*.yaml" -o -name "*.properties" \) \
        -not -path "*/.git/*" \
        -not -path "*/build/*" \
        -not -path "*/target/*" \
        -exec grep -Hn "debug.*true\|show-sql.*true\|ddl-auto.*create" {} \; >> "$SCAN_REPORT" 2>/dev/null || true
    
    echo "\`\`\`" >> "$SCAN_REPORT"
    echo "" >> "$SCAN_REPORT"
    
    # Check SSL/TLS configuration
    echo "### SSL/TLS Configuration Check:" >> "$SCAN_REPORT"
    echo "\`\`\`" >> "$SCAN_REPORT"
    
    find "$PROJECT_DIR" -type f \( -name "*.yml" -o -name "*.yaml" -o -name "*.properties" \) \
        -not -path "*/.git/*" \
        -not -path "*/build/*" \
        -not -path "*/target/*" \
        -exec grep -Hn "ssl\|tls\|https" {} \; >> "$SCAN_REPORT" 2>/dev/null || true
    
    echo "\`\`\`" >> "$SCAN_REPORT"
    echo "" >> "$SCAN_REPORT"
    
    log_success "Configuration security scan completed"
}

# Generate security recommendations
generate_recommendations() {
    log_info "Generating security recommendations..."
    
    cat >> "$SCAN_REPORT" << EOF
## 🔧 Security Recommendations

### Immediate Actions Required:

1. **Replace all hardcoded credentials** with environment variables
   - Use \`.env\` files for local development
   - Use proper secrets management in production (HashiCorp Vault, AWS Secrets Manager)
   - Rotate all exposed credentials immediately

2. **Fix SQL injection vulnerabilities**
   - Use parameterized queries (PreparedStatement) exclusively
   - Implement input validation for all user inputs
   - Use the SQLInjectionPrevention utility class

3. **Implement SSRF protection**
   - Validate all external URLs using SSRFPrevention utility
   - Whitelist allowed external domains
   - Block internal IP ranges and localhost

4. **Improve logging security**
   - Replace System.out.print with proper logging framework
   - Implement data masking for sensitive information
   - Use SecureDataEncryption for PII logging

5. **Enable comprehensive input validation**
   - Add @Valid and @Validated annotations to all controller methods
   - Implement custom validators for business logic
   - Sanitize all user inputs

### Production Security Checklist:

- [ ] All credentials stored in secure secrets management
- [ ] SSL/TLS enabled for all communication
- [ ] Database connections encrypted
- [ ] API rate limiting enabled
- [ ] Input validation on all endpoints
- [ ] Proper error handling (no stack traces in responses)
- [ ] Security headers configured
- [ ] Audit logging enabled
- [ ] Regular security scanning in CI/CD
- [ ] Dependency vulnerability scanning

### Monitoring and Alerting:

- [ ] Set up security incident alerting
- [ ] Monitor for failed authentication attempts
- [ ] Track API abuse patterns
- [ ] Log security events to SIEM
- [ ] Implement anomaly detection

### Development Process:

- [ ] Security code review required for all changes
- [ ] Automated security testing in CI/CD
- [ ] Regular penetration testing
- [ ] Security training for developers
- [ ] Secure coding guidelines enforcement

EOF

    log_success "Security recommendations generated"
}

# Main execution
main() {
    print_banner
    
    log_info "Starting comprehensive security scan..."
    log_info "Results will be saved to: $SCAN_REPORT"
    
    # Run all scans
    scan_hardcoded_credentials
    scan_sql_injection
    scan_ssrf_vulnerabilities
    scan_insecure_logging
    scan_input_validation
    scan_configuration_security
    generate_recommendations
    
    # Finalize report
    cat >> "$SCAN_REPORT" << EOF

---
**Scan completed:** $(date)
**Report generated by:** Fintech API Security Scanner
**Next scan recommended:** $(date -d "+1 week")

EOF
    
    log_success "Security scan completed!"
    log_info "Detailed report available at: $SCAN_REPORT"
    
    # Display summary
    echo ""
    echo "Security Scan Summary:"
    echo "====================="
    
    # Count total issues
    local total_critical=$(grep -c "🚨 CRITICAL" "$SCAN_REPORT" || echo "0")
    local total_warnings=$(grep -c "⚠️ WARNING" "$SCAN_REPORT" || echo "0")
    local total_passed=$(grep -c "✅ PASSED" "$SCAN_REPORT" || echo "0")
    
    echo "Critical Issues: $total_critical"
    echo "Warnings: $total_warnings"
    echo "Passed Checks: $total_passed"
    
    if [ "$total_critical" -gt 0 ]; then
        log_error "CRITICAL security issues found! Immediate action required."
        exit 1
    elif [ "$total_warnings" -gt 0 ]; then
        log_warning "Security warnings found. Review and address before production deployment."
        exit 1
    else
        log_success "No critical security issues detected."
        exit 0
    fi
}

# Run main function
main "$@"
