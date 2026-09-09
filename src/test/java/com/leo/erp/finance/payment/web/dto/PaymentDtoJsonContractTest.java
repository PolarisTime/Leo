package com.leo.erp.finance.payment.web.dto;

import com.leo.erp.common.config.JacksonConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payment 包 DTO 对外 JSON 契约冻结测试。
 * <p>
 * 使用生产 JacksonConfig customizer（Long→String、BigDecimal 定标、ISO 日期），
 * 逐字节冻结 Request/Response 的序列化与反序列化契约，
 * 作为未来 DTO 去重改造（如 {@code @JsonUnwrapped}）的前后对比基线。
 */
class PaymentDtoJsonContractTest {

    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        objectMapper = builder.build();
    }

    private PaymentResponse sampleResponse() {
        PaymentAllocationResponse allocation = new PaymentAllocationResponse(
                987654321098765432L, 1, 111222333444555666L, 111222333444555666L,
                "FS-2026-001", new BigDecimal("120.50"), new BigDecimal("80"));
        return new PaymentResponse(
                1234567890123456789L, "PAY-2026-001", "SUPPLIER_PAYMENT", 555444333222111000L, "PURCHASE_PREPAYMENT",
                "SUP-001", "供应商甲", 111222333444555666L, 222333444555666777L, "PO-2026-001",
                "SUP-001", "供应商甲", 888777666555444333L, "结算公司A", 999888777666555444L,
                LocalDate.of(2026, 9, 1), "BANK_TRANSFER", new BigDecimal("1000"),
                "PENDING_APPROVAL", true, "张三", "备注", List.of(allocation));
    }

    @Test
    void responseSerializationIsByteStable() throws Exception {
        String json = objectMapper.writeValueAsString(sampleResponse());

        assertThat(json).isEqualTo("{\"id\":\"1234567890123456789\",\"paymentNo\":\"PAY-2026-001\","
                + "\"counterpartyType\":\"SUPPLIER_PAYMENT\",\"counterpartyId\":\"555444333222111000\","
                + "\"paymentPurpose\":\"PURCHASE_PREPAYMENT\",\"counterpartyCode\":\"SUP-001\","
                + "\"counterpartyName\":\"供应商甲\",\"sourceStatementId\":\"111222333444555666\","
                + "\"sourcePurchaseOrderId\":\"222333444555666777\",\"purchaseOrderNo\":\"PO-2026-001\","
                + "\"supplierCode\":\"SUP-001\",\"supplierName\":\"供应商甲\","
                + "\"settlementCompanyId\":\"888777666555444333\",\"settlementCompanyName\":\"结算公司A\","
                + "\"accountId\":\"999888777666555444\",\"paymentDate\":\"2026-09-01\","
                + "\"payType\":\"BANK_TRANSFER\",\"amount\":1000.00,\"status\":\"PENDING_APPROVAL\","
                + "\"deletedFlag\":true,\"operatorName\":\"张三\",\"remark\":\"备注\","
                + "\"items\":[{\"id\":\"987654321098765432\",\"lineNo\":1,"
                + "\"sourceStatementId\":\"111222333444555666\",\"sourceFreightStatementId\":\"111222333444555666\","
                + "\"statementNo\":\"FS-2026-001\",\"statementBalanceAmount\":120.50,\"allocatedAmount\":80.00}]}");
    }

    @Test
    void requestDeserializationIsFieldStable() throws Exception {
        String json = "{\"paymentNo\":\"PAY-2026-001\",\"counterpartyType\":\"SUPPLIER_PAYMENT\","
                + "\"counterpartyId\":\"555444333222111000\",\"paymentPurpose\":\"PURCHASE_PREPAYMENT\","
                + "\"counterpartyCode\":\"SUP-001\",\"counterpartyName\":\"供应商甲\","
                + "\"sourceStatementId\":\"111222333444555666\",\"sourcePurchaseOrderId\":\"222333444555666777\","
                + "\"purchaseOrderNo\":\"PO-2026-001\",\"supplierCode\":\"SUP-001\",\"supplierName\":\"供应商甲\","
                + "\"settlementCompanyId\":\"888777666555444333\",\"settlementCompanyName\":\"结算公司A\","
                + "\"accountId\":null,\"paymentDate\":\"2026-09-01\",\"payType\":\"BANK_TRANSFER\","
                + "\"amount\":1000.00,\"status\":\"PENDING_APPROVAL\",\"operatorName\":\"张三\","
                + "\"remark\":\"备注\",\"items\":[{\"id\":\"987654321098765432\","
                + "\"sourceStatementId\":\"111222333444555666\",\"sourceFreightStatementId\":null,"
                + "\"allocatedAmount\":80.00}],\"audit\":false}";

        PaymentRequest request = objectMapper.readValue(json, PaymentRequest.class);

        assertThat(request.paymentNo()).isEqualTo("PAY-2026-001");
        assertThat(request.counterpartyType()).isEqualTo("SUPPLIER_PAYMENT");
        assertThat(request.counterpartyId()).isEqualTo(555444333222111000L);
        assertThat(request.paymentPurpose()).isEqualTo("PURCHASE_PREPAYMENT");
        assertThat(request.counterpartyCode()).isEqualTo("SUP-001");
        assertThat(request.counterpartyName()).isEqualTo("供应商甲");
        assertThat(request.sourceStatementId()).isEqualTo(111222333444555666L);
        assertThat(request.sourcePurchaseOrderId()).isEqualTo(222333444555666777L);
        assertThat(request.purchaseOrderNo()).isEqualTo("PO-2026-001");
        assertThat(request.supplierCode()).isEqualTo("SUP-001");
        assertThat(request.supplierName()).isEqualTo("供应商甲");
        assertThat(request.settlementCompanyId()).isEqualTo(888777666555444333L);
        assertThat(request.settlementCompanyName()).isEqualTo("结算公司A");
        assertThat(request.accountId()).isNull();
        assertThat(request.paymentDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(request.payType()).isEqualTo("BANK_TRANSFER");
        assertThat(request.amount()).isEqualByComparingTo("1000.00");
        assertThat(request.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(request.operatorName()).isEqualTo("张三");
        assertThat(request.remark()).isEqualTo("备注");
        assertThat(request.audit()).isFalse();
        assertThat(request.items()).hasSize(1);
        PaymentAllocationRequest item = request.items().get(0);
        assertThat(item.id()).isEqualTo(987654321098765432L);
        assertThat(item.sourceStatementId()).isEqualTo(111222333444555666L);
        assertThat(item.sourceFreightStatementId()).isNull();
        assertThat(item.allocatedAmount()).isEqualByComparingTo("80.00");
        assertThat(item.isStatementSourceValid()).isTrue();
    }

    /**
     * 反序列化会按字段名重新应用 BigDecimal 定标（如 amount: 1000 → 1000.00），
     * record equals 对 scale 敏感，因此以再序列化 JSON 逐字节一致为等价标准。
     */
    @Test
    void responseRoundTripKeepsJsonStable() throws Exception {
        PaymentResponse deserialized = objectMapper.readValue(
                objectMapper.writeValueAsString(sampleResponse()), PaymentResponse.class);

        assertThat(objectMapper.writeValueAsString(deserialized))
                .isEqualTo(objectMapper.writeValueAsString(sampleResponse()));
    }
}
