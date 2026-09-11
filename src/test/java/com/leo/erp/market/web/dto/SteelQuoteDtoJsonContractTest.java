package com.leo.erp.market.web.dto;

import com.leo.erp.common.config.JacksonConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 行情 DTO JSON 契约冻结: 雪花 ID 必须输出为十进制字符串, 日期/时间为 ISO 8601。
 */
class SteelQuoteDtoJsonContractTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        objectMapper = builder.build();
    }

    @Test
    void steelQuoteResponse_serializesSnowflakeIdAsString() throws Exception {
        SteelQuoteResponse response = new SteelQuoteResponse(
                9223372036854775807L, "杭州", LocalDate.of(2026, 9, 9), "下午",
                "螺纹钢", "Φ16", "HRB400E", "中天", new BigDecimal("3160.00"),
                "-", "货少", LocalDateTime.of(2026, 9, 9, 15, 45, 0));

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"id\":\"9223372036854775807\"");
        assertThat(json).contains("\"quoteDate\":\"2026-09-09\"");
        assertThat(json).contains("\"price\":3160.00");
        assertThat(json).contains("\"scrapedAt\":\"2026-09-09T15:45:00+08:00\"");
        assertThat(json).doesNotContain("\"id\":9");
    }

    @Test
    void materialPriceMatchResponse_serializesSnowflakeIdAsString() throws Exception {
        MaterialPriceMatchResponse response = new MaterialPriceMatchResponse(
                9223372036854775807L, "M001", "中天", "HRB400E", "直条", "16", "12米",
                "匹配", "中天", "Φ16", true, new BigDecimal("3130"), new BigDecimal("3160"),
                "12米(+30)", "-", "Φ16:3130;货少", LocalDate.of(2026, 9, 9), "下午");

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"materialId\":\"9223372036854775807\"");
        assertThat(json).doesNotContain("\"materialId\":9");
    }

    @Test
    void steelQuoteSyncResponse_serializesSnowflakeIdAsString() throws Exception {
        SteelQuoteSyncResponse response = new SteelQuoteSyncResponse(
                9223372036854775807L, "https://jiancai.mysteel.com/m/x.html",
                "2026-09-09", "1540", "下午", java.util.List.of("上午", "中午", "下午"),
                540, true);

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"articleId\":\"9223372036854775807\"");
    }
}
