package com.fintech.auth.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WellKnownController {

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        // Placeholder JWK set; replace with proper key management / JWKS endpoint
        return Map.of("keys", java.util.List.of());
    }
}
