package com.leo.erp.purchase.order.service;

import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeItemMaterialSupportTestDoubles;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseOrderApplyServiceTest {

    @Test
    void shouldApplyItemsAndCalculateTotals() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        PurchaseInboundItemQueryService inboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderApplyService service = new PurchaseOrderApplyService(
                materialSupport,
                warehouseSelectionSupport,
                inboundItemQueryService
        );

        PurchaseOrder order = new PurchaseOrder();
        PurchaseOrderItem existingItem = new PurchaseOrderItem();
        existingItem.setId(11L);
        order.getItems().add(existingItem);

        PurchaseOrderRequest request = request(List.of(
                itemRequest(11L, "M1", "一号库", "B1", 10, new BigDecimal("0.100"), new BigDecimal("4000.00")),
                itemRequest(null, "M2", "二号库", "B2", 3, new BigDecimal("0.200"), new BigDecimal("5000.00"))
        ));

        when(materialSupport.loadMaterialMap(List.of("M1", "M2"))).thenReturn(Map.of(
                "M1", new TradeMaterialSnapshot("M1", true),
                "M2", new TradeMaterialSnapshot("M2", true)
        ));
        TradeItemMaterialSupportTestDoubles.stubMaterialCodeNormalization(materialSupport);
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(warehouseSelectionSupport.normalizeWarehouseName("二号库", 2, true)).thenReturn("二号库");
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(false))).thenReturn("B1");
        when(materialSupport.normalizeBatchNo(any(), eq("B2"), eq(2), eq(false))).thenReturn("B2");
        when(inboundItemQueryService.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(List.of(11L, 101L)))
                .thenReturn(Map.of());

        service.applyItems(order, request, new AtomicLong(101L)::getAndIncrement);

        assertThat(order.getTotalWeight()).isEqualByComparingTo("1.600");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("7000.00");
        assertThat(order.getItems()).extracting(PurchaseOrderItem::getId)
                .containsExactly(11L, 101L);
        assertThat(order.getItems()).extracting(PurchaseOrderItem::getLineNo)
                .containsExactly(1, 2);
        assertThat(order.getItems().get(0)).satisfies(item -> {
            assertThat(item.getPurchaseOrder()).isSameAs(order);
            assertThat(item.getWarehouseName()).isEqualTo("一号库");
            assertThat(item.getBatchNo()).isEqualTo("B1");
            assertThat(item.getQuantityUnit()).isEqualTo("件");
            assertThat(item.getWeightTon()).isEqualByComparingTo("1.000");
            assertThat(item.getAmount()).isEqualByComparingTo("4000.00");
        });
        assertThat(order.getItems().get(1)).satisfies(item -> {
            assertThat(item.getPurchaseOrder()).isSameAs(order);
            assertThat(item.getWarehouseName()).isEqualTo("二号库");
            assertThat(item.getBatchNo()).isEqualTo("B2");
            assertThat(item.getWeightTon()).isEqualByComparingTo("0.600");
            assertThat(item.getAmount()).isEqualByComparingTo("3000.00");
        });
    }

    @Test
    void shouldRespectInboundWeightAdjustmentRules() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        PurchaseInboundItemQueryService inboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderApplyService service = new PurchaseOrderApplyService(
                materialSupport,
                warehouseSelectionSupport,
                inboundItemQueryService
        );

        PurchaseOrder order = new PurchaseOrder();
        PurchaseOrderItem firstItem = new PurchaseOrderItem();
        firstItem.setId(11L);
        PurchaseOrderItem secondItem = new PurchaseOrderItem();
        secondItem.setId(12L);
        order.getItems().add(firstItem);
        order.getItems().add(secondItem);

        PurchaseOrderRequest request = request(List.of(
                itemRequest(11L, "M1", "一号库", "B1", 10,
                        new BigDecimal("0.100"), new BigDecimal("4000.00"), new BigDecimal("1.000")),
                itemRequest(12L, "M2", "二号库", "B2", 3,
                        new BigDecimal("0.200"), new BigDecimal("5000.00"), new BigDecimal("0.500"))
        ));

        when(materialSupport.loadMaterialMap(List.of("M1", "M2"))).thenReturn(Map.of(
                "M1", new TradeMaterialSnapshot("M1", true),
                "M2", new TradeMaterialSnapshot("M2", true)
        ));
        TradeItemMaterialSupportTestDoubles.stubMaterialCodeNormalization(materialSupport);
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(warehouseSelectionSupport.normalizeWarehouseName("二号库", 2, true)).thenReturn("二号库");
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(false))).thenReturn("B1");
        when(materialSupport.normalizeBatchNo(any(), eq("B2"), eq(2), eq(false))).thenReturn("B2");
        when(inboundItemQueryService.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(List.of(11L, 12L)))
                .thenReturn(Map.of(
                        11L, new BigDecimal("0.050"),
                        12L, new BigDecimal("0.050")
                ));

        service.applyItems(order, request, new AtomicLong(101L)::getAndIncrement);

        assertThat(order.getItems()).extracting(PurchaseOrderItem::getWeightTon)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("1.000"), new BigDecimal("0.650"));
        assertThat(order.getTotalWeight()).isEqualByComparingTo("1.650");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("7250.00");
    }

    @Test
    void shouldCalculateWeightWhenRequestWeightIsMissing() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        PurchaseInboundItemQueryService inboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderApplyService service = new PurchaseOrderApplyService(
                materialSupport,
                warehouseSelectionSupport,
                inboundItemQueryService
        );
        PurchaseOrder order = new PurchaseOrder();
        PurchaseOrderRequest request = request(List.of(
                new PurchaseOrderItemRequest(
                        null,
                        "M1",
                        "宝钢",
                        "螺纹钢",
                        "HRB400",
                        "18",
                        "12m",
                        "吨",
                        "一号库",
                        "B1",
                        4,
                        null,
                        new BigDecimal("0.250"),
                        1,
                        null,
                        new BigDecimal("4000.00"),
                        null
                )
        ));

        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of(
                "M1", new TradeMaterialSnapshot("M1", true)
        ));
        TradeItemMaterialSupportTestDoubles.stubMaterialCodeNormalization(materialSupport);
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(false))).thenReturn("B1");
        when(inboundItemQueryService.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(List.of(101L)))
                .thenReturn(Map.of());

        service.applyItems(order, request, new AtomicLong(101L)::getAndIncrement);

        assertThat(order.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getWeightTon()).isEqualByComparingTo("1.000");
            assertThat(item.getAmount()).isEqualByComparingTo("4000.00");
        });
    }

    private PurchaseOrderRequest request(List<PurchaseOrderItemRequest> items) {
        return new PurchaseOrderRequest(
                "PO-001",
                "供应商A",
                LocalDateTime.of(2026, 4, 26, 0, 0),
                "李四",
                1L,
                "草稿",
                "备注",
                items
        );
    }

    private PurchaseOrderItemRequest itemRequest(Long id,
                                                 String materialCode,
                                                 String warehouseName,
                                                 String batchNo,
                                                 Integer quantity,
                                                 BigDecimal pieceWeightTon,
                                                 BigDecimal unitPrice) {
        return itemRequest(id, materialCode, warehouseName, batchNo, quantity, pieceWeightTon, unitPrice, null);
    }

    private PurchaseOrderItemRequest itemRequest(Long id,
                                                 String materialCode,
                                                 String warehouseName,
                                                 String batchNo,
                                                 Integer quantity,
                                                 BigDecimal pieceWeightTon,
                                                 BigDecimal unitPrice,
                                                 BigDecimal weightTon) {
        BigDecimal calculatedWeightTon = pieceWeightTon.multiply(BigDecimal.valueOf(quantity.longValue()));
        BigDecimal requestWeightTon = weightTon == null ? calculatedWeightTon : weightTon;
        return new PurchaseOrderItemRequest(
                id,
                materialCode,
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "12m",
                "吨",
                warehouseName,
                batchNo,
                quantity,
                null,
                pieceWeightTon,
                1,
                requestWeightTon,
                unitPrice,
                requestWeightTon.multiply(unitPrice)
        );
    }
}
