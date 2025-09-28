package com.fintech.auth.controller;

import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Validated
@Tag(name = "Well-Known", description = "Well-known endpoints for authentication")
public class WellKnownController {

    @GetMapping("/.well-known/jwks.json")
    @Operation(summary = "Get JWKS", description = "Get JSON Web Key Set for token validation")
    public Map<String, Object> jwks() {
        // Placeholder JWK set; replace with proper key management / JWKS endpoint
        return Map.of("keys", java.util.List.of());
    }
}
