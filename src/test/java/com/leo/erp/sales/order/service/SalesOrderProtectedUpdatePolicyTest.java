package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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

    @Test
    void shouldRejectProtectedUpdateWhenNextStatusIsNeitherDraftNorAudited() {
        SalesOrder order = auditedSalesOrder();
        SalesOrderRequest request = requestFrom(order, StatusConstants.SALES_COMPLETED, order.getDeliveryDate(), 2, order.getItems().get(0).getUnitPrice());

        assertThat(policy.allowsProtectedUpdate(order, request)).isFalse();
    }

    @Test
    void shouldAllowDeliveryVerificationDateRemarkAndPricingUpdate() {
        SalesOrder order = auditedSalesOrder();
        order.setStatus(StatusConstants.DELIVERY_VERIFICATION);
        SalesOrderRequest request = requestWith(order, StatusConstants.DELIVERY_VERIFICATION, builder -> {
            builder.deliveryDate = order.getDeliveryDate().plusDays(1);
            builder.remark = "完成销售后调整";
            builder.items = List.of(itemRequestWith(order.getItems().get(0), itemBuilder ->
                    itemBuilder.unitPrice = new BigDecimal("3200.00")));
        });

        assertThat(policy.allowsProtectedUpdate(order, request)).isTrue();
    }

    @Test
    void shouldRejectStatusOnlyUpdateWhenPrivateInputsAreNull() throws ReflectiveOperationException {
        SalesOrder order = auditedSalesOrder();
        SalesOrderRequest request = requestFrom(order, StatusConstants.DRAFT, order.getDeliveryDate(), 2, order.getItems().get(0).getUnitPrice());

        assertThat(invokeStatusOnlyUpdate(null, request)).isFalse();
        assertThat(invokeStatusOnlyUpdate(order, null)).isFalse();
    }

    @Test
    void shouldRejectReturnDraftWhenAnyProtectedHeaderFieldChanges() {
        SalesOrder order = auditedSalesOrder();

        assertRejected(order, "orderNo", requestWith(order, StatusConstants.DRAFT, builder -> builder.orderNo = "SO-CHANGED"));
        assertRejected(order, "purchaseInboundNo", requestWith(order, StatusConstants.DRAFT, builder -> builder.purchaseInboundNo = "PI-CHANGED"));
        assertRejected(order, "purchaseOrderNo", requestWith(order, StatusConstants.DRAFT, builder -> builder.purchaseOrderNo = "PO-CHANGED"));
        assertRejected(order, "customerCode", requestWith(order, StatusConstants.DRAFT, builder -> builder.customerCode = "C999"));
        assertRejected(order, "customerId", requestWith(order, StatusConstants.DRAFT, builder -> builder.customerId = 1002L));
        assertRejected(order, "customerName", requestWith(order, StatusConstants.DRAFT, builder -> builder.customerName = "客户B"));
        assertRejected(order, "projectId", requestWith(order, StatusConstants.DRAFT, builder -> builder.projectId = 202L));
        assertRejected(order, "projectName", requestWith(order, StatusConstants.DRAFT, builder -> builder.projectName = "项目B"));
        assertRejected(order, "deliveryDate", requestWith(order, StatusConstants.DRAFT, builder -> builder.deliveryDate = order.getDeliveryDate().plusDays(1)));
        assertRejected(order, "salesName", requestWith(order, StatusConstants.DRAFT, builder -> builder.salesName = "李四"));
        assertRejected(order, "remark", requestWith(order, StatusConstants.DRAFT, builder -> builder.remark = "新备注"));
    }

    @Test
    void shouldRejectReturnDraftWhenRequestItemsAreNull() {
        SalesOrder order = auditedSalesOrder();
        SalesOrderRequest request = requestWith(order, StatusConstants.DRAFT, builder -> builder.items = null);

        assertThat(policy.allowsProtectedUpdate(order, request)).isFalse();
    }

    @Test
    void shouldRejectReturnDraftWhenAnyProtectedItemFieldChanges() {
        SalesOrder order = auditedSalesOrder();

        assertRejected(order, "item.id", requestWithItem(order, builder -> builder.id = 12L));
        assertRejected(order, "item.materialId", requestWithItem(order, builder -> builder.materialId = 502L));
        assertRejected(order, "item.materialCode", requestWithItem(order, builder -> builder.materialCode = "M2"));
        assertRejected(order, "item.brand", requestWithItem(order, builder -> builder.brand = "鞍钢"));
        assertRejected(order, "item.category", requestWithItem(order, builder -> builder.category = "螺纹"));
        assertRejected(order, "item.material", requestWithItem(order, builder -> builder.material = "HRB500"));
        assertRejected(order, "item.spec", requestWithItem(order, builder -> builder.spec = "10"));
        assertRejected(order, "item.length", requestWithItem(order, builder -> builder.length = "9m"));
        assertRejected(order, "item.unit", requestWithItem(order, builder -> builder.unit = "支"));
        assertRejected(order, "item.sourceInboundItemId", requestWithItem(order, builder -> builder.sourceInboundItemId = 22L));
        assertRejected(order, "item.sourcePurchaseOrderItemId", requestWithItem(order, builder -> builder.sourcePurchaseOrderItemId = 32L));
        assertRejected(order, "item.warehouseId", requestWithItem(order, builder -> builder.warehouseId = 602L));
        assertRejected(order, "item.warehouseName", requestWithItem(order, builder -> builder.warehouseName = "二号库"));
        assertRejected(order, "item.batchNo", requestWithItem(order, builder -> builder.batchNo = "B2"));
        assertRejected(order, "item.quantity", requestWithItem(order, builder -> builder.quantity = 3));
        assertRejected(order, "item.quantityUnit", requestWithItem(order, builder -> builder.quantityUnit = "吨"));
        assertRejected(order, "item.pieceWeightTon", requestWithItem(order, builder -> builder.pieceWeightTon = new BigDecimal("2.249")));
        assertRejected(order, "item.piecesPerBundle", requestWithItem(order, builder -> builder.piecesPerBundle = 2));
        assertRejected(order, "item.weightTon", requestWithItem(order, builder -> builder.weightTon = new BigDecimal("4.497")));
        assertRejected(order, "item.unitPrice", requestWithItem(order, builder -> builder.unitPrice = new BigDecimal("3001.00")));
        assertRejected(order, "item.amount", requestWithItem(order, builder -> builder.amount = new BigDecimal("13489.00")));
    }

    @Test
    void shouldAllowReturnDraftWhenNumericScaleAndBlankQuantityUnitNormalizeToSameValues() {
        SalesOrder order = auditedSalesOrder();
        SalesOrderRequest request = requestWithItem(order, builder -> {
            builder.quantityUnit = " ";
            builder.pieceWeightTon = new BigDecimal("2.2480000");
            builder.weightTon = new BigDecimal("4.4960000");
            builder.unitPrice = new BigDecimal("3000.0");
            builder.amount = new BigDecimal("13488.0");
        });

        assertThat(policy.allowsProtectedUpdate(order, request)).isTrue();
    }

    private SalesOrder auditedSalesOrder() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-POLICY-001");
        order.setPurchaseInboundNo("PI-001");
        order.setPurchaseOrderNo("PO-001");
        order.setCustomerCode("C001");
        order.setCustomerId(1001L);
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
        item.setMaterialId(501L);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("盘螺");
        item.setMaterial("HRB400");
        item.setSpec("8");
        item.setLength("12m");
        item.setUnit("吨");
        item.setSourceInboundItemId(21L);
        item.setSourcePurchaseOrderItemId(31L);
        item.setWarehouseId(601L);
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
        return requestWith(order, status, builder -> {
            builder.deliveryDate = deliveryDate;
            builder.items = List.of(itemRequestWith(item, itemBuilder -> {
                itemBuilder.quantity = quantity;
                itemBuilder.unitPrice = unitPrice;
            }));
        });
    }

    private boolean invokeStatusOnlyUpdate(SalesOrder entity, SalesOrderRequest request) throws ReflectiveOperationException {
        Method method = SalesOrderProtectedUpdatePolicy.class.getDeclaredMethod(
                "matchesStatusOnlyUpdate",
                SalesOrder.class,
                SalesOrderRequest.class
        );
        method.setAccessible(true);
        return (boolean) method.invoke(policy, entity, request);
    }

    private void assertRejected(SalesOrder order, String scenario, SalesOrderRequest request) {
        assertThat(policy.allowsProtectedUpdate(order, request))
                .as(scenario)
                .isFalse();
    }

    private SalesOrderRequest requestWith(SalesOrder order,
                                          String status,
                                          Consumer<SalesOrderRequestBuilder> customizer) {
        SalesOrderRequestBuilder builder = new SalesOrderRequestBuilder(order, status);
        customizer.accept(builder);
        return builder.build();
    }

    private SalesOrderRequest requestWithItem(SalesOrder order, Consumer<SalesOrderItemRequestBuilder> customizer) {
        SalesOrderItem item = order.getItems().get(0);
        return requestWith(order, StatusConstants.DRAFT, builder ->
                builder.items = List.of(itemRequestWith(item, customizer)));
    }

    private SalesOrderItemRequest itemRequestWith(SalesOrderItem item, Consumer<SalesOrderItemRequestBuilder> customizer) {
        SalesOrderItemRequestBuilder builder = new SalesOrderItemRequestBuilder(item);
        customizer.accept(builder);
        return builder.build();
    }

    private static final class SalesOrderRequestBuilder {
        private String orderNo;
        private String purchaseInboundNo;
        private String purchaseOrderNo;
        private String customerCode;
        private Long customerId;
        private String customerName;
        private Long projectId;
        private String projectName;
        private Long settlementCompanyId;
        private String settlementCompanyName;
        private LocalDate deliveryDate;
        private String salesName;
        private String status;
        private String remark;
        private List<SalesOrderItemRequest> items;

        private SalesOrderRequestBuilder(SalesOrder order, String status) {
            this.orderNo = order.getOrderNo();
            this.purchaseInboundNo = order.getPurchaseInboundNo();
            this.purchaseOrderNo = order.getPurchaseOrderNo();
            this.customerCode = order.getCustomerCode();
            this.customerId = order.getCustomerId();
            this.customerName = order.getCustomerName();
            this.projectId = order.getProjectId();
            this.projectName = order.getProjectName();
            this.settlementCompanyId = order.getSettlementCompanyId();
            this.settlementCompanyName = order.getSettlementCompanyName();
            this.deliveryDate = order.getDeliveryDate();
            this.salesName = order.getSalesName();
            this.status = status;
            this.remark = order.getRemark();
            this.items = order.getItems().stream()
                    .map(item -> new SalesOrderItemRequestBuilder(item).build())
                    .toList();
        }

        private SalesOrderRequest build() {
            return new SalesOrderRequest(
                    orderNo,
                    purchaseInboundNo,
                    purchaseOrderNo,
                    customerCode,
                    customerId,
                    customerName,
                    projectId,
                    projectName,
                    settlementCompanyId,
                    settlementCompanyName,
                    deliveryDate,
                    salesName,
                    status,
                    remark,
                    items
            );
        }
    }

    private static final class SalesOrderItemRequestBuilder {
        private Long id;
        private Long materialId;
        private String materialCode;
        private String brand;
        private String category;
        private String material;
        private String spec;
        private String length;
        private String unit;
        private Long sourceInboundItemId;
        private Long sourcePurchaseOrderItemId;
        private Long warehouseId;
        private String warehouseName;
        private String batchNo;
        private Integer quantity;
        private String quantityUnit;
        private BigDecimal pieceWeightTon;
        private Integer piecesPerBundle;
        private BigDecimal weightTon;
        private BigDecimal unitPrice;
        private BigDecimal amount;

        private SalesOrderItemRequestBuilder(SalesOrderItem item) {
            this.id = item.getId();
            this.materialId = item.getMaterialId();
            this.materialCode = item.getMaterialCode();
            this.brand = item.getBrand();
            this.category = item.getCategory();
            this.material = item.getMaterial();
            this.spec = item.getSpec();
            this.length = item.getLength();
            this.unit = item.getUnit();
            this.sourceInboundItemId = item.getSourceInboundItemId();
            this.sourcePurchaseOrderItemId = item.getSourcePurchaseOrderItemId();
            this.warehouseId = item.getWarehouseId();
            this.warehouseName = item.getWarehouseName();
            this.batchNo = item.getBatchNo();
            this.quantity = item.getQuantity();
            this.quantityUnit = item.getQuantityUnit();
            this.pieceWeightTon = item.getPieceWeightTon();
            this.piecesPerBundle = item.getPiecesPerBundle();
            this.weightTon = item.getWeightTon();
            this.unitPrice = item.getUnitPrice();
            this.amount = item.getAmount();
        }

        private SalesOrderItemRequest build() {
            return new SalesOrderItemRequest(
                    id,
                    materialId,
                    materialCode,
                    brand,
                    category,
                    material,
                    spec,
                    length,
                    unit,
                    sourceInboundItemId,
                    sourcePurchaseOrderItemId,
                    warehouseId,
                    warehouseName,
                    batchNo,
                    quantity,
                    quantityUnit,
                    pieceWeightTon,
                    piecesPerBundle,
                    weightTon,
                    unitPrice,
                    amount
            );
        }
    }
}
