package com.fintech.auth.config;

import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.web.header.writers.StrictTransportSecurityHeaderWriter;
import org.springframework.security.web.server.header.StrictTransportSecurityServerHttpHeadersWriter;
import org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter;
import org.springframework.security.web.authentication.preauth.x509.X509PrincipalExtractor;
import org.springframework.security.web.authentication.preauth.x509.SubjectDnX509PrincipalExtractor;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.config.ProviderSettings;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http
            .authorizeHttpRequests((authorize) -> authorize
                .requestMatchers("/actuator/**", "/.well-known/**").permitAll()
                .anyRequest().authenticated()
            )
            // Enforce HTTPS for all requests
            .requiresChannel(channel -> channel.anyRequest().requiresSecure())
            // Add HSTS header for FAPI compliance
            .headers(headers -> headers.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
            // Add mTLS support for token endpoint (stub)
            .x509(x509 -> x509
                .subjectPrincipalRegex("CN=(.*?)(?:,|$)")
                .userDetailsService(username -> {
                    // TODO: Lookup client by certificate subject (for mTLS)
                    // Validate against RegisteredClient.getClientSettings().get("certificate_thumbprint")
                    // String thumbprint = ...extract from certificate...
                    // Compare with RegisteredClient.getClientSettings().get("certificate_thumbprint")
                    return null;
                })
            );
        // Add PoP token support (stub)
        // TODO: Implement PoP token issuance and validation (DPoP, cnf claim)
        return http.build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("sandbox-client")
            .clientSecret("sandbox-secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            // For mTLS, register client certificate thumbprint (stub)
            .clientAuthenticationMethod(ClientAuthenticationMethod.TLS_CLIENT_AUTHENTICATION)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("http://localhost:8080/login/oauth2/code/sandbox-client")
            .scope("openid")
            .scope("profile")
            // Store certificate thumbprint for mTLS (stub value)
            .clientSettings(settings -> settings.setting("certificate_thumbprint", "dummy-thumbprint"))
            .build();
        return new InMemoryRegisteredClientRepository(registeredClient);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
            .issuer("http://localhost:9000")
            .build();
    }

    // JWKS and token endpoints are exposed by default by Spring Authorization Server
    // For mTLS and PoP tokens, additional configuration is required (stubbed for now)
    @Bean
    public JwtGenerator jwtGenerator(JwtEncoder jwtEncoder) {
        return new CustomJwtGenerator(jwtEncoder);
    }
    // TODO: Implement FAPI-compliant error handling and logging
    // TODO: Validate audience, scopes, and claims for FAPI
}
