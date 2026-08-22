package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SalesOrderAuditedPricingService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderAuditedPricingServiceTest {

    @Mock
    private SalesOrderOutboundPricingSyncService outboundPricingSyncService;

    @InjectMocks
    private SalesOrderAuditedPricingService service;

    private SalesOrderItem salesOrderItem(Long id, Integer lineNo) {
        SalesOrderItem i = new SalesOrderItem();
        i.setId(id);
        i.setLineNo(lineNo);
        i.setMaterialId(500L);
        i.setMaterialCode("M001");
        i.setBrand("品牌A");
        i.setCategory("型钢");
        i.setMaterial("螺纹钢");
        i.setSpec("HRB400");
        i.setLength("12m");
        i.setUnit("吨");
        i.setSourceInboundItemId(700L);
        i.setSourcePurchaseOrderItemId(800L);
        i.setWarehouseId(1L);
        i.setWarehouseName("库房A");
        i.setBatchNo("B001");
        i.setQuantity(10);
        i.setQuantityUnit("件");
        i.setPieceWeightTon(new BigDecimal("1.250"));
        i.setPiecesPerBundle(100);
        i.setWeightTon(new BigDecimal("12.500"));
        i.setUnitPrice(new BigDecimal("4000.00"));
        i.setAmount(new BigDecimal("50000.00"));
        return i;
    }

    private SalesOrder salesOrder(String status, List<SalesOrderItem> items) {
        SalesOrder o = new SalesOrder();
        o.setId(1L);
        o.setOrderNo("SO001");
        o.setPurchaseInboundNo("IB001");
        o.setPurchaseOrderNo("PO001");
        o.setCustomerCode("CUST001");
        o.setCustomerId(10L);
        o.setCustomerName("客户A");
        o.setProjectId(20L);
        o.setProjectName("项目A");
        o.setSalesName("销售员A");
        o.setStatus(status);
        o.setItems(items);
        return o;
    }

    private SalesOrderItemRequest itemRequest(Long id, BigDecimal unitPrice) {
        return new SalesOrderItemRequest(
                id, 500L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                700L, 800L, 1L, "库房A", "B001", 10, "件", new BigDecimal("1.250"), 100,
                new BigDecimal("12.500"), unitPrice, null);
    }

    private SalesOrderRequest request(String status, String orderNo, List<SalesOrderItemRequest> items) {
        return new SalesOrderRequest(
                orderNo, "IB001", "PO001", "CUST001", 10L, "客户A", 20L, "项目A", null, null,
                LocalDate.of(2026, 8, 1), "销售员A", status, null, items, List.of(), false);
    }

    // ---------- isAuditedPricingUpdate ----------

    @Test
    void isAuditedPricingUpdate_shouldBeTrueForAuditedStatus() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = request(StatusConstants.AUDITED, "SO001", List.of(itemRequest(1L, null)));

        assertThat(service.isAuditedPricingUpdate(entity, request)).isTrue();
    }

    @Test
    void isAuditedPricingUpdate_shouldBeTrueForDeliveryVerification() {
        SalesOrder entity = salesOrder(StatusConstants.DELIVERY_VERIFICATION, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = request(StatusConstants.DELIVERY_VERIFICATION, "SO001", List.of(itemRequest(1L, null)));

        assertThat(service.isAuditedPricingUpdate(entity, request)).isTrue();
    }

    @Test
    void isAuditedPricingUpdate_shouldBeFalseForNonProtectedStatus() {
        SalesOrder entity = salesOrder(StatusConstants.DRAFT, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = request(StatusConstants.DRAFT, "SO001", List.of(itemRequest(1L, null)));

        assertThat(service.isAuditedPricingUpdate(entity, request)).isFalse();
    }

    @Test
    void isAuditedPricingUpdate_shouldBeFalseWhenRequestedStatusMismatch() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = request(StatusConstants.DRAFT, "SO001", List.of(itemRequest(1L, null)));

        assertThat(service.isAuditedPricingUpdate(entity, request)).isFalse();
    }

    @Test
    void isAuditedPricingUpdate_shouldBeFalseWhenFieldsMismatch() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = request(StatusConstants.AUDITED, "DIFFERENT", List.of(itemRequest(1L, null)));

        assertThat(service.isAuditedPricingUpdate(entity, request)).isFalse();
    }

    // ---------- matchesAuditedPricingUpdate ----------

    @Test
    void matches_shouldBeFalseForNullEntity() {
        assertThat(service.matchesAuditedPricingUpdate(null, request(StatusConstants.AUDITED, "SO001", List.of())))
                .isFalse();
    }

    @Test
    void matches_shouldBeFalseForNullRequest() {
        assertThat(service.matchesAuditedPricingUpdate(salesOrder(StatusConstants.AUDITED, List.of()), null))
                .isFalse();
    }

    @Test
    void matches_shouldBeFalseWhenItemCountDiffers() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = request(StatusConstants.AUDITED, "SO001",
                List.of(itemRequest(1L, null), itemRequest(2L, null)));

        assertThat(service.matchesAuditedPricingUpdate(entity, request)).isFalse();
    }

    @Test
    void matches_shouldBeFalseWhenRequestItemsNull() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = request(StatusConstants.AUDITED, "SO001", null);

        assertThat(service.matchesAuditedPricingUpdate(entity, request)).isFalse();
    }

    @Test
    void matches_shouldBeFalseWhenItemIdNotResolved() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = request(StatusConstants.AUDITED, "SO001", List.of(itemRequest(99L, null)));

        assertThat(service.matchesAuditedPricingUpdate(entity, request)).isFalse();
    }

    @Test
    void matches_shouldBeFalseWhenItemMaterialMismatch() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderItemRequest bad = new SalesOrderItemRequest(
                1L, 500L, "M999", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                700L, 800L, 1L, "库房A", "B001", 10, "件", new BigDecimal("1.250"), 100,
                new BigDecimal("12.500"), null, null);
        SalesOrderRequest request = request(StatusConstants.AUDITED, "SO001", List.of(bad));

        assertThat(service.matchesAuditedPricingUpdate(entity, request)).isFalse();
    }

    @Test
    void matches_shouldBeTrueWhenAllFieldsMatch() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = request(StatusConstants.AUDITED, "SO001", List.of(itemRequest(1L, null)));

        assertThat(service.matchesAuditedPricingUpdate(entity, request)).isTrue();
    }

    // ---------- applyAuditedPricingUpdate ----------

    @Test
    void apply_shouldUpdatePricingAndSyncOutbound() {
        SalesOrderItem item = salesOrderItem(1L, 1);
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(item));
        SalesOrderRequest request = request(StatusConstants.AUDITED, "SO001",
                List.of(itemRequest(1L, new BigDecimal("5000"))));

        service.applyAuditedPricingUpdate(entity, request);

        assertThat(item.getUnitPrice()).isEqualByComparingTo("5000");
        assertThat(item.getAmount()).isEqualByComparingTo("62500"); // 12.5 * 5000
        assertThat(entity.getTotalAmount()).isEqualByComparingTo("62500");
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<Long, BigDecimal>> priceCaptor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(outboundPricingSyncService).syncAuditedOutboundPricing(eq(List.of(1L)), priceCaptor.capture());
        assertThat(priceCaptor.getValue().get(1L)).isEqualByComparingTo("5000");
    }

    @Test
    void apply_shouldTreatNullUnitPriceAsZero() {
        SalesOrderItem item = salesOrderItem(1L, 1);
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(item));
        SalesOrderRequest request = request(StatusConstants.AUDITED, "SO001",
                List.of(itemRequest(1L, null)));

        service.applyAuditedPricingUpdate(entity, request);

        assertThat(item.getAmount()).isEqualByComparingTo("0");
        assertThat(entity.getTotalAmount()).isEqualByComparingTo("0");
    }

    @Test
    void apply_shouldRejectMissingRequestItemForEntityItem() {
        // 实体内明细在请求中不存在 → requestItemMap.get 返回 null → NPE（残余风险）
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = request(StatusConstants.AUDITED, "SO001", List.of());

        assertThatThrownBy(() -> service.applyAuditedPricingUpdate(entity, request))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void apply_shouldSkipSyncWhenSyncServiceNull() {
        SalesOrderAuditedPricingService svc = new SalesOrderAuditedPricingService(null);
        SalesOrderItem item = salesOrderItem(1L, 1);
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(item));
        SalesOrderRequest request = request(StatusConstants.AUDITED, "SO001", List.of(itemRequest(1L, null)));

        svc.applyAuditedPricingUpdate(entity, request);

        assertThat(entity.getTotalAmount()).isEqualByComparingTo("0");
    }

    @Test
    void apply_shouldSkipSyncWhenEntityHasNoItems() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of());
        SalesOrderRequest request = request(StatusConstants.AUDITED, "SO001", List.of());

        service.applyAuditedPricingUpdate(entity, request);

        assertThat(entity.getTotalAmount()).isEqualByComparingTo("0");
        verifyNoInteractions(outboundPricingSyncService);
    }

    // ---------- 主字段逐项不匹配 ----------

    private SalesOrderRequest requestFull(String orderNo, String inboundNo, String poNo, String customerCode,
                                          Long customerId, String customerName, Long projectId,
                                          String projectName, String salesName, List<SalesOrderItemRequest> items) {
        return new SalesOrderRequest(
                orderNo, inboundNo, poNo, customerCode, customerId, customerName, projectId, projectName,
                null, null, LocalDate.of(2026, 8, 1), salesName, StatusConstants.AUDITED, null, items, List.of(), false);
    }

    @Test
    void matches_shouldBeFalseWhenInboundNoMismatch() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = requestFull("SO001", "IB999", "PO001", "CUST001", 10L, "客户A", 20L, "项目A", "销售员A", List.of(itemRequest(1L, null)));
        assertThat(service.matchesAuditedPricingUpdate(entity, request)).isFalse();
    }

    @Test
    void matches_shouldBeFalseWhenPurchaseOrderNoMismatch() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = requestFull("SO001", "IB001", "PO999", "CUST001", 10L, "客户A", 20L, "项目A", "销售员A", List.of(itemRequest(1L, null)));
        assertThat(service.matchesAuditedPricingUpdate(entity, request)).isFalse();
    }

    @Test
    void matches_shouldBeFalseWhenCustomerCodeMismatch() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = requestFull("SO001", "IB001", "PO001", "OTHER", 10L, "客户A", 20L, "项目A", "销售员A", List.of(itemRequest(1L, null)));
        assertThat(service.matchesAuditedPricingUpdate(entity, request)).isFalse();
    }

    @Test
    void matches_shouldBeFalseWhenCustomerIdMismatch() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = requestFull("SO001", "IB001", "PO001", "CUST001", 99L, "客户A", 20L, "项目A", "销售员A", List.of(itemRequest(1L, null)));
        assertThat(service.matchesAuditedPricingUpdate(entity, request)).isFalse();
    }

    @Test
    void matches_shouldBeFalseWhenCustomerNameMismatch() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = requestFull("SO001", "IB001", "PO001", "CUST001", 10L, "其他客户", 20L, "项目A", "销售员A", List.of(itemRequest(1L, null)));
        assertThat(service.matchesAuditedPricingUpdate(entity, request)).isFalse();
    }

    @Test
    void matches_shouldBeFalseWhenProjectIdMismatch() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = requestFull("SO001", "IB001", "PO001", "CUST001", 10L, "客户A", 99L, "项目A", "销售员A", List.of(itemRequest(1L, null)));
        assertThat(service.matchesAuditedPricingUpdate(entity, request)).isFalse();
    }

    @Test
    void matches_shouldBeFalseWhenProjectNameMismatch() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = requestFull("SO001", "IB001", "PO001", "CUST001", 10L, "客户A", 20L, "其他项目", "销售员A", List.of(itemRequest(1L, null)));
        assertThat(service.matchesAuditedPricingUpdate(entity, request)).isFalse();
    }

    @Test
    void matches_shouldBeFalseWhenSalesNameMismatch() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = requestFull("SO001", "IB001", "PO001", "CUST001", 10L, "客户A", 20L, "项目A", "其他销售", List.of(itemRequest(1L, null)));
        assertThat(service.matchesAuditedPricingUpdate(entity, request)).isFalse();
    }

    // ---------- 明细字段不匹配 ----------

    @Test
    void matches_shouldBeFalseWhenItemMaterialIdMismatch() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderItemRequest bad = new SalesOrderItemRequest(
                1L, 999L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                700L, 800L, 1L, "库房A", "B001", 10, "件", new BigDecimal("1.250"), 100,
                new BigDecimal("12.500"), null, null);
        SalesOrderRequest request = request(StatusConstants.AUDITED, "SO001", List.of(bad));
        assertThat(service.matchesAuditedPricingUpdate(entity, request)).isFalse();
    }

    @Test
    void matches_shouldBeFalseWhenItemQuantityMismatch() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderItemRequest bad = new SalesOrderItemRequest(
                1L, 500L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                700L, 800L, 1L, "库房A", "B001", 99, "件", new BigDecimal("1.250"), 100,
                new BigDecimal("12.500"), null, null);
        SalesOrderRequest request = request(StatusConstants.AUDITED, "SO001", List.of(bad));
        assertThat(service.matchesAuditedPricingUpdate(entity, request)).isFalse();
    }

    @Test
    void matches_shouldBeFalseWhenItemWeightMismatch() {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        SalesOrderItemRequest bad = new SalesOrderItemRequest(
                1L, 500L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                700L, 800L, 1L, "库房A", "B001", 10, "件", new BigDecimal("1.250"), 100,
                new BigDecimal("13.000"), null, null);
        SalesOrderRequest request = request(StatusConstants.AUDITED, "SO001", List.of(bad));
        assertThat(service.matchesAuditedPricingUpdate(entity, request)).isFalse();
    }

    @Test
    void isAuditedPricingUpdate_shouldBeFalseForNullEntityStatus() {
        SalesOrder entity = salesOrder(null, List.of(salesOrderItem(1L, 1)));
        SalesOrderRequest request = request(StatusConstants.AUDITED, "SO001", List.of(itemRequest(1L, null)));

        assertThat(service.isAuditedPricingUpdate(entity, request)).isFalse();
    }

    // ---------- 明细剩余字段不匹配 ----------

    private SalesOrderItemRequest rawItemRequest(Long id, String materialCode, String brand, String category,
                                                 String material, String spec, String length, String unit,
                                                 Long sourceInboundItemId, Long sourcePurchaseOrderItemId,
                                                 Long warehouseId, String warehouseName, String batchNo,
                                                 Integer quantity, String quantityUnit, String pieceWeightTon,
                                                 Integer piecesPerBundle, String weightTon) {
        return new SalesOrderItemRequest(
                id, 500L, materialCode, brand, category, material, spec, length, unit,
                sourceInboundItemId, sourcePurchaseOrderItemId, warehouseId, warehouseName, batchNo,
                quantity, quantityUnit, pieceWeightTon == null ? null : new BigDecimal(pieceWeightTon),
                piecesPerBundle, weightTon == null ? null : new BigDecimal(weightTon), null, null);
    }

    private void assertItemMismatch(SalesOrderItemRequest bad) {
        SalesOrder entity = salesOrder(StatusConstants.AUDITED, List.of(salesOrderItem(1L, 1)));
        assertThat(service.matchesAuditedPricingUpdate(entity,
                request(StatusConstants.AUDITED, "SO001", List.of(bad)))).isFalse();
    }

    @Test
    void matches_shouldBeFalseWhenItemMaterialCodeMismatch() {
        assertItemMismatch(rawItemRequest(1L, "M999", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                700L, 800L, 1L, "库房A", "B001", 10, "件", "1.250", 100, "12.500"));
    }

    @Test
    void matches_shouldBeFalseWhenItemBrandMismatch() {
        assertItemMismatch(rawItemRequest(1L, "M001", "其他品牌", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                700L, 800L, 1L, "库房A", "B001", 10, "件", "1.250", 100, "12.500"));
    }

    @Test
    void matches_shouldBeFalseWhenItemCategoryMismatch() {
        assertItemMismatch(rawItemRequest(1L, "M001", "品牌A", "其他类", "螺纹钢", "HRB400", "12m", "吨",
                700L, 800L, 1L, "库房A", "B001", 10, "件", "1.250", 100, "12.500"));
    }

    @Test
    void matches_shouldBeFalseWhenItemMaterialMismatch2() {
        assertItemMismatch(rawItemRequest(1L, "M001", "品牌A", "型钢", "其他材质", "HRB400", "12m", "吨",
                700L, 800L, 1L, "库房A", "B001", 10, "件", "1.250", 100, "12.500"));
    }

    @Test
    void matches_shouldBeFalseWhenItemSpecMismatch() {
        assertItemMismatch(rawItemRequest(1L, "M001", "品牌A", "型钢", "螺纹钢", "其他规格", "12m", "吨",
                700L, 800L, 1L, "库房A", "B001", 10, "件", "1.250", 100, "12.500"));
    }

    @Test
    void matches_shouldBeFalseWhenItemLengthMismatch() {
        assertItemMismatch(rawItemRequest(1L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "9m", "吨",
                700L, 800L, 1L, "库房A", "B001", 10, "件", "1.250", 100, "12.500"));
    }

    @Test
    void matches_shouldBeFalseWhenItemUnitMismatch() {
        assertItemMismatch(rawItemRequest(1L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "根",
                700L, 800L, 1L, "库房A", "B001", 10, "件", "1.250", 100, "12.500"));
    }

    @Test
    void matches_shouldBeFalseWhenItemSourceInboundMismatch() {
        assertItemMismatch(rawItemRequest(1L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                999L, 800L, 1L, "库房A", "B001", 10, "件", "1.250", 100, "12.500"));
    }

    @Test
    void matches_shouldBeFalseWhenItemSourcePurchaseMismatch() {
        assertItemMismatch(rawItemRequest(1L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                700L, 999L, 1L, "库房A", "B001", 10, "件", "1.250", 100, "12.500"));
    }

    @Test
    void matches_shouldBeFalseWhenItemWarehouseIdMismatch() {
        assertItemMismatch(rawItemRequest(1L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                700L, 800L, 9L, "库房A", "B001", 10, "件", "1.250", 100, "12.500"));
    }

    @Test
    void matches_shouldBeFalseWhenItemWarehouseNameMismatch() {
        assertItemMismatch(rawItemRequest(1L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                700L, 800L, 1L, "其他库房", "B001", 10, "件", "1.250", 100, "12.500"));
    }

    @Test
    void matches_shouldBeFalseWhenItemBatchMismatch() {
        assertItemMismatch(rawItemRequest(1L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                700L, 800L, 1L, "库房A", "B999", 10, "件", "1.250", 100, "12.500"));
    }

    @Test
    void matches_shouldBeFalseWhenItemQuantityUnitMismatch() {
        assertItemMismatch(rawItemRequest(1L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                700L, 800L, 1L, "库房A", "B001", 10, "根", "1.250", 100, "12.500"));
    }

    @Test
    void matches_shouldBeFalseWhenItemPieceWeightMismatch() {
        assertItemMismatch(rawItemRequest(1L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                700L, 800L, 1L, "库房A", "B001", 10, "件", "1.999", 100, "12.500"));
    }

    @Test
    void matches_shouldBeFalseWhenItemPiecesPerBundleMismatch() {
        assertItemMismatch(rawItemRequest(1L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                700L, 800L, 1L, "库房A", "B001", 10, "件", "1.250", 999, "12.500"));
    }
}
