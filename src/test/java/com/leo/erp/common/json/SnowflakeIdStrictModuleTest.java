package com.leo.erp.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 请求体雪花 ID 入站边界：{@code @SnowflakeId Long} 只接受十进制字符串，非 ID 的 Long 行为不变。
 *
 * <p>契约依据 AGENTS《强制 RESTful API 规范》：客户端传入的雪花 ID 必须是十进制字符串，
 * 新增 API 不得接受 JSON number；历史数值兼容只保留给未标注 {@code @SnowflakeId} 的 Long
 * （行数、文件大小、版本号等非 ID 语义）。</p>
 */
class SnowflakeIdStrictModuleTest {

    record StrictSample(@SnowflakeId Long customerId, Long fileSize) {
    }

    private ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        Jackson2ObjectMapperBuilderCustomizer customizer =
                new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer();
        customizer.customize(builder);
        return builder.build();
    }

    @Test
    void annotatedId_shouldAcceptDecimalString() throws Exception {
        StrictSample sample = mapper().readValue("{\"customerId\":\"9223372036854775807\"}", StrictSample.class);

        assertThat(sample.customerId()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void annotatedId_shouldRejectJsonNumberEvenWhenSafeInteger() {
        assertThatThrownBy(() -> mapper().readValue("{\"customerId\":12345}", StrictSample.class))
                .hasMessageContaining("雪花 ID 必须以十进制字符串传递");
    }

    @Test
    void annotatedId_shouldRejectZeroAndNegative() {
        assertThatThrownBy(() -> mapper().readValue("{\"customerId\":\"0\"}", StrictSample.class))
                .hasMessageContaining("雪花 ID 必须为正整数");
        assertThatThrownBy(() -> mapper().readValue("{\"customerId\":\"-1\"}", StrictSample.class))
                .hasMessageContaining("雪花 ID 必须为正整数");
    }

    @Test
    void annotatedId_shouldRejectValueOverLongMax() {
        assertThatThrownBy(() -> mapper().readValue("{\"customerId\":\"9223372036854775808\"}", StrictSample.class))
                .hasMessageContaining("雪花 ID 必须为十进制整数字符串");
    }

    @Test
    void annotatedId_shouldRejectNonNumericString() {
        assertThatThrownBy(() -> mapper().readValue("{\"customerId\":\"abc\"}", StrictSample.class))
                .hasMessageContaining("雪花 ID 必须为十进制整数字符串");
    }

    /** 非 ID 的 Long 仍兼容安全整数 number（历史契约不变），避免把 fileSize/version 误收紧。 */
    @Test
    void nonIdLong_shouldStillAcceptSafeNumber() throws Exception {
        StrictSample sample = mapper().readValue("{\"fileSize\":12345}", StrictSample.class);

        assertThat(sample.fileSize()).isEqualTo(12345L);
    }

    /** 真实 DTO 验证：SalesOrderRequest.customerId 带 @SnowflakeId，number 入参必须被拒绝。 */
    @Test
    void realRequestDto_shouldRejectNumericSnowflakeId() {
        assertThatThrownBy(() -> mapper().readValue(
                "{\"customerId\":123,\"customerName\":\"客户A\"}", SalesOrderRequest.class))
                .hasMessageContaining("雪花 ID 必须以十进制字符串传递");
    }

    @Test
    void realRequestDto_shouldAcceptStringSnowflakeId() throws Exception {
        SalesOrderRequest request = mapper().readValue(
                "{\"customerId\":\"9007199254740993\",\"customerName\":\"客户A\"}", SalesOrderRequest.class);

        assertThat(request.customerId()).isEqualTo(9007199254740993L);
    }
}
