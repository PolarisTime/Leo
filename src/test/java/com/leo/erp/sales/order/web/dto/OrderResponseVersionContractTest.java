package com.leo.erp.sales.order.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.mapper.PurchaseOrderMapperImpl;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.mapper.SalesOrderMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「列表/详情响应带 version」与「version 以十进制字符串序列化」这两条跨端契约的可执行锁（质量报告 P1-2）。
 *
 * <p>为什么单独锁一层：前端保存时把详情里的 {@code version} 原样回送到
 * {@code X-Resource-Version}。只要映射层漏掉该字段（MapStruct 未映射）或序列化形态变成 number，
 * 前端就会永久失去表达「我编辑的是哪一版」的能力，而两个接口本身都还是 200，不会被别的测试发现。</p>
 */
class OrderResponseVersionContractTest {

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
    void salesOrderResponse_mapsEntityVersionAndSerializesItAsString() throws Exception {
        SalesOrder entity = new SalesOrder();
        entity.setId(9007199254740993L);
        entity.setOrderNo("SO001");
        entity.setVersion(7L);

        SalesOrderResponse response = new SalesOrderMapperImpl().toResponse(entity);

        assertThat(response.version()).isEqualTo(7L);
        assertThat(objectMapper.writeValueAsString(response)).contains("\"version\":\"7\"");
    }

    @Test
    void purchaseOrderResponse_mapsEntityVersionAndSerializesItAsString() throws Exception {
        PurchaseOrder entity = new PurchaseOrder();
        entity.setId(9007199254740995L);
        entity.setOrderNo("PO001");
        entity.setVersion(12L);

        PurchaseOrderResponse response = new PurchaseOrderMapperImpl().toResponse(entity);

        assertThat(response.version()).isEqualTo(12L);
        assertThat(objectMapper.writeValueAsString(response)).contains("\"version\":\"12\"");
    }
}
