package com.fintech.auth.config;

import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class CustomJwtGenerator extends JwtGenerator {
    @Autowired
    private FapiClaimsValidator fapiClaimsValidator;

    public CustomJwtGenerator(JwtEncoder jwtEncoder) {
        super(jwtEncoder);
    }

    @Override
    protected JwtClaimsSet.Builder populateClaims(JwtEncodingContext context) {
        JwtClaimsSet.Builder claims = super.populateClaims(context);
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
