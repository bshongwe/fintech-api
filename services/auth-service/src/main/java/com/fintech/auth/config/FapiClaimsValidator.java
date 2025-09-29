package com.fintech.auth.config;

import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class FapiClaimsValidator {
    private static final Set<String> REQUIRED_SCOPES = Set.of("openid", "profile");
    private static final String REQUIRED_AUDIENCE = "fintech-api";

    public void validateClaims(JwtEncodingContext context) {
        JwtClaimsSet.Builder claimsBuilder = context.getClaims();
        JwtClaimsSet claims = claimsBuilder.build();
        // Validate audience
        if (!claims.getAudience().contains(REQUIRED_AUDIENCE)) {
            throw new IllegalArgumentException("Invalid audience for FAPI");
        }
        // Validate scopes
        Object scopeObj = claims.getClaims().get("scope");
        if (scopeObj instanceof String scopeStr) {
            for (String required : REQUIRED_SCOPES) {
                if (!scopeStr.contains(required)) {
                    throw new IllegalArgumentException("Missing required scope: " + required);
                }
            }
        }
    }
}
