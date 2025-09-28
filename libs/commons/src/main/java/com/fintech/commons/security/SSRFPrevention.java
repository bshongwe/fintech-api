package com.fintech.commons.security;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Server-Side Request Forgery (SSRF) Prevention Utility
 * 
 * Prevents SSRF attacks by:
 * - URL validation and whitelisting
 * - Internal network detection
 * - Dangerous protocol blocking
 * - Host header validation
 * 
 * CRITICAL: Always validate external URLs before making HTTP requests
 * 
 * @author Fintech Security Team
 */
@Component
public class SSRFPrevention {
    
    private static final Logger log = LoggerFactory.getLogger(SSRFPrevention.class);
    
    // Allowed external domains (whitelist approach)
    private static final List<String> ALLOWED_DOMAINS = Arrays.asList(
        "api.standardbank.co.za",
        "openapi.investec.com", 
        "api.capitecbank.co.za",
        "api.absa.co.za",
        "api.sandbox.standardbank.co.za",
        "openapi.sandbox.investec.com",
        "api.sandbox.capitecbank.co.za",
        "api.sandbox.absa.co.za"
    );
    
    // Blocked protocols
    private static final List<String> BLOCKED_PROTOCOLS = Arrays.asList(
        "file", "ftp", "gopher", "ldap", "dict", "jar", "netdoc"
    );
    
    // Internal/Private IP ranges (RFC 1918, RFC 3927, etc.)
    private static final Pattern[] INTERNAL_IP_PATTERNS = {
        Pattern.compile("^10\\..*"),                    // 10.0.0.0/8
        Pattern.compile("^172\\.(1[6-9]|2[0-9]|3[01])\\..*"), // 172.16.0.0/12
        Pattern.compile("^192\\.168\\..*"),             // 192.168.0.0/16
        Pattern.compile("^127\\..*"),                   // 127.0.0.0/8 (localhost)
        Pattern.compile("^169\\.254\\..*"),             // 169.254.0.0/16 (link-local)
        Pattern.compile("^0\\.0\\.0\\.0$"),            // 0.0.0.0
        Pattern.compile("^224\\..*"),                   // 224.0.0.0/4 (multicast)
        Pattern.compile("^240\\..*")                    // 240.0.0.0/4 (reserved)
    };
    
    // Common localhost variations
    private static final List<String> LOCALHOST_VARIATIONS = Arrays.asList(
        "localhost", "0.0.0.0", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1"
    );
    
    /**
     * Validate URL for SSRF vulnerabilities
     * 
     * @param urlString URL to validate
     * @return true if URL is safe, false if potentially dangerous
     */
    public boolean validateUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            log.warn("Empty or null URL provided for validation");
            return false;
        }
        
        try {
            URL url = new URL(urlString.trim());
            
            // Check protocol
            if (!isProtocolAllowed(url.getProtocol())) {
                log.warn("Blocked protocol detected in URL: {}", url.getProtocol());
                return false;
            }
            
            // Check if it's an internal/private IP
            if (isInternalHost(url.getHost())) {
                log.warn("Internal/private host detected in URL: {}", url.getHost());
                return false;
            }
            
            // Check against domain whitelist  
            if (!isDomainAllowed(url.getHost())) {
                log.warn("Domain not in whitelist: {}", url.getHost());
                return false;
            }
            
            // Check for suspicious ports
            if (isSuspiciousPort(url.getPort())) {
                log.warn("Suspicious port detected in URL: {}", url.getPort());
                return false;
            }
            
            return true;
            
        } catch (MalformedURLException e) {
            log.warn("Malformed URL provided: {}", urlString, e);
            return false;
        }
    }
    
    /**
     * Validate and sanitize URL for safe external requests
     * 
     * @param urlString URL to validate and sanitize
     * @return Sanitized URL if safe, null if dangerous
     */
    public String validateAndSanitizeUrl(String urlString) {
        if (!validateUrl(urlString)) {
            return null;
        }
        
        try {
            URL url = new URL(urlString.trim());
            
            // Reconstruct URL to prevent bypass attempts
            String sanitizedUrl = url.getProtocol() + "://" + url.getHost();
            
            // Add port if specified and safe
            if (url.getPort() != -1 && !isSuspiciousPort(url.getPort())) {
                sanitizedUrl += ":" + url.getPort();
            }
            
            // Add path if present
            if (url.getPath() != null && !url.getPath().isEmpty()) {
                sanitizedUrl += url.getPath();
            }
            
            // Add query parameters if present (validate them)
            if (url.getQuery() != null && !url.getQuery().isEmpty()) {
                String sanitizedQuery = sanitizeQueryParameters(url.getQuery());
                if (sanitizedQuery != null) {
                    sanitizedUrl += "?" + sanitizedQuery;
                }
            }
            
            log.debug("URL sanitized successfully: {}", sanitizedUrl);
            return sanitizedUrl;
            
        } catch (MalformedURLException e) {
            log.error("Failed to sanitize URL: {}", urlString, e);
            return null;
        }
    }
    
    /**
     * Check if protocol is allowed
     * 
     * @param protocol Protocol to check
     * @return true if allowed, false otherwise
     */
    private boolean isProtocolAllowed(String protocol) {
        if (protocol == null) {
            return false;
        }
        
        String lowerProtocol = protocol.toLowerCase();
        
        // Only allow HTTP and HTTPS
        if (!lowerProtocol.equals("http") && !lowerProtocol.equals("https")) {
            return false;
        }
        
        // Check against blocked protocols list
        return !BLOCKED_PROTOCOLS.contains(lowerProtocol);
    }
    
    /**
     * Check if host is internal/private
     * 
     * @param host Host to check
     * @return true if internal, false if external
     */
    private boolean isInternalHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            return true; // Treat empty host as internal
        }
        
        String lowerHost = host.toLowerCase().trim();
        
        // Check localhost variations
        if (LOCALHOST_VARIATIONS.contains(lowerHost)) {
            return true;
        }
        
        // Check internal IP patterns
        for (Pattern pattern : INTERNAL_IP_PATTERNS) {
            if (pattern.matcher(lowerHost).matches()) {
                return true;
            }
        }
        
        // Check for IPv6 localhost
        if (lowerHost.startsWith("::") || lowerHost.contains("::1")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if domain is in the allowed list
     * 
     * @param host Host to check
     * @return true if allowed, false otherwise
     */
    private boolean isDomainAllowed(String host) {
        if (host == null || host.trim().isEmpty()) {
            return false;
        }
        
        String lowerHost = host.toLowerCase().trim();
        
        // Exact match
        if (ALLOWED_DOMAINS.contains(lowerHost)) {
            return true;
        }
        
        // Check subdomains of allowed domains
        for (String allowedDomain : ALLOWED_DOMAINS) {
            if (lowerHost.endsWith("." + allowedDomain)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if port is suspicious
     * 
     * @param port Port number
     * @return true if suspicious, false if safe
     */
    private boolean isSuspiciousPort(int port) {
        if (port == -1) {
            return false; // Default port is OK
        }
        
        // Allow standard web ports
        if (port == 80 || port == 443 || port == 8080 || port == 8443) {
            return false;
        }
        
        // Block well-known service ports that could be abused
        int[] suspiciousPorts = {
            22,    // SSH
            23,    // Telnet
            25,    // SMTP
            53,    // DNS
            110,   // POP3
            143,   // IMAP
            993,   // IMAPS
            995,   // POP3S
            3389,  // RDP
            5432,  // PostgreSQL
            6379,  // Redis
            9092,  // Kafka
            27017  // MongoDB
        };
        
        for (int suspiciousPort : suspiciousPorts) {
            if (port == suspiciousPort) {
                return true;
            }
        }
        
        // Block ports outside reasonable web range
        return port < 80 || port > 65535;
    }
    
    /**
     * Sanitize query parameters to prevent injection
     * 
     * @param query Query string to sanitize
     * @return Sanitized query string or null if dangerous
     */
    private String sanitizeQueryParameters(String query) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }
        
        // Remove potentially dangerous characters
        String sanitized = query.replaceAll("[<>&\"']", "");
        
        // Check for injection attempts
        String upperQuery = sanitized.toUpperCase();
        String[] dangerousPatterns = {"SCRIPT", "JAVASCRIPT", "VBSCRIPT", "ONLOAD", "ONERROR"};
        
        for (String pattern : dangerousPatterns) {
            if (upperQuery.contains(pattern)) {
                log.warn("Dangerous pattern '{}' detected in query parameters", pattern);
                return null;
            }
        }
        
        return sanitized;
    }
    
    /**
     * Validate Host header to prevent Host header injection
     * 
     * @param hostHeader Host header value
     * @return true if valid, false if suspicious
     */
    public boolean validateHostHeader(String hostHeader) {
        if (hostHeader == null || hostHeader.trim().isEmpty()) {
            return false;
        }
        
        try {
            // Parse as URI to validate format
            URI uri = new URI("http://" + hostHeader.trim());
            String host = uri.getHost();
            
            if (host == null) {
                return false;
            }
            
            // Check if it's our allowed domain
            return isDomainAllowed(host) || host.equals("localhost") || host.equals("127.0.0.1");
            
        } catch (URISyntaxException e) {
            log.warn("Invalid Host header format: {}", hostHeader, e);
            return false;
        }
    }
    
    /**
     * Create safe HTTP client configuration
     * 
     * @return Configuration parameters for safe HTTP requests
     */
    public HttpClientConfig getSafeHttpClientConfig() {
        return HttpClientConfig.builder()
            .connectTimeout(10000)      // 10 seconds
            .readTimeout(30000)         // 30 seconds
            .followRedirects(false)     // Disable redirects to prevent bypass
            .maxRedirects(0)
            .userAgent("FintechAPI/1.0")
            .build();
    }
    
    /**
     * Log SSRF attempt for security monitoring
     * 
     * @param urlString Suspicious URL
     * @param userContext User context (ID, IP, etc.)
     * @param reason Reason for blocking
     */
    public void logSSRFAttempt(String urlString, String userContext, String reason) {
        log.error("SECURITY INCIDENT: Potential SSRF attempt detected - " +
                "URL: '{}', User Context: '{}', Reason: '{}'", 
                urlString, userContext, reason);
        
        // In production, this should also:
        // 1. Send alert to security team
        // 2. Increment security metrics
        // 3. Consider blocking the user temporarily
        // 4. Log to security event system (SIEM)
    }
    
    /**
     * HTTP Client Configuration class
     */
    public static class HttpClientConfig {
        private final int connectTimeout;
        private final int readTimeout;
        private final boolean followRedirects;
        private final int maxRedirects;
        private final String userAgent;
        
        private HttpClientConfig(Builder builder) {
            this.connectTimeout = builder.connectTimeout;
            this.readTimeout = builder.readTimeout;
            this.followRedirects = builder.followRedirects;
            this.maxRedirects = builder.maxRedirects;
            this.userAgent = builder.userAgent;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public int getConnectTimeout() { return connectTimeout; }
        public int getReadTimeout() { return readTimeout; }
        public boolean isFollowRedirects() { return followRedirects; }
        public int getMaxRedirects() { return maxRedirects; }
        public String getUserAgent() { return userAgent; }
        
        public static class Builder {
            private int connectTimeout = 10000;
            private int readTimeout = 30000;
            private boolean followRedirects = false;
            private int maxRedirects = 0;
            private String userAgent = "FintechAPI/1.0";
            
            public Builder connectTimeout(int connectTimeout) {
                this.connectTimeout = connectTimeout;
                return this;
            }
            
            public Builder readTimeout(int readTimeout) {
                this.readTimeout = readTimeout;
                return this;
            }
            
            public Builder followRedirects(boolean followRedirects) {
                this.followRedirects = followRedirects;
                return this;
            }
            
            public Builder maxRedirects(int maxRedirects) {
                this.maxRedirects = maxRedirects;
                return this;
            }
            
            public Builder userAgent(String userAgent) {
                this.userAgent = userAgent;
                return this;
            }
            
            public HttpClientConfig build() {
                return new HttpClientConfig(this);
            }
        }
    }
}
