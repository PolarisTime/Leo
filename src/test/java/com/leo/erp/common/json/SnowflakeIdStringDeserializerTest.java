package com.leo.erp.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdStringDeserializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    static class AnnotatedPayload {
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = SnowflakeIdStringDeserializer.class)
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    @Test
    void deserialize_shouldAcceptDecimalString() throws Exception {
        AnnotatedPayload payload = objectMapper.readValue("{\"id\":\"9223372036854775807\"}", AnnotatedPayload.class);

        assertThat(payload.getId()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void deserialize_shouldTreatBlankStringAsNull() throws Exception {
        AnnotatedPayload payload = objectMapper.readValue("{\"id\":\"  \"}", AnnotatedPayload.class);

        assertThat(payload.getId()).isNull();
    }

    @Test
    void deserialize_shouldRejectJsonNumber() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"id\":123}", AnnotatedPayload.class))
                .hasMessageContaining("雪花 ID 必须以十进制字符串传递");
    }

    @Test
    void deserialize_shouldRejectNegativeOrZero() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"id\":\"0\"}", AnnotatedPayload.class))
                .hasMessageContaining("雪花 ID 必须为正整数");
        assertThatThrownBy(() -> objectMapper.readValue("{\"id\":\"-1\"}", AnnotatedPayload.class))
                .hasMessageContaining("雪花 ID 必须为正整数");
    }

    @Test
    void deserialize_shouldRejectNonNumericString() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"id\":\"abc\"}", AnnotatedPayload.class))
                .hasMessageContaining("雪花 ID 必须为十进制整数字符串");
    }
}
