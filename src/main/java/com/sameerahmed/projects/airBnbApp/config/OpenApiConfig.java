package com.sameerahmed.projects.airBnbApp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI airBnbOpenAPI() {
        final String bearerScheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Airbnb Hotel Booking API")
                        .description("""
                                REST API for an Airbnb-style hotel booking platform.

                                **Auth:** Obtain an access token via `POST /auth/login` (refresh token is set as an HttpOnly cookie).
                                Send the access token as `Authorization: Bearer <token>`.

                                **Context path:** All endpoints are under `/api/v1`.
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("Airbnb App")))
                .servers(List.of(new Server().url("/api/v1").description("API v1")))
                .addSecurityItem(new SecurityRequirement().addList(bearerScheme))
                .components(new Components()
                        .addSecuritySchemes(bearerScheme, new SecurityScheme()
                                .name(bearerScheme)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token from /auth/login")));
    }
}
