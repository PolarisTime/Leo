package com.leo.erp.sales.contract.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.JacksonConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** 雪花 ID 出参必须是十进制字符串, 避免前端 Number 精度丢失。 */
class SalesContractJsonSerializationTest {

    private ObjectMapper objectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    void response_snowflakeIds_areSerializedAsStrings() throws Exception {
        SalesContractResponse response = new SalesContractResponse(
                9223372036854775807L,
                "HT-1",
                "年度合同",
                9007199254740993L,
                "客户甲",
                9007199254740995L,
                "项目乙",
                LocalDate.of(2026, 9, 17),
                null,
                null,
                new BigDecimal("1000000.00"),
                new BigDecimal("3000.50000000"),
                "已审核",
                null,
                null,
                null,
                3L
        );

        String json = objectMapper().writeValueAsString(response);

        assertThat(json).contains("\"id\":\"9223372036854775807\"");
        assertThat(json).contains("\"customerId\":\"9007199254740993\"");
        assertThat(json).contains("\"projectId\":\"9007199254740995\"");
        assertThat(json).contains("\"version\":\"3\"");
    }

    @Test
    void checkResponse_amountsStayNumeric() throws Exception {
        SalesContractCheckResponse response = new SalesContractCheckResponse(
                true,
                new BigDecimal("100.00"),
                new BigDecimal("120.00"),
                new BigDecimal("-20.00"),
                new BigDecimal("10.00000000"),
                new BigDecimal("12.00000000"),
                new BigDecimal("-2.00000000"),
                new BigDecimal("20.00"),
                new BigDecimal("2.00000000"),
                "已超出合同额度"
        );

        String json = objectMapper().writeValueAsString(response);

        assertThat(json).contains("\"hasContract\":true");
        assertThat(json).contains("\"contractAmount\":100.00");
        assertThat(json).contains("\"exceededTonnage\":2.00000000");
    }
}
