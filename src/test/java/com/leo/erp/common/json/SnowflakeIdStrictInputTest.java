package com.leo.erp.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.JacksonConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 请求体雪花 ID 入参契约：名为 id / 以 Id 结尾的 Long 只接受十进制字符串；
 * 非 ID 的 Long（版本号等）仍接受数字。
 */
class SnowflakeIdStrictInputTest {

    static class SampleRequest {
        public Long id;
        public Long customerId;
        public Long version;
        public String name;
    }

    private ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        Jackson2ObjectMapperBuilderCustomizer customizer =
                new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer();
        customizer.customize(builder);
        return builder.build();
    }

    @Test
    void idAndForeignKeyId_shouldRejectJsonNumber() {
        assertThatThrownBy(() -> mapper().readValue("{\"id\":123}", SampleRequest.class))
                .hasMessageContaining("雪花 ID 必须以十进制字符串传递");
        assertThatThrownBy(() -> mapper().readValue("{\"customerId\":123}", SampleRequest.class))
                .hasMessageContaining("雪花 ID 必须以十进制字符串传递");
    }

    @Test
    void idAndForeignKeyId_shouldAcceptDecimalString() throws Exception {
        SampleRequest request = mapper()
                .readValue("{\"id\":\"9007199254740993\",\"customerId\":\"42\"}", SampleRequest.class);

        assertThat(request.id).isEqualTo(9007199254740993L);
        assertThat(request.customerId).isEqualTo(42L);
    }

    @Test
    void nonIdLong_shouldStillAcceptNumber() throws Exception {
        SampleRequest request = mapper().readValue("{\"version\":7}", SampleRequest.class);

        assertThat(request.version).isEqualTo(7L);
    }
}
