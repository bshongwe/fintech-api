package com.fintech.auth.config;

import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.stereotype.Component;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class CustomJwtGenerator {
    @Autowired
    private FapiClaimsValidator fapiClaimsValidator;

    public JwtClaimsSet.Builder populateClaims(JwtEncodingContext context) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        // Add PoP token confirmation claim (cnf)
        if (context.getPrincipal() != null) {
            claims.claim("cnf", Map.of("jkt", "dummy-thumbprint"));
        }
        // FAPI claims validation
        if (fapiClaimsValidator != null) {
            fapiClaimsValidator.validateClaims(context);
        }
        return claims;
    }
}
