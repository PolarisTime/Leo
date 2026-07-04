package com.leo.erp.purchase.order.mapper;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderMapperTest {

    private final PurchaseOrderMapper mapper = Mappers.getMapper(PurchaseOrderMapper.class);

    @Test
    void shouldMapEntityToResponse() {
        PurchaseOrder entity = new PurchaseOrder();
        entity.setId(1L);
        entity.setOrderNo("PO-001");
        entity.setSupplierName("供应商甲");
        entity.setOrderDate(LocalDateTime.of(2026, 5, 15, 10, 30));
        entity.setBuyerName("张三");
        entity.setTotalWeight(new BigDecimal("10.000"));
        entity.setTotalAmount(new BigDecimal("40000.00"));
        entity.setStatus("已审核");
        entity.setRemark("测试备注");

        PurchaseOrderResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.orderNo()).isEqualTo("PO-001");
        assertThat(response.supplierName()).isEqualTo("供应商甲");
        assertThat(response.orderDate()).isEqualTo(LocalDateTime.of(2026, 5, 15, 10, 30));
        assertThat(response.buyerName()).isEqualTo("张三");
        assertThat(response.totalWeight()).isEqualByComparingTo("10.000");
        assertThat(response.totalAmount()).isEqualByComparingTo("40000.00");
        assertThat(response.status()).isEqualTo("已审核");
        assertThat(response.remark()).isEqualTo("测试备注");
        assertThat(response.items()).isNull();
    }

    @Test
    void shouldMapNullFieldsToNull() {
        PurchaseOrder entity = new PurchaseOrder();
        entity.setId(2L);
        entity.setOrderNo("PO-002");
        entity.setSupplierName("供应商乙");
        entity.setOrderDate(null);
        entity.setBuyerName(null);
        entity.setTotalWeight(null);
        entity.setTotalAmount(null);
        entity.setStatus("草稿");
        entity.setRemark(null);

        PurchaseOrderResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.orderNo()).isEqualTo("PO-002");
        assertThat(response.supplierName()).isEqualTo("供应商乙");
        assertThat(response.orderDate()).isNull();
        assertThat(response.buyerName()).isNull();
        assertThat(response.totalWeight()).isNull();
        assertThat(response.totalAmount()).isNull();
        assertThat(response.status()).isEqualTo("草稿");
        assertThat(response.remark()).isNull();
        assertThat(response.items()).isNull();
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
