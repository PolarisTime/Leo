package com.leo.erp.market.quotation.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.JacksonConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 报价单 DTO JSON 契约: 雪花 ID 必须输出为字符串。 */
class QuoteSheetDtoJsonContractTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        objectMapper = builder.build();
    }

    @Test
    void response_serializesSnowflakeIdAsString() throws Exception {
        QuoteSheetResponse response = new QuoteSheetResponse(
                9223372036854775807L, "9223372036854775807", "9月9日报单", 123L, "云潮筝鸣府",
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午", new BigDecimal("30.00"),
                false, "报价", null,
                List.of(new QuoteSheetResponse.BrandResponse(1L, "中天", new BigDecimal("30.00"), 0)),
                List.of(new QuoteSheetResponse.ItemResponse(2L, 1, "螺纹钢", "HRB400E", 12, "9米",
                        new BigDecimal("10.00000000"),
                        List.of(new QuoteSheetResponse.ItemPriceResponse(
                                3L, "中天", new BigDecimal("3280.00"), 77L, "杭州物资")))),
                null, null);

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"id\":\"9223372036854775807\"");
        assertThat(json).contains("\"projectId\":\"123\"");
        assertThat(json).contains("\"orderDate\":\"2026-09-09\"");
        assertThat(json).contains("\"spec\":12");
    }

    @Test
    void request_deserializesSpecAndLength() throws Exception {
        String json = "{\"name\":\"9月9日报单\",\"orderDate\":\"2026-09-09\",\"refDate\":\"2026-09-10\","
                + "\"refPeriod\":\"9:30 上午\",\"brands\":[{\"brandName\":\"中天\",\"freight\":30}],"
                + "\"items\":[{\"category\":\"螺纹钢\",\"material\":\"HRB400E\",\"spec\":12,\"length\":\"9米\","
                + "\"ton\":10,\"prices\":[{\"brandName\":\"中天\",\"spotPrice\":3280}]}]}";

        QuoteSheetRequest request = objectMapper.readValue(json, QuoteSheetRequest.class);

        assertThat(request.items().get(0).spec()).isEqualTo(12);
        assertThat(request.items().get(0).prices().get(0).spotPrice()).isEqualByComparingTo("3280");
    }
}
