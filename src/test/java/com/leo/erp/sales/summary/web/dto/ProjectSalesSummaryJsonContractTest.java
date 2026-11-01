package com.leo.erp.sales.summary.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.JacksonConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目销售汇总响应 JSON 契约：雪花 ID 字符串化 + 金额 2 位 / 吨位 8 位 / 使用率 4 位。
 *
 * <p>重点锁住 {@code amountUsageRate}：它的字段名含 {@code amount}，而全局
 * {@code ScaledBigDecimalSerializer} 会按字段名把金额类字段强制截到 2 位小数，
 * 使用率若不加字段级序列化器，{@code 0.8532} 会被输出成 {@code 0.85}（85.32% → 85.00%）。</p>
 */
class ProjectSalesSummaryJsonContractTest {

    private ObjectMapper objectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        return builder.build();
    }

    private ProjectSalesSummaryResponse response() {
        return new ProjectSalesSummaryResponse(
                9223372036854775807L,
                "项目甲",
                new BigDecimal("1000.00"),
                new BigDecimal("100.00000000"),
                new BigDecimal("800.00"),
                new BigDecimal("80.00000000"),
                new BigDecimal("300.00"),
                new BigDecimal("30.00000000"),
                new BigDecimal("500.00"),
                new BigDecimal("50.00000000"),
                true,
                new BigDecimal("2000.00"),
                new BigDecimal("200.00000000"),
                new BigDecimal("1000.00"),
                new BigDecimal("100.00000000"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00000000"),
                new BigDecimal("0.8532"),
                new BigDecimal("0.8532"),
                "合同额度充足"
        );
    }

    @Test
    void projectId_isSerializedAsDecimalString() throws Exception {
        String json = objectMapper().writeValueAsString(response());

        assertThat(json).contains("\"projectId\":\"9223372036854775807\"");
    }

    @Test
    void amountsKeepTwoDecimalsAndTonnagesKeepEight() throws Exception {
        String json = objectMapper().writeValueAsString(response());

        assertThat(json).contains("\"orderAmount\":1000.00");
        assertThat(json).contains("\"orderTonnage\":100.00000000");
        assertThat(json).contains("\"unreconciledTonnage\":50.00000000");
        assertThat(json).contains("\"contractTonnage\":200.00000000");
    }

    @Test
    void usageRatesKeepFourDecimals() throws Exception {
        String json = objectMapper().writeValueAsString(response());

        assertThat(json).contains("\"amountUsageRate\":0.8532");
        assertThat(json).contains("\"tonnageUsageRate\":0.8532");
    }

    @Test
    void absentUsageRates_areSerializedAsNull() throws Exception {
        ProjectSalesSummaryResponse withoutContract = new ProjectSalesSummaryResponse(
                2L,
                "项目乙",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                "该项目未关联有效销售合同"
        );

        String json = objectMapper().writeValueAsString(withoutContract);

        assertThat(json).contains("\"amountUsageRate\":null");
        assertThat(json).contains("\"tonnageUsageRate\":null");
    }
}
