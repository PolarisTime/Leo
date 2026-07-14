package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.domain.entity.SalesModes;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderAuditedPricingServiceTest {

    @Test
    void shouldRejectNullInputs() {
        var service = new SalesOrderAuditedPricingService(null);
        SalesOrder order = auditedSalesOrder(11L);
        SalesOrderRequest request = requestFrom(order, StatusConstants.AUDITED, new BigDecimal("3200.00"));

        assertThat(service.matchesAuditedPricingUpdate(null, request)).isFalse();
        assertThat(service.matchesAuditedPricingUpdate(order, null)).isFalse();
    }

    @Test
    void shouldRejectChangedHeaderFields() {
        var service = new SalesOrderAuditedPricingService(null);
        SalesOrder order = auditedSalesOrder(11L);

        List<HeaderMutation> mutations = List.of(
                new HeaderMutation("orderNo", o -> requestWithHeader(o, "SO-CHANGED",
                        o.getPurchaseInboundNo(), o.getPurchaseOrderNo(), o.getCustomerCode(), o.getCustomerName(),
                        o.getProjectId(), o.getProjectName(), o.getSalesName())),
                new HeaderMutation("purchaseInboundNo", o -> requestWithHeader(o, o.getOrderNo(),
                        "PI-CHANGED", o.getPurchaseOrderNo(), o.getCustomerCode(), o.getCustomerName(),
                        o.getProjectId(), o.getProjectName(), o.getSalesName())),
                new HeaderMutation("purchaseOrderNo", o -> requestWithHeader(o, o.getOrderNo(),
                        o.getPurchaseInboundNo(), "PO-CHANGED", o.getCustomerCode(), o.getCustomerName(),
                        o.getProjectId(), o.getProjectName(), o.getSalesName())),
                new HeaderMutation("customerCode", o -> requestWithHeader(o, o.getOrderNo(),
                        o.getPurchaseInboundNo(), o.getPurchaseOrderNo(), "C999", o.getCustomerName(),
                        o.getProjectId(), o.getProjectName(), o.getSalesName())),
                new HeaderMutation("customerName", o -> requestWithHeader(o, o.getOrderNo(),
                        o.getPurchaseInboundNo(), o.getPurchaseOrderNo(), o.getCustomerCode(), "客户B",
                        o.getProjectId(), o.getProjectName(), o.getSalesName())),
                new HeaderMutation("projectId", o -> requestWithHeader(o, o.getOrderNo(),
                        o.getPurchaseInboundNo(), o.getPurchaseOrderNo(), o.getCustomerCode(), o.getCustomerName(),
                        202L, o.getProjectName(), o.getSalesName())),
                new HeaderMutation("projectName", o -> requestWithHeader(o, o.getOrderNo(),
                        o.getPurchaseInboundNo(), o.getPurchaseOrderNo(), o.getCustomerCode(), o.getCustomerName(),
                        o.getProjectId(), "项目B", o.getSalesName())),
                new HeaderMutation("salesName", o -> requestWithHeader(o, o.getOrderNo(),
                        o.getPurchaseInboundNo(), o.getPurchaseOrderNo(), o.getCustomerCode(), o.getCustomerName(),
                        o.getProjectId(), o.getProjectName(), "李四"))
        );

        for (HeaderMutation mutation : mutations) {
            assertThat(service.matchesAuditedPricingUpdate(order, mutation.requestFactory().create(order)))
                    .as(mutation.name())
                    .isFalse();
        }
    }

    @Test
    void shouldRejectChangedCustomerIdForAuditedPricingUpdate() {
        var service = new SalesOrderAuditedPricingService(null);
        SalesOrder order = auditedSalesOrder(11L);
        order.setCustomerId(1001L);
        SalesOrderRequest request = new SalesOrderRequest(
                order.getOrderNo(),
                order.getPurchaseInboundNo(),
                order.getPurchaseOrderNo(),
                order.getCustomerCode(),
                1002L,
                order.getCustomerName(),
                order.getProjectId(),
                order.getProjectName(),
                null,
                null,
                order.getDeliveryDate().plusDays(1),
                order.getSalesName(),
                StatusConstants.AUDITED,
                "改价备注",
                List.of(itemRequestFrom(order.getItems().get(0), new BigDecimal("3200.00")))
        );

        assertThat(service.matchesAuditedPricingUpdate(order, request)).isFalse();
    }

    @Test
    void shouldRejectDifferentItemCount() {
        var service = new SalesOrderAuditedPricingService(null);
        SalesOrder order = auditedSalesOrder(11L);
        SalesOrderRequest request = new SalesOrderRequest(
                order.getOrderNo(),
                order.getPurchaseInboundNo(),
                order.getPurchaseOrderNo(),
                order.getCustomerCode(),
                order.getCustomerName(),
                order.getProjectId(),
                order.getProjectName(),
                order.getDeliveryDate(),
                order.getSalesName(),
                StatusConstants.AUDITED,
                order.getRemark(),
                List.of()
        );

        assertThat(service.matchesAuditedPricingUpdate(order, request)).isFalse();
    }

    @Test
    void shouldAllowNullRequestItemsWhenEntityHasNoItems() {
        var service = new SalesOrderAuditedPricingService(null);
        SalesOrder order = auditedSalesOrder(11L);
        order.setItems(new ArrayList<>());
        SalesOrderRequest request = requestWithItems(order, null);

        assertThat(service.matchesAuditedPricingUpdate(order, request)).isTrue();
    }

    @Test
    void shouldDetectAuditedPricingUpdateOnlyWhenBothStatusesAreAudited() {
        var service = new SalesOrderAuditedPricingService(null);
        SalesOrder order = auditedSalesOrder(11L);
        SalesOrderRequest auditedRequest = requestFrom(order, StatusConstants.AUDITED, new BigDecimal("3200.00"));

        assertThat(service.isAuditedPricingUpdate(order, auditedRequest)).isTrue();

        order.setStatus(StatusConstants.UNAUDITED);
        assertThat(service.isAuditedPricingUpdate(order, auditedRequest)).isFalse();

        order.setStatus(StatusConstants.AUDITED);
        SalesOrderRequest draftRequest = requestFrom(order, StatusConstants.DRAFT, new BigDecimal("3200.00"));
        assertThat(service.isAuditedPricingUpdate(order, draftRequest)).isFalse();

        SalesOrderRequest changedHeaderRequest = requestWithHeader(order, "SO-CHANGED",
                order.getPurchaseInboundNo(), order.getPurchaseOrderNo(), order.getCustomerCode(),
                order.getCustomerName(), order.getProjectId(), order.getProjectName(), order.getSalesName());
        assertThat(service.isAuditedPricingUpdate(order, changedHeaderRequest)).isFalse();
    }

    @Test
    void shouldDetectDeliveryVerificationPricingUpdate() {
        var service = new SalesOrderAuditedPricingService(null);
        SalesOrder order = auditedSalesOrder(11L);
        order.setStatus(StatusConstants.DELIVERY_VERIFICATION);
        SalesOrderRequest request = requestFrom(
                order,
                StatusConstants.DELIVERY_VERIFICATION,
                new BigDecimal("3200.00")
        );

        assertThat(service.isAuditedPricingUpdate(order, request)).isTrue();
        assertThat(service.isAuditedPricingUpdate(
                order,
                requestFrom(order, StatusConstants.SALES_COMPLETED, new BigDecimal("3200.00"))
        )).isFalse();
    }

    @Test
    void shouldKeepDeliveryVerificationStatusAfterPricingUpdate() {
        var service = new SalesOrderAuditedPricingService(null);
        SalesOrder order = auditedSalesOrder(11L);
        order.setStatus(StatusConstants.DELIVERY_VERIFICATION);
        SalesOrderRequest request = requestFrom(
                order,
                StatusConstants.DELIVERY_VERIFICATION,
                new BigDecimal("3200.00")
        );

        service.applyAuditedPricingUpdate(order, request);

        assertThat(order.getStatus()).isEqualTo(StatusConstants.DELIVERY_VERIFICATION);
    }

    @Test
    void shouldRejectChangedItemFields() {
        var service = new SalesOrderAuditedPricingService(null);
        SalesOrder order = auditedSalesOrder(11L);
        SalesOrderItem item = order.getItems().get(0);

        List<ItemMutation> mutations = List.of(
                new ItemMutation("id", i -> itemRequestWith(i, 99L, i.getMaterialCode(), i.getBrand(),
                        i.getCategory(), i.getMaterial(), i.getSpec(), i.getLength(), i.getUnit(),
                        i.getSourceInboundItemId(), i.getSourcePurchaseOrderItemId(), i.getWarehouseName(),
                        i.getBatchNo(), i.getQuantity(), i.getQuantityUnit(), i.getPieceWeightTon(),
                        i.getPiecesPerBundle(), i.getWeightTon())),
                new ItemMutation("materialCode", i -> itemRequestWith(i, i.getId(), "M2", i.getBrand(),
                        i.getCategory(), i.getMaterial(), i.getSpec(), i.getLength(), i.getUnit(),
                        i.getSourceInboundItemId(), i.getSourcePurchaseOrderItemId(), i.getWarehouseName(),
                        i.getBatchNo(), i.getQuantity(), i.getQuantityUnit(), i.getPieceWeightTon(),
                        i.getPiecesPerBundle(), i.getWeightTon())),
                new ItemMutation("brand", i -> itemRequestWith(i, i.getId(), i.getMaterialCode(), "鞍钢",
                        i.getCategory(), i.getMaterial(), i.getSpec(), i.getLength(), i.getUnit(),
                        i.getSourceInboundItemId(), i.getSourcePurchaseOrderItemId(), i.getWarehouseName(),
                        i.getBatchNo(), i.getQuantity(), i.getQuantityUnit(), i.getPieceWeightTon(),
                        i.getPiecesPerBundle(), i.getWeightTon())),
                new ItemMutation("category", i -> itemRequestWith(i, i.getId(), i.getMaterialCode(), i.getBrand(),
                        "螺纹钢", i.getMaterial(), i.getSpec(), i.getLength(), i.getUnit(),
                        i.getSourceInboundItemId(), i.getSourcePurchaseOrderItemId(), i.getWarehouseName(),
                        i.getBatchNo(), i.getQuantity(), i.getQuantityUnit(), i.getPieceWeightTon(),
                        i.getPiecesPerBundle(), i.getWeightTon())),
                new ItemMutation("material", i -> itemRequestWith(i, i.getId(), i.getMaterialCode(), i.getBrand(),
                        i.getCategory(), "HRB500", i.getSpec(), i.getLength(), i.getUnit(),
                        i.getSourceInboundItemId(), i.getSourcePurchaseOrderItemId(), i.getWarehouseName(),
                        i.getBatchNo(), i.getQuantity(), i.getQuantityUnit(), i.getPieceWeightTon(),
                        i.getPiecesPerBundle(), i.getWeightTon())),
                new ItemMutation("spec", i -> itemRequestWith(i, i.getId(), i.getMaterialCode(), i.getBrand(),
                        i.getCategory(), i.getMaterial(), "10", i.getLength(), i.getUnit(),
                        i.getSourceInboundItemId(), i.getSourcePurchaseOrderItemId(), i.getWarehouseName(),
                        i.getBatchNo(), i.getQuantity(), i.getQuantityUnit(), i.getPieceWeightTon(),
                        i.getPiecesPerBundle(), i.getWeightTon())),
                new ItemMutation("length", i -> itemRequestWith(i, i.getId(), i.getMaterialCode(), i.getBrand(),
                        i.getCategory(), i.getMaterial(), i.getSpec(), "9m", i.getUnit(),
                        i.getSourceInboundItemId(), i.getSourcePurchaseOrderItemId(), i.getWarehouseName(),
                        i.getBatchNo(), i.getQuantity(), i.getQuantityUnit(), i.getPieceWeightTon(),
                        i.getPiecesPerBundle(), i.getWeightTon())),
                new ItemMutation("unit", i -> itemRequestWith(i, i.getId(), i.getMaterialCode(), i.getBrand(),
                        i.getCategory(), i.getMaterial(), i.getSpec(), i.getLength(), "kg",
                        i.getSourceInboundItemId(), i.getSourcePurchaseOrderItemId(), i.getWarehouseName(),
                        i.getBatchNo(), i.getQuantity(), i.getQuantityUnit(), i.getPieceWeightTon(),
                        i.getPiecesPerBundle(), i.getWeightTon())),
                new ItemMutation("sourceInboundItemId", i -> itemRequestWith(i, i.getId(), i.getMaterialCode(), i.getBrand(),
                        i.getCategory(), i.getMaterial(), i.getSpec(), i.getLength(), i.getUnit(),
                        22L, i.getSourcePurchaseOrderItemId(), i.getWarehouseName(),
                        i.getBatchNo(), i.getQuantity(), i.getQuantityUnit(), i.getPieceWeightTon(),
                        i.getPiecesPerBundle(), i.getWeightTon())),
                new ItemMutation("sourcePurchaseOrderItemId", i -> itemRequestWith(i, i.getId(), i.getMaterialCode(), i.getBrand(),
                        i.getCategory(), i.getMaterial(), i.getSpec(), i.getLength(), i.getUnit(),
                        i.getSourceInboundItemId(), 32L, i.getWarehouseName(),
                        i.getBatchNo(), i.getQuantity(), i.getQuantityUnit(), i.getPieceWeightTon(),
                        i.getPiecesPerBundle(), i.getWeightTon())),
                new ItemMutation("warehouseName", i -> itemRequestWith(i, i.getId(), i.getMaterialCode(), i.getBrand(),
                        i.getCategory(), i.getMaterial(), i.getSpec(), i.getLength(), i.getUnit(),
                        i.getSourceInboundItemId(), i.getSourcePurchaseOrderItemId(), "二号库",
                        i.getBatchNo(), i.getQuantity(), i.getQuantityUnit(), i.getPieceWeightTon(),
                        i.getPiecesPerBundle(), i.getWeightTon())),
                new ItemMutation("batchNo", i -> itemRequestWith(i, i.getId(), i.getMaterialCode(), i.getBrand(),
                        i.getCategory(), i.getMaterial(), i.getSpec(), i.getLength(), i.getUnit(),
                        i.getSourceInboundItemId(), i.getSourcePurchaseOrderItemId(), i.getWarehouseName(),
                        "B2", i.getQuantity(), i.getQuantityUnit(), i.getPieceWeightTon(),
                        i.getPiecesPerBundle(), i.getWeightTon())),
                new ItemMutation("quantity", i -> itemRequestWith(i, i.getId(), i.getMaterialCode(), i.getBrand(),
                        i.getCategory(), i.getMaterial(), i.getSpec(), i.getLength(), i.getUnit(),
                        i.getSourceInboundItemId(), i.getSourcePurchaseOrderItemId(), i.getWarehouseName(),
                        i.getBatchNo(), 3, i.getQuantityUnit(), i.getPieceWeightTon(),
                        i.getPiecesPerBundle(), i.getWeightTon())),
                new ItemMutation("quantityUnit", i -> itemRequestWith(i, i.getId(), i.getMaterialCode(), i.getBrand(),
                        i.getCategory(), i.getMaterial(), i.getSpec(), i.getLength(), i.getUnit(),
                        i.getSourceInboundItemId(), i.getSourcePurchaseOrderItemId(), i.getWarehouseName(),
                        i.getBatchNo(), i.getQuantity(), "吨", i.getPieceWeightTon(),
                        i.getPiecesPerBundle(), i.getWeightTon())),
                new ItemMutation("pieceWeightTon", i -> itemRequestWith(i, i.getId(), i.getMaterialCode(), i.getBrand(),
                        i.getCategory(), i.getMaterial(), i.getSpec(), i.getLength(), i.getUnit(),
                        i.getSourceInboundItemId(), i.getSourcePurchaseOrderItemId(), i.getWarehouseName(),
                        i.getBatchNo(), i.getQuantity(), i.getQuantityUnit(), new BigDecimal("2.249"),
                        i.getPiecesPerBundle(), i.getWeightTon())),
                new ItemMutation("piecesPerBundle", i -> itemRequestWith(i, i.getId(), i.getMaterialCode(), i.getBrand(),
                        i.getCategory(), i.getMaterial(), i.getSpec(), i.getLength(), i.getUnit(),
                        i.getSourceInboundItemId(), i.getSourcePurchaseOrderItemId(), i.getWarehouseName(),
                        i.getBatchNo(), i.getQuantity(), i.getQuantityUnit(), i.getPieceWeightTon(),
                        2, i.getWeightTon())),
                new ItemMutation("weightTon", i -> itemRequestWith(i, i.getId(), i.getMaterialCode(), i.getBrand(),
                        i.getCategory(), i.getMaterial(), i.getSpec(), i.getLength(), i.getUnit(),
                        i.getSourceInboundItemId(), i.getSourcePurchaseOrderItemId(), i.getWarehouseName(),
                        i.getBatchNo(), i.getQuantity(), i.getQuantityUnit(), i.getPieceWeightTon(),
                        i.getPiecesPerBundle(), new BigDecimal("4.497")))
        );

        for (ItemMutation mutation : mutations) {
            SalesOrderRequest request = requestWithItems(order,
                    List.of(mutation.requestFactory().create(item)));

            assertThat(service.matchesAuditedPricingUpdate(order, request))
                    .as(mutation.name())
                    .isFalse();
        }
    }

    @Test
    void shouldRejectChangedMaterialOrWarehouseStableIdentity() {
        var service = new SalesOrderAuditedPricingService(null);
        SalesOrder order = auditedSalesOrder(11L);
        SalesOrderItem item = order.getItems().get(0);

        SalesOrderRequest changedMaterial = requestWithItems(
                order,
                List.of(itemRequestWithStableIds(item, 502L, item.getWarehouseId()))
        );
        SalesOrderRequest changedWarehouse = requestWithItems(
                order,
                List.of(itemRequestWithStableIds(item, item.getMaterialId(), 602L))
        );

        assertThat(service.matchesAuditedPricingUpdate(order, changedMaterial)).isFalse();
        assertThat(service.matchesAuditedPricingUpdate(order, changedWarehouse)).isFalse();
    }

    @Test
    void shouldAllowNormalizedEquivalentItemFields() {
        var service = new SalesOrderAuditedPricingService(null);
        SalesOrder order = auditedSalesOrder(11L);
        SalesOrderItem item = order.getItems().get(0);
        SalesOrderItemRequest requestItem = itemRequestWith(item, item.getId(), " M1 ", " 宝钢 ",
                " 盘螺 ", " HRB400 ", " 8 ", " 12m ", " 吨 ", item.getSourceInboundItemId(),
                item.getSourcePurchaseOrderItemId(), " 一号库 ", " B1 ", item.getQuantity(), item.getQuantityUnit(),
                new BigDecimal("2.2480"), item.getPiecesPerBundle(), new BigDecimal("4.4960"));

        assertThat(service.matchesAuditedPricingUpdate(order, requestWithItems(order, List.of(requestItem)))).isTrue();
    }

    @Test
    void shouldApplyPricingAndSkipSyncWhenNoSyncServiceExists() {
        var service = new SalesOrderAuditedPricingService(null);
        SalesOrder order = auditedSalesOrder(11L);
        SalesOrderRequest request = requestFrom(order, StatusConstants.AUDITED, new BigDecimal("3200.00"));

        service.applyAuditedPricingUpdate(order, request);

        assertThat(order.getItems().get(0).getUnitPrice()).isEqualByComparingTo("3200.00");
        assertThat(order.getItems().get(0).getAmount()).isEqualByComparingTo("14387.20");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("14387.20");
    }

    @Test
    void shouldSkipSyncWhenAllItemIdsAreMissing() {
        AtomicReference<Collection<Long>> syncedIds = new AtomicReference<>();
        SalesOrderOutboundPricingSyncService syncService = (ids, unitPriceByItemId) -> syncedIds.set(ids);
        var service = new SalesOrderAuditedPricingService(syncService);
        SalesOrder order = auditedSalesOrder(null);
        SalesOrderRequest request = requestFrom(order, StatusConstants.AUDITED, new BigDecimal("3200.00"));

        service.applyAuditedPricingUpdate(order, request);

        assertThat(syncedIds.get()).isNull();
    }

    @Test
    void shouldSyncPricingForAuditedOrderItems() {
        AtomicReference<Collection<Long>> syncedIds = new AtomicReference<>();
        AtomicReference<Map<Long, BigDecimal>> syncedPrices = new AtomicReference<>();
        SalesOrderOutboundPricingSyncService syncService = (ids, unitPriceByItemId) -> {
            syncedIds.set(ids);
            syncedPrices.set(unitPriceByItemId);
        };
        var service = new SalesOrderAuditedPricingService(syncService);
        SalesOrder order = auditedSalesOrder(11L);
        SalesOrderRequest request = requestFrom(order, StatusConstants.AUDITED, new BigDecimal("3200.00"));

        service.applyAuditedPricingUpdate(order, request);

        assertThat(syncedIds.get()).containsExactly(11L);
        assertThat(syncedPrices.get()).containsEntry(11L, new BigDecimal("3200.00"));
    }

    @Test
    void shouldSyncOnlyItemsWithIdsWhenOrderContainsMixedItemIds() {
        AtomicReference<Collection<Long>> syncedIds = new AtomicReference<>();
        AtomicReference<Map<Long, BigDecimal>> syncedPrices = new AtomicReference<>();
        SalesOrderOutboundPricingSyncService syncService = (ids, unitPriceByItemId) -> {
            syncedIds.set(ids);
            syncedPrices.set(unitPriceByItemId);
        };
        var service = new SalesOrderAuditedPricingService(syncService);
        SalesOrder order = auditedSalesOrder(11L);
        SalesOrderItem first = order.getItems().get(0);
        SalesOrderItem second = copyItem(first, null, 2);
        order.setItems(new ArrayList<>(List.of(first, second)));
        SalesOrderRequest request = requestWithItems(order, List.of(
                itemRequestFrom(first, new BigDecimal("3200.00")),
                itemRequestFrom(second, new BigDecimal("3300.00"))
        ));

        service.applyAuditedPricingUpdate(order, request);

        assertThat(syncedIds.get()).containsExactly(11L);
        assertThat(syncedPrices.get()).containsOnlyKeys(11L);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("29224.00");
    }

    private SalesOrder auditedSalesOrder(Long itemId) {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-AUDITED-001");
        order.setSalesMode(SalesModes.NORMAL);
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
        item.setId(itemId);
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

    private SalesOrderRequest requestFrom(SalesOrder order, String status, BigDecimal unitPrice) {
        return new SalesOrderRequest(
                order.getOrderNo(),
                order.getPurchaseInboundNo(),
                order.getPurchaseOrderNo(),
                order.getCustomerCode(),
                order.getCustomerName(),
                order.getProjectId(),
                order.getProjectName(),
                order.getDeliveryDate().plusDays(1),
                order.getSalesName(),
                status,
                "改价备注",
                List.of(itemRequestFrom(order.getItems().get(0), unitPrice))
        );
    }

    private SalesOrderRequest requestWithHeader(
            SalesOrder order,
            String orderNo,
            String purchaseInboundNo,
            String purchaseOrderNo,
            String customerCode,
            String customerName,
            Long projectId,
            String projectName,
            String salesName
    ) {
        return new SalesOrderRequest(
                orderNo,
                purchaseInboundNo,
                purchaseOrderNo,
                customerCode,
                customerName,
                projectId,
                projectName,
                order.getDeliveryDate().plusDays(1),
                salesName,
                StatusConstants.AUDITED,
                "改价备注",
                List.of(itemRequestFrom(order.getItems().get(0), new BigDecimal("3200.00")))
        );
    }

    private SalesOrderRequest requestWithItems(SalesOrder order, List<SalesOrderItemRequest> items) {
        return new SalesOrderRequest(
                order.getOrderNo(),
                order.getPurchaseInboundNo(),
                order.getPurchaseOrderNo(),
                order.getCustomerCode(),
                order.getCustomerName(),
                order.getProjectId(),
                order.getProjectName(),
                order.getDeliveryDate().plusDays(1),
                order.getSalesName(),
                StatusConstants.AUDITED,
                "改价备注",
                items
        );
    }

    private SalesOrderItemRequest itemRequestFrom(SalesOrderItem item, BigDecimal unitPrice) {
        return new SalesOrderItemRequest(
                item.getId(),
                item.getMaterialId(),
                item.getMaterialCode(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getUnit(),
                item.getSourceInboundItemId(),
                item.getSourcePurchaseOrderItemId(),
                item.getWarehouseId(),
                item.getWarehouseName(),
                item.getBatchNo(),
                item.getQuantity(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getWeightTon(),
                unitPrice,
                item.getAmount()
        );
    }

    private SalesOrderItemRequest itemRequestWith(
            SalesOrderItem item,
            Long id,
            String materialCode,
            String brand,
            String category,
            String material,
            String spec,
            String length,
            String unit,
            Long sourceInboundItemId,
            Long sourcePurchaseOrderItemId,
            String warehouseName,
            String batchNo,
            Integer quantity,
            String quantityUnit,
            BigDecimal pieceWeightTon,
            Integer piecesPerBundle,
            BigDecimal weightTon
    ) {
        return new SalesOrderItemRequest(
                id,
                item.getMaterialId(),
                materialCode,
                brand,
                category,
                material,
                spec,
                length,
                unit,
                sourceInboundItemId,
                sourcePurchaseOrderItemId,
                item.getWarehouseId(),
                warehouseName,
                batchNo,
                quantity,
                quantityUnit,
                pieceWeightTon,
                piecesPerBundle,
                weightTon,
                new BigDecimal("3200.00"),
                item.getAmount()
        );
    }

    private SalesOrderItemRequest itemRequestWithStableIds(
            SalesOrderItem item,
            Long materialId,
            Long warehouseId) {
        return new SalesOrderItemRequest(
                item.getId(),
                materialId,
                item.getMaterialCode(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getUnit(),
                item.getSourceInboundItemId(),
                item.getSourcePurchaseOrderItemId(),
                warehouseId,
                item.getWarehouseName(),
                item.getBatchNo(),
                item.getQuantity(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getWeightTon(),
                new BigDecimal("3200.00"),
                item.getAmount()
        );
    }

    private SalesOrderItem copyItem(SalesOrderItem source, Long id, int lineNo) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrder(source.getSalesOrder());
        item.setLineNo(lineNo);
        item.setMaterialId(source.getMaterialId());
        item.setMaterialCode(source.getMaterialCode());
        item.setBrand(source.getBrand());
        item.setCategory(source.getCategory());
        item.setMaterial(source.getMaterial());
        item.setSpec(source.getSpec());
        item.setLength(source.getLength());
        item.setUnit(source.getUnit());
        item.setSourceInboundItemId(source.getSourceInboundItemId());
        item.setSourcePurchaseOrderItemId(source.getSourcePurchaseOrderItemId());
        item.setWarehouseId(source.getWarehouseId());
        item.setWarehouseName(source.getWarehouseName());
        item.setBatchNo(source.getBatchNo());
        item.setQuantity(source.getQuantity());
        item.setQuantityUnit(source.getQuantityUnit());
        item.setPieceWeightTon(source.getPieceWeightTon());
        item.setPiecesPerBundle(source.getPiecesPerBundle());
        item.setWeightTon(source.getWeightTon());
        item.setUnitPrice(source.getUnitPrice());
        item.setAmount(source.getAmount());
        return item;
    }

    private record HeaderMutation(String name, HeaderRequestFactory requestFactory) {}

    private interface HeaderRequestFactory {
        SalesOrderRequest create(SalesOrder order);
    }

    private record ItemMutation(String name, ItemRequestFactory requestFactory) {}

    private interface ItemRequestFactory {
        SalesOrderItemRequest create(SalesOrderItem item);
    }
}
