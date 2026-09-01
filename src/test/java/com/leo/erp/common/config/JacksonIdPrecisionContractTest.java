package com.leo.erp.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API JSON 序列化契约：雪花 ID 必须为字符串，金额/重量精度不变。
 */
class JacksonIdPrecisionContractTest {

    static class SamplePayload {
        public Long id;
        public BigDecimal weightTon;
        public BigDecimal amount;
        public String materialCode;
    }

    private ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        Jackson2ObjectMapperBuilderCustomizer customizer =
                new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer();
        customizer.customize(builder);
        return builder.build();
    }

    @Test
    void snowflakeId_shouldSerializeAsString() throws Exception {
        SamplePayload payload = new SamplePayload();
        payload.id = 9223372036854775807L;
        payload.materialCode = "M001";

        String json = mapper().writeValueAsString(payload);

        assertThat(json).contains("\"id\":\"9223372036854775807\"");
        assertThat(json).doesNotContain("\"id\":9223372036854775807");
    }

    @Test
    void weightTon_shouldKeepEightDecimalPlaces() throws Exception {
        SamplePayload payload = new SamplePayload();
        payload.weightTon = new BigDecimal("6.250000001");

        String json = mapper().writeValueAsString(payload);

        assertThat(json).contains("\"weightTon\":6.25000000");
    }

    @Test
    void amount_shouldKeepTwoDecimalPlaces() throws Exception {
        SamplePayload payload = new SamplePayload();
        payload.amount = new BigDecimal("25000.005");

        String json = mapper().writeValueAsString(payload);

        assertThat(json).contains("\"amount\":25000.01");
    }

    @Test
    void snowflakeId_shouldRemainParseableAsLong() throws Exception {
        SamplePayload payload = new SamplePayload();
        payload.id = 9007199254740993000L;

        String json = mapper().writeValueAsString(payload);

        String serialized = json.replaceAll(".*\"id\":\"(\\d+)\".*", "$1");
        assertThat(Long.parseLong(serialized)).isEqualTo(9007199254740993000L);
    }
}
