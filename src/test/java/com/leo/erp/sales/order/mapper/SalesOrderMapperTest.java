package com.leo.erp.sales.order.mapper;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderMapperTest {

    private final SalesOrderMapper mapper = Mappers.getMapper(SalesOrderMapper.class);

    @Test
    void shouldMapEntityToResponse() {
        SalesOrder entity = new SalesOrder();
        entity.setId(1L);
        entity.setOrderNo("SO-001");
        entity.setPurchaseInboundNo("PI-001");
        entity.setPurchaseOrderNo("PO-001");
        entity.setCustomerCode("C-001");
        entity.setCustomerName("客户甲");
        entity.setProjectId(100L);
        entity.setProjectName("项目A");
        entity.setDeliveryDate(LocalDate.of(2026, 5, 20));
        entity.setSalesName("张三");
        entity.setTotalWeight(new BigDecimal("5.000"));
        entity.setTotalAmount(new BigDecimal("15000.00"));
        entity.setStatus("已确认");
        entity.setRemark("测试备注");

        SalesOrderResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.orderNo()).isEqualTo("SO-001");
        assertThat(response.purchaseInboundNo()).isEqualTo("PI-001");
        assertThat(response.purchaseOrderNo()).isEqualTo("PO-001");
        assertThat(response.customerCode()).isEqualTo("C-001");
        assertThat(response.customerName()).isEqualTo("客户甲");
        assertThat(response.projectId()).isEqualTo(100L);
        assertThat(response.projectName()).isEqualTo("项目A");
        assertThat(response.deliveryDate()).isEqualTo(LocalDate.of(2026, 5, 20));
        assertThat(response.salesName()).isEqualTo("张三");
        assertThat(response.totalWeight()).isEqualByComparingTo("5.000");
        assertThat(response.totalAmount()).isEqualByComparingTo("15000.00");
        assertThat(response.status()).isEqualTo("已确认");
        assertThat(response.remark()).isEqualTo("测试备注");
        assertThat(response.items()).isNull();
    }

    @Test
    void shouldMapNullFieldsToNull() {
        SalesOrder entity = new SalesOrder();
        entity.setId(2L);
        entity.setOrderNo("SO-002");
        entity.setPurchaseInboundNo(null);
        entity.setPurchaseOrderNo(null);
        entity.setCustomerCode(null);
        entity.setCustomerName("客户乙");
        entity.setProjectId(null);
        entity.setProjectName("项目B");
        entity.setDeliveryDate(LocalDate.of(2026, 6, 15));
        entity.setSalesName("李四");
        entity.setTotalWeight(new BigDecimal("3.000"));
        entity.setTotalAmount(new BigDecimal("9000.00"));
        entity.setStatus("待审核");
        entity.setRemark(null);

        SalesOrderResponse response = mapper.toResponse(entity);

        assertThat(response.purchaseInboundNo()).isNull();
        assertThat(response.purchaseOrderNo()).isNull();
        assertThat(response.customerCode()).isNull();
        assertThat(response.projectId()).isNull();
        assertThat(response.remark()).isNull();
        assertThat(response.items()).isNull();
    }
}
