package com.leo.erp.sales.order.service;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.mapper.SalesOrderMapper;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderResponseAssemblerTest {

    private final SalesOrderResponseAssembler assembler =
            new SalesOrderResponseAssembler(Mappers.getMapper(SalesOrderMapper.class));

    @Test
    void shouldAssembleDetailResponseWithItems() {
        SalesOrder order = order();
        order.getItems().add(item(order));

        SalesOrderResponse response = assembler.toDetailResponse(order);

        assertThat(response.orderNo()).isEqualTo("SO-001");
        assertThat(response.purchaseOrderNo()).isEqualTo("PO-001");
        assertThat(response.customerCode()).isEqualTo("C-001");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sourceInboundItemId()).isEqualTo(101L);
        assertThat(response.items().get(0).sourcePurchaseOrderItemId()).isEqualTo(201L);
        assertThat(response.items().get(0).originalWeightTon()).isEqualByComparingTo("0.900");
    }

    private SalesOrder order() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-001");
        order.setPurchaseInboundNo("PI-001");
        order.setPurchaseOrderNo("PO-001");
        order.setCustomerCode("C-001");
        order.setCustomerName("客户甲");
        order.setProjectId(100L);
        order.setProjectName("项目A");
        order.setDeliveryDate(LocalDate.of(2026, 5, 20));
        order.setSalesName("张三");
        order.setTotalWeight(new BigDecimal("1.000"));
        order.setTotalAmount(new BigDecimal("1000.00"));
        order.setStatus("草稿");
        order.setRemark("备注");
        return order;
    }

    private SalesOrderItem item(SalesOrder order) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(11L);
        item.setSalesOrder(order);
        item.setLineNo(1);
        item.setMaterialCode("M-001");
        item.setBrand("品牌A");
        item.setCategory("螺纹");
        item.setMaterial("盘螺");
        item.setSpec("HRB400");
        item.setLength("12");
        item.setUnit("吨");
        item.setSourceInboundItemId(101L);
        item.setSourcePurchaseOrderItemId(201L);
        item.setWarehouseName("一号仓");
        item.setBatchNo("LOT-001");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("1.000"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("1.000"));
        item.setUnitPrice(new BigDecimal("1000.00"));
        item.setAmount(new BigDecimal("1000.00"));
        item.setOriginalWeightTon(new BigDecimal("0.900"));
        return item;
    }
}
