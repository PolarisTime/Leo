package com.leo.erp.master.warehouse.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.JacksonConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WarehouseOptionResponseTest {

    private static final long LARGE_SNOWFLAKE_ID = 9007199254740993L;

    @Test
    void shouldExposeStableIdentityAndDisplaySnapshot() {
        WarehouseOptionResponse response = new WarehouseOptionResponse(
                LARGE_SNOWFLAKE_ID,
                LARGE_SNOWFLAKE_ID,
                "WH001 / 一号库",
                "WH001",
                "一号库"
        );

        assertThat(response.id()).isEqualTo(LARGE_SNOWFLAKE_ID);
        assertThat(response.value()).isEqualTo(LARGE_SNOWFLAKE_ID);
        assertThat(response.label()).isEqualTo("WH001 / 一号库");
        assertThat(response.warehouseCode()).isEqualTo("WH001");
        assertThat(response.warehouseName()).isEqualTo("一号库");
    }

    @Test
    void shouldSerializeLargeIdAndValueAsExactStrings() throws Exception {
        WarehouseOptionResponse response = new WarehouseOptionResponse(
                LARGE_SNOWFLAKE_ID,
                LARGE_SNOWFLAKE_ID,
                "WH001 / 一号库",
                "WH001",
                "一号库"
        );

        String json = objectMapper().writeValueAsString(response);

        assertThat(json)
                .contains("\"id\":\"9007199254740993\"")
                .contains("\"value\":\"9007199254740993\"")
                .doesNotContain("\"value\":\"一号库\"");
    }

    @Test
    void shouldRejectMissingOrMismatchedStableIdentity() {
        assertThatThrownBy(() -> new WarehouseOptionResponse(
                null, null, "WH001 / 一号库", "WH001", "一号库"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("稳定ID");
        assertThatThrownBy(() -> new WarehouseOptionResponse(
                LARGE_SNOWFLAKE_ID, 1L, "WH001 / 一号库", "WH001", "一号库"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value必须与ID一致");
    }

    @Test
    void shouldRejectIncompleteDisplaySnapshot() {
        assertThatThrownBy(() -> new WarehouseOptionResponse(
                LARGE_SNOWFLAKE_ID, LARGE_SNOWFLAKE_ID, "一号库", " ", "一号库"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仓库编码");
        assertThatThrownBy(() -> new WarehouseOptionResponse(
                LARGE_SNOWFLAKE_ID, LARGE_SNOWFLAKE_ID, " ", "WH001", "一号库"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label");
    }

    private ObjectMapper objectMapper() {
        JacksonConfig config = new JacksonConfig("Asia/Shanghai");
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        config.jackson2ObjectMapperBuilderCustomizer().customize(builder);
        return builder.build();
    }
}
