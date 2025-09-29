package com.fintech.commons.security.util;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Server-Side Request Forgery (SSRF) Prevention Utility
 * 
 * Provides methods to validate URLs and IP addresses to prevent SSRF attacks
 * by blocking requests to internal/private network ranges and localhost.
 */
@Component
public class SSRFPrevention {
    
    private static final Logger log = LoggerFactory.getLogger(SSRFPrevention.class);
    
    // Private IP address ranges (RFC 1918, RFC 3927, RFC 4193, etc.)
    private static final List<String> PRIVATE_IP_RANGES = Arrays.asList(
        "10.0.0.0/8",           // Class A private
        "172.16.0.0/12",        // Class B private
        "192.168.0.0/16",       // Class C private
        "169.254.0.0/16",       // Link-local
        "127.0.0.0/8",          // Loopback
        "::1/128",              // IPv6 loopback
        "fc00::/7",             // IPv6 private
        "fe80::/10"             // IPv6 link-local
    );
    
    // Dangerous URL patterns
    private static final Pattern DANGEROUS_PROTOCOLS = Pattern.compile(
        "^(file|ftp|gopher|dict|ldap|ldaps|tftp):", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern LOCALHOST_PATTERNS = Pattern.compile(
        "(localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0|\\[::\\]|\\[::1\\])",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Validates if a URL is safe for external requests (not SSRF vulnerable)
     */
    public boolean isUrlSafe(String url) {
        if (url == null || url.trim().isEmpty()) {
            log.warn("SSRF Prevention: Empty or null URL provided");
            return false;
        }
        
        try {
            // Check for dangerous protocols
            if (DANGEROUS_PROTOCOLS.matcher(url).find()) {
                log.warn("SSRF Prevention: Dangerous protocol detected in URL: {}", 
                    sanitizeUrlForLogging(url));
                return false;
            }
            
            // Check for localhost patterns
            if (LOCALHOST_PATTERNS.matcher(url).find()) {
                log.warn("SSRF Prevention: Localhost pattern detected in URL: {}", 
                    sanitizeUrlForLogging(url));
                return false;
            }
            
            // Extract and validate host
            java.net.URL parsedUrl = new java.net.URL(url);
            String host = parsedUrl.getHost();
            
            return isHostSafe(host);
            
        } catch (Exception e) {
            log.error("SSRF Prevention: Error parsing URL: {}", sanitizeUrlForLogging(url), e);
            return false;
        }
    }
    
    /**
     * Validates if a hostname/IP is safe for external requests
     */
    public boolean isHostSafe(String host) {
        if (host == null || host.trim().isEmpty()) {
            log.warn("SSRF Prevention: Empty or null host provided");
            return false;
        }
        
        try {
            InetAddress address = InetAddress.getByName(host);
            
            // Check if it's a private/internal address
            if (isPrivateAddress(address)) {
                log.warn("SSRF Prevention: Private/internal address blocked: {}", host);
                return false;
            }
            
            // Additional checks for special addresses
            if (address.isLoopbackAddress() || address.isLinkLocalAddress() || 
                address.isSiteLocalAddress() || address.isMulticastAddress()) {
                log.warn("SSRF Prevention: Special address type blocked: {}", host);
                return false;
            }
            
            return true;
            
        } catch (UnknownHostException e) {
            log.warn("SSRF Prevention: Unable to resolve host: {}", host);
            return false;
        } catch (Exception e) {
            log.error("SSRF Prevention: Error validating host: {}", host, e);
            return false;
        }
    }
    
    /**
     * Checks if an IP address is in private/internal ranges
     */
    private boolean isPrivateAddress(InetAddress address) {
        byte[] addr = address.getAddress();
        
        // IPv4 checks
        if (addr.length == 4) {
            return isPrivateIPv4(addr);
        }
        
        // IPv6 checks
        if (addr.length == 16) {
            return isPrivateIPv6(addr);
        }
        
        return false;
    }
    
    private boolean isPrivateIPv4(byte[] addr) {
        // 10.0.0.0/8
        if (addr[0] == 10) return true;
        
        // 172.16.0.0/12
        if (addr[0] == (byte) 172 && (addr[1] & 0xF0) == 16) return true;
        
        // 192.168.0.0/16
        if (addr[0] == (byte) 192 && addr[1] == (byte) 168) return true;
        
        // 127.0.0.0/8 (loopback)
        if (addr[0] == 127) return true;
        
        // 169.254.0.0/16 (link-local)
        if (addr[0] == (byte) 169 && addr[1] == (byte) 254) return true;
        
        return false;
    }
    
    private boolean isPrivateIPv6(byte[] addr) {
        // ::1 (loopback)
        if (isIPv6Loopback(addr)) return true;
        
        // fc00::/7 (unique local addresses)
        if ((addr[0] & 0xFE) == 0xFC) return true;
        
        // fe80::/10 (link-local)
        if (addr[0] == (byte) 0xFE && (addr[1] & 0xC0) == 0x80) return true;
        
        return false;
    }
    
    private boolean isIPv6Loopback(byte[] addr) {
        for (int i = 0; i < 15; i++) {
            if (addr[i] != 0) return false;
        }
        return addr[15] == 1;
    }
    
    /**
     * Sanitizes URL for safe logging (removes sensitive information)
     */
    private String sanitizeUrlForLogging(String url) {
        if (url == null) return "null";
        
        // Remove potential credentials from URL
        return url.replaceAll("://[^@]*@", "://***@");
    }
    
    /**
     * Validates multiple URLs in batch
     */
    public boolean areUrlsSafe(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return true; // Empty list is considered safe
        }
        
        return urls.stream().allMatch(this::isUrlSafe);
    }
}
