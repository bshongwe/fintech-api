package com.fintech.auth.config;

import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class CustomJwtGenerator extends JwtGenerator {
    public CustomJwtGenerator(JwtEncoder jwtEncoder) {
        super(jwtEncoder);
    }

    @Override
    protected JwtClaimsSet.Builder populateClaims(JwtEncodingContext context) {
        JwtClaimsSet.Builder claims = super.populateClaims(context);
        // Add PoP token confirmation claim (cnf)
        if (context.getPrincipal() != null) {
            // Example: Add a dummy JWK thumbprint for demonstration
            claims.claim("cnf", Map.of("jkt", "dummy-thumbprint"));
        }
        return claims;
    }
}
