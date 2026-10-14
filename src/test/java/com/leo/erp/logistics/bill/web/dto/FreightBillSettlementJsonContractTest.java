package com.leo.erp.logistics.bill.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.logistics.bill.domain.enums.FreightSettlementMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 物流单结算方式 DTO JSON 契约: 冻结给前端的字段名、类型与精度。
 *
 * <ul>
 *   <li>{@code settlementMode} 是枚举<b>名字符串</b>(BY_TONNAGE / FIXED), 未知字面量解析失败,
 *       不会被静默当成按吨位;</li>
 *   <li>{@code fixedFreightAmount} / {@code tonnageFreightAmount} / {@code totalFreight} 是
 *       <b>JSON number</b> 且固定 2 位小数(不是十进制字符串);</li>
 *   <li>雪花 ID 仍是十进制字符串(ToStringSerializer 约定不变)。</li>
 * </ul>
 */
class FreightBillSettlementJsonContractTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        objectMapper = builder.build();
    }

    @Test
    void response_serializesSettlementFieldsWithFrozenNamesAndPrecision() throws Exception {
        FreightBillResponse response = new FreightBillResponse(
                9223372036854775807L, "9223372036854775807", 1L, "C001", "承运商A", 30L, "结算公司A",
                5L, "沪A123", LocalDate.of(2026, 8, 1),
                new BigDecimal("100.5"), new BigDecimal("12.5"), new BigDecimal("1800.55"),
                FreightSettlementMode.FIXED, new BigDecimal("1800.55"), new BigDecimal("1250.625"),
                "草稿", false, null, List.of(), new BigDecimal("0"), List.of());

        String json = objectMapper.writeValueAsString(response);

        // 金额是 JSON number(不是字符串), 且固定 2 位小数(HALF_UP)
        assertThat(json).contains("\"unitPrice\":100.50");
        assertThat(json).contains("\"totalFreight\":1800.55");
        assertThat(json).contains("\"fixedFreightAmount\":1800.55");
        assertThat(json).contains("\"tonnageFreightAmount\":1250.63");
        // 结算方式是枚举名字符串
        assertThat(json).contains("\"settlementMode\":\"FIXED\"");
        // 雪花 ID 仍是十进制字符串
        assertThat(json).contains("\"id\":\"9223372036854775807\"");
        assertThat(json).contains("\"carrierId\":\"1\"");
        assertThat(json).contains("\"vehicleId\":\"5\"");
    }

    @Test
    void response_byTonnageMode_serializesNullFixedAmount() throws Exception {
        FreightBillResponse response = new FreightBillResponse(
                1L, "1", null, "C001", "承运商A", null, null, null, null, LocalDate.of(2026, 8, 1),
                new BigDecimal("100.00"), new BigDecimal("12.50000000"), new BigDecimal("1250.00"),
                FreightSettlementMode.BY_TONNAGE, null, new BigDecimal("1250.00"),
                "草稿", false, null, List.of(), null, List.of());

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"settlementMode\":\"BY_TONNAGE\"");
        assertThat(json).contains("\"fixedFreightAmount\":null");
        assertThat(json).contains("\"tonnageFreightAmount\":1250.00");
    }

    @Test
    void request_deserializesSettlementFields() throws Exception {
        String json = "{\"billNo\":\"FB001\",\"carrierCode\":\"C001\",\"carrierName\":\"承运商A\","
                + "\"billTime\":\"2026-08-01\",\"unitPrice\":100.5,\"settlementMode\":\"FIXED\","
                + "\"fixedFreightAmount\":1800.55,\"items\":[]}";

        FreightBillRequest request = objectMapper.readValue(json, FreightBillRequest.class);

        assertThat(request.settlementMode()).isEqualTo(FreightSettlementMode.FIXED);
        assertThat(request.fixedFreightAmount()).isEqualByComparingTo("1800.55");
    }

    /** 请求未携带结算字段时必须是 null(表示"没有该字段"), 由服务层决定保持原值/默认按吨位。 */
    @Test
    void request_omittedSettlementFields_areNull() throws Exception {
        String json = "{\"billNo\":\"FB001\",\"carrierCode\":\"C001\",\"carrierName\":\"承运商A\","
                + "\"billTime\":\"2026-08-01\",\"unitPrice\":100,\"items\":[]}";

        FreightBillRequest request = objectMapper.readValue(json, FreightBillRequest.class);

        assertThat(request.settlementMode()).isNull();
        assertThat(request.fixedFreightAmount()).isNull();
    }

    /** 未知结算方式字面量必须解析失败(400), 不得静默落到默认值。 */
    @Test
    void request_unknownSettlementMode_isRejected() {
        String json = "{\"billNo\":\"FB001\",\"carrierCode\":\"C001\",\"carrierName\":\"承运商A\","
                + "\"billTime\":\"2026-08-01\",\"unitPrice\":100,\"settlementMode\":\"TONNAGE\",\"items\":[]}";

        assertThatThrownBy(() -> objectMapper.readValue(json, FreightBillRequest.class))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("TONNAGE");
    }
}
