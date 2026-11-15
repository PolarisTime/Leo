package com.leo.erp.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.leo.erp.common.config.JacksonConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 状态 code/label 双向映射契约：API 输出稳定 code，请求可接受 code 并回落为 DB label。
 */
class StatusCodeJsonTest {

    static class SampleStatus {
        @JsonSerialize(using = StatusCodeSerializer.class)
        @JsonDeserialize(using = StatusCodeDeserializer.class)
        public String status;

        @JsonSerialize(using = StatusCodeSerializer.class)
        public String resultStatus;
    }

    private ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        Jackson2ObjectMapperBuilderCustomizer customizer =
                new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer();
        customizer.customize(builder);
        return builder.build();
    }

    @Test
    void labelShouldSerializeToStableCode() throws Exception {
        SampleStatus payload = new SampleStatus();
        payload.status = "完成销售";

        assertThat(mapper().writeValueAsString(payload)).contains("\"status\":\"SALES_COMPLETED\"");
    }

    @Test
    void codeAndUnknownStatusShouldPassThrough() throws Exception {
        SampleStatus payload = new SampleStatus();
        payload.status = "AUDITED";
        payload.resultStatus = "SUCCESS";

        String json = mapper().writeValueAsString(payload);

        assertThat(json).contains("\"status\":\"AUDITED\"");
        assertThat(json).contains("\"resultStatus\":\"SUCCESS\"");
    }

    @Test
    void codeShouldDeserializeToDatabaseLabel() throws Exception {
        SampleStatus payload = mapper().readValue("{\"status\":\"SALES_COMPLETED\"}", SampleStatus.class);

        assertThat(payload.status).isEqualTo("完成销售");
    }

    @Test
    void labelAndUnknownStatusShouldDeserializeUnchanged() throws Exception {
        SampleStatus payload = mapper().readValue("{\"status\":\"已审核\"}", SampleStatus.class);
        assertThat(payload.status).isEqualTo("已审核");

        SampleStatus unknown = mapper().readValue("{\"status\":\"SUCCESS\"}", SampleStatus.class);
        assertThat(unknown.status).isEqualTo("SUCCESS");
    }
}
