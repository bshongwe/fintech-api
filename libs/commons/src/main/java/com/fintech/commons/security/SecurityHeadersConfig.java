package com.fintech.commons.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

/**
 * Security Headers Configuration
 * 
 * Configures comprehensive security headers for all services.
 * Includes HSTS, CSP, X-Frame-Options, and other security headers.
 */
@Configuration
@EnableWebSecurity
public class SecurityHeadersConfig {
    
    @Bean
    public SecurityFilterChain securityHeadersFilterChain(HttpSecurity http) throws Exception {
        return http
                .headers(headers -> headers
                        // Enable HSTS (HTTP Strict Transport Security)
                        .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                                .maxAgeInSeconds(31536000) // 1 year
                                .includeSubdomains(true)
                                .preload(true)
                        )
                        
                        // Content Security Policy
                        .contentSecurityPolicy(cspConfig -> cspConfig
                                .policyDirectives(
                                        "default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net https://unpkg.com; " +
                                        "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com; " +
                                        "font-src 'self' https://fonts.gstatic.com; " +
                                        "img-src 'self' data: https:; " +
                                        "connect-src 'self' wss: https:; " +
                                        "frame-ancestors 'none'; " +
                                        "form-action 'self'; " +
                                        "base-uri 'self'; " +
                                        "object-src 'none';"
                                )
                        )
                        
                        // X-Frame-Options
                        .frameOptions(frameOptionsConfig -> frameOptionsConfig
                                .deny()
                        )
                        
                        // X-Content-Type-Options
                        .contentTypeOptions(contentTypeOptionsConfig -> contentTypeOptionsConfig
                                .and()
                        )
                        
                        // Referrer Policy
                        .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                        
                        // Custom security headers
                        .addHeaderWriter(new StaticHeadersWriter("X-Permitted-Cross-Domain-Policies", "none"))
                        .addHeaderWriter(new StaticHeadersWriter("X-XSS-Protection", "1; mode=block"))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", 
                                "geolocation=(), microphone=(), camera=(), payment=(), usb=(), magnetometer=(), gyroscope=()"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Embedder-Policy", "require-corp"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Resource-Policy", "same-origin"))
                        
                        // Remove server header for security
                        .addHeaderWriter(new StaticHeadersWriter("Server", ""))
                        
                        // Cache control for sensitive endpoints
                        .cacheControl(cacheControlConfig -> cacheControlConfig
                                .and()
                        )
                )
                .build();
    }
}
