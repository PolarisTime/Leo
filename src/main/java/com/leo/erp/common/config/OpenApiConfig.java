package com.leo.erp.common.config;

import com.leo.erp.common.api.ApiVersion;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
public class OpenApiConfig {

    private static final String API_PROBLEM_SCHEMA = "ApiProblem";
    private static final String API_FIELD_ERROR_SCHEMA = "ApiFieldError";

    @Bean
    public OpenAPI leoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Leo ERP API")
                        .description("""
                                REST API for the Leo ERP system.

                                ## API version
                                - `/api/v2.0/**`: the only supported API contract
                                - Successful JSON responses return DTOs directly

                                ## Authentication
                                - POST /api/v2.0/auth/login — obtain access token
                                - Bearer token required for all other endpoints

                                ## Response format
                                - Success: resource DTO, collection, page DTO, or an empty `204 No Content` response
                                - Error: RFC 9457 Problem Details with `application/problem+json`
                                - Business error `code`, `traceId`, `timestamp`, and field-level `errors` are Problem Details extensions

                                ## Generating frontend types
                                ```
                                npx openapi-typescript http://localhost:11211/api/v3/api-docs \\
                                  -o src/types/api-schema.ts
                                ```
                                """)
                        .version("v2.0")
                        .contact(new Contact().name("Leo ERP"))
                        .license(new License().name("Proprietary")));
    }

    @Bean
    public GroupedOpenApi v2OpenApi() {
        return GroupedOpenApi.builder()
                .group("v2.0")
                .pathsToMatch(ApiVersion.V2_PREFIX + "/**")
                .addOpenApiCustomizer(this::customizeV2OpenApi)
                .build();
    }

    private void customizeV2OpenApi(OpenAPI openApi) {
        Components components = openApi.getComponents();
        if (components == null) {
            components = new Components();
            openApi.setComponents(components);
        }
        components.addSchemas(API_FIELD_ERROR_SCHEMA, fieldErrorSchema());
        components.addSchemas(API_PROBLEM_SCHEMA, problemSchema());

        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
            if (!operation.getResponses().containsKey("default")) {
                operation.getResponses().addApiResponse("default", problemResponse());
            }
        }));
    }

    private ObjectSchema fieldErrorSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("field", new StringSchema());
        schema.addProperty("code", new StringSchema());
        schema.addProperty("message", new StringSchema());
        schema.setRequired(List.of("field", "code", "message"));
        return schema;
    }

    private ObjectSchema problemSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("type", new StringSchema().format("uri"));
        schema.addProperty("title", new StringSchema());
        schema.addProperty("status", new IntegerSchema().format("int32"));
        schema.addProperty("detail", new StringSchema());
        schema.addProperty("instance", new StringSchema().format("uri"));
        schema.addProperty("code", new IntegerSchema().format("int32"));
        schema.addProperty("traceId", new StringSchema());
        schema.addProperty("timestamp", new DateTimeSchema());
        schema.addProperty(
                "errors",
                new ArraySchema().items(new ObjectSchema().$ref("#/components/schemas/" + API_FIELD_ERROR_SCHEMA))
        );
        schema.setRequired(List.of("type", "title", "status", "detail", "instance", "code", "timestamp"));
        return schema;
    }

    private ApiResponse problemResponse() {
        io.swagger.v3.oas.models.media.MediaType mediaType = new io.swagger.v3.oas.models.media.MediaType();
        mediaType.setSchema(new ObjectSchema().$ref("#/components/schemas/" + API_PROBLEM_SCHEMA));
        return new ApiResponse()
                .description("RFC 9457 Problem Details")
                .content(new io.swagger.v3.oas.models.media.Content()
                        .addMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE, mediaType));
    }
}
