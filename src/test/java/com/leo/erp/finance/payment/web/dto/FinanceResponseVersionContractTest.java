package com.leo.erp.finance.payment.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.mapper.PaymentMapperImpl;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.mapper.ReceiptMapperImpl;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「财务单据列表/详情响应带 version」与「version 以十进制字符串序列化」的可执行锁（质量报告 P1-2）。
 *
 * <p>为什么单独锁一层：前端保存时把详情里的 {@code version} 原样回送到
 * {@code X-Resource-Version}。只要映射层漏掉该字段（MapStruct 未映射）或序列化形态变成 number，
 * 前端就会永久失去表达「我编辑的是哪一版」的能力，而 PUT 仍然返回 200，不会被别的测试发现。</p>
 */
class FinanceResponseVersionContractTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        Jackson2ObjectMapperBuilderCustomizer customizer =
                new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer();
        customizer.customize(builder);
        objectMapper = builder.build();
    }

    @Test
    void paymentResponse_mapsEntityVersionAndSerializesItAsString() throws Exception {
        Payment entity = new Payment();
        entity.setId(9007199254740993L);
        entity.setPaymentNo("PAY001");
        entity.setVersion(9L);

        PaymentResponse response = new PaymentMapperImpl().toResponse(entity);

        assertThat(response.version()).isEqualTo(9L);
        assertThat(objectMapper.writeValueAsString(response)).contains("\"version\":\"9\"");
    }

    @Test
    void receiptResponse_mapsEntityVersionAndSerializesItAsString() throws Exception {
        Receipt entity = new Receipt();
        entity.setId(9007199254740995L);
        entity.setReceiptNo("RC001");
        entity.setVersion(11L);

        ReceiptResponse response = new ReceiptMapperImpl().toResponse(entity);

        assertThat(response.version()).isEqualTo(11L);
        assertThat(objectMapper.writeValueAsString(response)).contains("\"version\":\"11\"");
    }
}
