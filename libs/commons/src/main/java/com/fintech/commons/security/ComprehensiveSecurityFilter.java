package com.fintech.commons.security;

import com.fintech.commons.security.util.SSRFPrevention;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Comprehensive Security Filter
 * 
 * Provides additional security checks including:
 * - Rate limiting
 * - Request sanitization
 * - Suspicious pattern detection
 * - SSRF protection for external URLs
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ComprehensiveSecurityFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(ComprehensiveSecurityFilter.class);
    private final SSRFPrevention ssrfPrevention;
    
    // Rate limiting
    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    
    public ComprehensiveSecurityFilter(SSRFPrevention ssrfPrevention) {
        this.ssrfPrevention = ssrfPrevention;
    }
    private final ConcurrentHashMap<String, Long> lastResetTime = new ConcurrentHashMap<>();
    
    // Suspicious patterns
    private static final Pattern[] SUSPICIOUS_PATTERNS = {
            Pattern.compile("(?i).*(union|select|insert|update|delete|drop|exec|script).*"),
            Pattern.compile("(?i).*(javascript:|vbscript:|onload|onerror).*"),
            Pattern.compile("(?i).*(<script|</script>|<iframe|</iframe>).*"),
            Pattern.compile("(?i).*(cmd|powershell|bash|sh).*"),
            Pattern.compile("(?i).*(\\.\\./|\\.\\\\|\\\\\\.\\./).*") // Path traversal
    };
    
    // Blocked user agents
    private static final Pattern[] BLOCKED_USER_AGENTS = {
            Pattern.compile("(?i).*(sqlmap|nikto|nmap|masscan|zap|burp|havij).*"),
            Pattern.compile("(?i).*(bot|crawler|spider|scraper).*"),
            Pattern.compile("(?i).*(python-requests|curl|wget|httpie).*") // Allow legitimate tools in dev/test
    };
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        try {
            // 1. Rate limiting check
            if (!checkRateLimit(httpRequest, httpResponse)) {
                return;
            }
            
            // 2. User agent validation
            if (!validateUserAgent(httpRequest, httpResponse)) {
                return;
            }
            
            // 3. Request sanitization and suspicious pattern detection
            if (!validateRequestContent(httpRequest, httpResponse)) {
                return;
            }
            
            // 4. SSRF protection for URL parameters
            if (!validateUrlParameters(httpRequest, httpResponse)) {
                return;
            }
            
            // 5. Add security headers
            addSecurityHeaders(httpResponse);
            
            // Continue with the filter chain
            chain.doFilter(request, response);
            
        } catch (Exception e) {
            logger.error("Security filter error", e);
            httpResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            httpResponse.getWriter().write("{\"error\":\"Security validation failed\"}");
        }
    }
    
    /**
     * Rate limiting implementation
     */
    private boolean checkRateLimit(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        String clientIp = getClientIpAddress(request);
        long currentTime = System.currentTimeMillis();
        
        // Reset counter every minute
        lastResetTime.compute(clientIp, (key, lastTime) -> {
            if (lastTime == null || currentTime - lastTime > 60000) {
                requestCounts.put(clientIp, new AtomicInteger(0));
                return currentTime;
            }
            return lastTime;
        });
        
        // Check rate limit
        AtomicInteger count = requestCounts.computeIfAbsent(clientIp, k -> new AtomicInteger(0));
        if (count.incrementAndGet() > MAX_REQUESTS_PER_MINUTE) {
            logger.warn("Rate limit exceeded for IP: {} ({})", clientIp, count.get());
            response.setStatus(429); // 429 Too Many Requests
            response.setHeader("Retry-After", "60");
            response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate user agent
     */
    private boolean validateUserAgent(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            logger.warn("Request without User-Agent from IP: {}", getClientIpAddress(request));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"User-Agent header required\"}");
            return false;
        }
        
        // Check for blocked user agents (in production only)
        String activeProfile = System.getProperty("spring.profiles.active", "development");
        if ("production".equals(activeProfile)) {
            for (Pattern pattern : BLOCKED_USER_AGENTS) {
                if (pattern.matcher(userAgent).matches()) {
                    logger.warn("Blocked user agent: {} from IP: {}", userAgent, getClientIpAddress(request));
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Access denied\"}");
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Validate request content for suspicious patterns
     */
    private boolean validateRequestContent(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        // Check query parameters
        String queryString = request.getQueryString();
        if (queryString != null && containsSuspiciousContent(queryString)) {
            logger.warn("Suspicious query string detected from IP: {}", 
                    getClientIpAddress(request));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid request parameters\"}");
            return false;
        }
        
        // Check headers for suspicious content
        String referer = request.getHeader("Referer");
        if (referer != null && containsSuspiciousContent(referer)) {
            logger.warn("Suspicious referer detected from IP: {}", 
                    getClientIpAddress(request));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid referer\"}");
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate URL parameters for SSRF attempts
     */
    private boolean validateUrlParameters(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        // Check common URL parameter names
        String[] urlParams = {"url", "callback", "redirect", "webhook", "endpoint", "target"};
        
        for (String paramName : urlParams) {
            String paramValue = request.getParameter(paramName);
            if (paramValue != null && !ssrfPrevention.isUrlSafe(paramValue)) {
                logger.warn("SSRF attempt detected in parameter {} from IP: {}", 
                        paramName, getClientIpAddress(request));
                response.setStatus(400); // 400 Bad Request
                response.getWriter().write("{\"error\":\"Invalid URL parameter\"}");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Add additional security headers
     */
    private void addSecurityHeaders(HttpServletResponse response) {
        // Add cache control for API responses
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        
        // Add security headers not covered by Spring Security
        response.setHeader("X-Robots-Tag", "noindex, nofollow, nosnippet, noarchive");
    }
    
    /**
     * Check for suspicious content patterns
     */
    private boolean containsSuspiciousContent(String content) {
        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(content).matches()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get client IP address considering proxies
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
