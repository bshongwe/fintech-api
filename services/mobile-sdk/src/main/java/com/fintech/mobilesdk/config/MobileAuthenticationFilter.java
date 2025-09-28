package com.fintech.mobilesdk.config;

import com.fintech.mobilesdk.application.MobileAuthenticationService;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mobile Authentication Filter
 * 
 * Custom filter to validate mobile session tokens and set user context
 * including user ID, device ID, and security level for downstream processing.
 */
@Component
public class MobileAuthenticationFilter extends OncePerRequestFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(MobileAuthenticationFilter.class);
    
    private final MobileAuthenticationService authenticationService;
    
    @Autowired
    public MobileAuthenticationFilter(MobileAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            try {
                Optional<MobileAuthenticationService.SessionValidationResult> validationResult = 
                    authenticationService.validateSession(token);
                
                if (validationResult.isPresent()) {
                    MobileAuthenticationService.SessionValidationResult result = validationResult.get();
                    
                    // Set user context attributes
                    request.setAttribute("userId", result.getUserId());
                    request.setAttribute("deviceId", result.getDeviceId());
                    request.setAttribute("deviceInternalId", result.getDeviceInternalId());
                    request.setAttribute("securityLevel", result.getSecurityLevel());
                    request.setAttribute("fullyAuthenticated", result.getFullyAuthenticated());
                    request.setAttribute("riskScore", result.getRiskScore());
                    
                    // Create authentication token
                    List<SimpleGrantedAuthority> authorities = Arrays.asList(
                        new SimpleGrantedAuthority("ROLE_MOBILE_USER")
                    );
                    
                    // Add elevated privileges based on security level
                    if (result.getSecurityLevel() >= 2) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_ELEVATED_USER"));
                    }
                    
                    if (result.getSecurityLevel() >= 3) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_HIGH_SECURITY_USER"));
                    }
                    
                    UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(
                            result.getUserId().toString(), 
                            null, 
                            authorities
                        );
                    
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    
                    logger.debug("Mobile session validated for user: {} on device: {}", 
                               result.getUserId(), result.getDeviceId());
                }
                
            } catch (Exception e) {
                logger.error("Error validating mobile session token", e);
                // Continue without authentication - let Spring Security handle it
            }
        }
        
        filterChain.doFilter(request, response);
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        
        // Skip filter for public endpoints
        return path.equals("/api/v1/mobile/auth/devices/register") ||
               path.equals("/api/v1/mobile/auth/authenticate") ||
               path.equals("/api/v1/mobile/auth/token/refresh") ||
               path.startsWith("/actuator/health") ||
               path.startsWith("/actuator/info") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/swagger-ui");
    }
}
