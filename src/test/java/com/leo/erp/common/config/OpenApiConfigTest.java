package com.leo.erp.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void leoOpenApi_createsOpenAPIBean() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openApi = config.leoOpenApi();

        assertThat(openApi).isNotNull();
        assertThat(openApi.getInfo()).isNotNull();
        assertThat(openApi.getInfo().getTitle()).isEqualTo("Leo ERP API");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("v1");
        assertThat(openApi.getInfo().getDescription()).contains("REST API");
        assertThat(openApi.getInfo().getContact().getName()).isEqualTo("Leo ERP");
        assertThat(openApi.getInfo().getLicense().getName()).isEqualTo("Proprietary");
    }
}
