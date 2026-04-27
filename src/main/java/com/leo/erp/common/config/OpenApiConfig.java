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
                        .description("Leo ERP backend bootstrap API")
                        .version("v0.1.0")
                        .contact(new Contact().name("Leo ERP"))
                        .license(new License().name("Proprietary")));
    }
}
