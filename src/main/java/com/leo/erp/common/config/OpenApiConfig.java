package com.leo.erp.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
public class OpenApiConfig {

    @Bean
    public OpenAPI leoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Leo ERP API")
                        .description("""
                                REST API for the Leo ERP system.

                                ## Authentication
                                - POST /api/auth/login — obtain access token
                                - Bearer token required for all other endpoints

                                ## Response format
                                All endpoints return `{ code: 0, message: "...", data: {...}, timestamp: "..." }`.
                                - code=0: success
                                - code=4000: validation error
                                - code=4010: unauthorized
                                - code=4030: forbidden

                                ## Generating frontend types
                                ```
                                npx openapi-typescript http://localhost:11211/api/v3/api-docs \\
                                  -o src/types/api-schema.ts
                                ```
                                """)
                        .version("v1")
                        .contact(new Contact().name("Leo ERP"))
                        .license(new License().name("Proprietary")));
    }
}
