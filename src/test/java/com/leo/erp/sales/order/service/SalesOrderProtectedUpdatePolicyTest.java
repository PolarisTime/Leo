package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderProtectedUpdatePolicyTest {

    private final SalesOrderProtectedUpdatePolicy policy =
            new SalesOrderProtectedUpdatePolicy(new SalesOrderAuditedPricingService(null));

    @Test
    void shouldAllowAuditedOrderToReturnDraftWhenOnlyStatusChanges() {
        SalesOrder order = auditedSalesOrder();
        SalesOrderRequest request = requestFrom(order, StatusConstants.DRAFT, order.getDeliveryDate(), 2, order.getItems().get(0).getUnitPrice());

        assertThat(policy.allowsProtectedUpdate(order, request)).isTrue();
    }

    @Test
    void shouldRejectReturnDraftWhenBusinessFieldsChange() {
        SalesOrder order = auditedSalesOrder();
        SalesOrderRequest request = requestFrom(order, StatusConstants.DRAFT, order.getDeliveryDate().plusDays(1), 2, order.getItems().get(0).getUnitPrice());

        assertThat(policy.allowsProtectedUpdate(order, request)).isFalse();
    }

    @Test
    void shouldRejectReturnDraftWhenItemQuantityChanges() {
        SalesOrder order = auditedSalesOrder();
        SalesOrderRequest request = requestFrom(order, StatusConstants.DRAFT, order.getDeliveryDate(), 3, order.getItems().get(0).getUnitPrice());

        assertThat(policy.allowsProtectedUpdate(order, request)).isFalse();
    }

    @Test
    void shouldAllowAuditedPricingUpdateThroughPricingPolicy() {
        SalesOrder order = auditedSalesOrder();
        SalesOrderRequest request = requestFrom(order, StatusConstants.AUDITED, order.getDeliveryDate(), 2, new BigDecimal("3200.00"));

        assertThat(policy.allowsProtectedUpdate(order, request)).isTrue();
    }

    @Test
    void shouldRejectProtectedUpdateFromNonAuditedStatus() {
        SalesOrder order = auditedSalesOrder();
        order.setStatus(StatusConstants.DRAFT);
        SalesOrderRequest request = requestFrom(order, StatusConstants.AUDITED, order.getDeliveryDate(), 2, order.getItems().get(0).getUnitPrice());

        assertThat(policy.allowsProtectedUpdate(order, request)).isFalse();
    }

    private SalesOrder auditedSalesOrder() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-POLICY-001");
        order.setPurchaseInboundNo("PI-001");
        order.setPurchaseOrderNo("PO-001");
        order.setCustomerCode("C001");
        order.setCustomerName("客户A");
        order.setProjectId(101L);
        order.setProjectName("项目A");
        order.setDeliveryDate(LocalDate.of(2026, 4, 26));
        order.setSalesName("张三");
        order.setStatus(StatusConstants.AUDITED);
        order.setRemark("备注");
        order.setTotalWeight(new BigDecimal("4.496"));
        order.setTotalAmount(new BigDecimal("13488.00"));

        SalesOrderItem item = new SalesOrderItem();
        item.setId(11L);
        item.setSalesOrder(order);
        item.setLineNo(1);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("盘螺");
        item.setMaterial("HRB400");
        item.setSpec("8");
        item.setLength("12m");
        item.setUnit("吨");
        item.setSourceInboundItemId(21L);
        item.setSourcePurchaseOrderItemId(31L);
        item.setWarehouseName("一号库");
        item.setBatchNo("B1");
        item.setQuantity(2);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("2.248"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("4.496"));
        item.setUnitPrice(new BigDecimal("3000.00"));
        item.setAmount(new BigDecimal("13488.00"));
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }

    private SalesOrderRequest requestFrom(SalesOrder order,
                                          String status,
                                          LocalDate deliveryDate,
                                          Integer quantity,
                                          BigDecimal unitPrice) {
        SalesOrderItem item = order.getItems().get(0);
        return new SalesOrderRequest(
                order.getOrderNo(),
                order.getPurchaseInboundNo(),
                order.getPurchaseOrderNo(),
                order.getCustomerCode(),
                order.getCustomerName(),
                order.getProjectId(),
                order.getProjectName(),
                deliveryDate,
                order.getSalesName(),
                status,
                order.getRemark(),
                List.of(new SalesOrderItemRequest(
                        item.getId(),
                        item.getMaterialCode(),
                        item.getBrand(),
                        item.getCategory(),
                        item.getMaterial(),
                        item.getSpec(),
                        item.getLength(),
                        item.getUnit(),
                        item.getSourceInboundItemId(),
                        item.getSourcePurchaseOrderItemId(),
                        item.getWarehouseName(),
                        item.getBatchNo(),
                        quantity,
                        item.getQuantityUnit(),
                        item.getPieceWeightTon(),
                        item.getPiecesPerBundle(),
                        item.getWeightTon(),
                        unitPrice,
                        item.getAmount()
                ))
        );
    }
}
