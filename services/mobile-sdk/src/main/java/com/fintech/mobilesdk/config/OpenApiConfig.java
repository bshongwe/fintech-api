package com.fintech.mobilesdk.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI Configuration
 * 
 * Configures Swagger/OpenAPI documentation for mobile SDK REST APIs.
 */
@Configuration
public class OpenApiConfig {
    
    @Value("${app.version:1.0.0}")
    private String appVersion;
    
    @Value("${app.mobile.base-url:http://localhost:8080}")
    private String baseUrl;
    
    @Bean
    public OpenAPI mobileSDKOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Mobile SDK API")
                .description("Enterprise mobile SDK backend providing secure authentication, " +
                           "device management, push notifications, and mobile app integration services")
                .version(appVersion)
                .contact(new Contact()
                    .name("FinTech API Team")
                    .email("api-support@fintech.com")
                    .url("https://fintech.com/support"))
                .license(new License()
                    .name("Proprietary")
                    .url("https://fintech.com/license")))
            .servers(List.of(
                new Server().url(baseUrl).description("Mobile SDK Service"),
                new Server().url("https://api.fintech.com").description("Production API"),
                new Server().url("https://staging-api.fintech.com").description("Staging API")
            ))
            .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
            .components(new io.swagger.v3.oas.models.Components()
                .addSecuritySchemes("Bearer Authentication", 
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .in(SecurityScheme.In.HEADER)
                        .name("Authorization")
                        .description("JWT token for mobile session authentication")));
    }
}
