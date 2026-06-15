package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.master.material.repository.MaterialCategoryRepository;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.service.PurchaseOrderItemPieceWeightService;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseInboundApplyServiceTest {

    @Test
    void shouldApplyItemsAndDeriveHeaderValuesFromSourceLines() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseOrderItemQueryService purchaseOrderItemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        MaterialCategoryRepository materialCategoryRepository = mock(MaterialCategoryRepository.class);
        PurchaseInboundWeightWriteBackService weightWriteBackService = new PurchaseInboundWeightWriteBackService(
                inboundItemRepository,
                purchaseOrderRepository,
                mock(PurchaseOrderItemPieceWeightService.class)
        );
        PurchaseInboundApplyService service = new PurchaseInboundApplyService(
                materialSupport,
                new PurchaseInboundSourceValidator(
                        purchaseOrderItemQueryService,
                        new PurchaseInboundAllocationService(inboundItemRepository)
                ),
                new PurchaseInboundWeightSettlementService(materialCategoryRepository),
                weightWriteBackService,
                new InboundItemMapper(materialSupport, warehouseSelectionSupport)
        );

        PurchaseOrder order1 = purchaseOrder(301L, "PO-001");
        PurchaseOrder order2 = purchaseOrder(302L, "PO-002");
        PurchaseOrderItem sourceItem1 = sourcePurchaseOrderItem(order1, 201L, "M1", "B1", 10,
                new BigDecimal("0.100"), new BigDecimal("4000.00"));
        PurchaseOrderItem sourceItem2 = sourcePurchaseOrderItem(order2, 202L, "M2", "B2", 6,
                new BigDecimal("0.200"), new BigDecimal("5000.00"));
        order1.getItems().add(sourceItem1);
        order2.getItems().add(sourceItem2);

        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(1L);
        PurchaseInboundRequest request = new PurchaseInboundRequest(
                "PI-001",
                null,
                "供应商A",
                null,
                LocalDate.of(2026, 4, 26),
                null,
                "草稿",
                null,
                List.of(
                        itemRequest(201L, "M1", "一号库", "理算", "B1", 4,
                                new BigDecimal("0.100"), new BigDecimal("4000.00")),
                        itemRequest(202L, "M2", "二号库", "月结", "B2", 3,
                                new BigDecimal("0.200"), new BigDecimal("5000.00"))
                )
        );

        when(materialSupport.loadMaterialMap(List.of("M1", "M2"))).thenReturn(Map.of(
                "M1", new TradeMaterialSnapshot("M1", true),
                "M2", new TradeMaterialSnapshot("M2", true)
        ));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(materialSupport.normalizeBatchNo(any(), eq("B2"), eq(2), eq(true))).thenReturn("B2");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(warehouseSelectionSupport.normalizeWarehouseName("二号库", 2, true)).thenReturn("二号库");
        when(materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of("螺纹钢"))).thenReturn(List.of());
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(201L, 202L)))
                .thenReturn(List.of(sourceItem1, sourceItem2));
        when(inboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L, 202L)),
                eq(1L)
        )).thenReturn(List.of());
        when(inboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L, 202L)),
                eq(1L)
        )).thenReturn(List.of());
        when(purchaseOrderRepository.findByIdInAndDeletedFlagFalse(any()))
                .thenReturn(List.of(order1, order2));

        AtomicLong nextId = new AtomicLong(101L);
        service.applyItems(inbound, request, nextId::getAndIncrement);

        assertThat(inbound.getPurchaseOrderNo()).isEqualTo("PO-001, PO-002");
        assertThat(inbound.getWarehouseName()).isEqualTo("一号库");
        assertThat(inbound.getSettlementMode()).isEqualTo("混合");
        assertThat(inbound.getTotalWeight()).isEqualByComparingTo("1.000");
        assertThat(inbound.getTotalAmount()).isEqualByComparingTo("4600.00");
        assertThat(inbound.getItems()).hasSize(2);
        assertThat(inbound.getItems()).extracting(PurchaseInboundItem::getId)
                .containsExactly(101L, 102L);
        assertThat(inbound.getItems()).extracting(PurchaseInboundItem::getLineNo)
                .containsExactly(1, 2);
        assertThat(inbound.getItems().get(0).getAmount()).isEqualByComparingTo("1600.00");
        assertThat(inbound.getItems().get(1).getAmount()).isEqualByComparingTo("3000.00");
        verify(purchaseOrderRepository).saveAll(any());
    }

    private PurchaseOrder purchaseOrder(Long id, String orderNo) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setStatus("已审核");
        order.setSupplierName("供应商A");
        return order;
    }

    private PurchaseOrderItem sourcePurchaseOrderItem(PurchaseOrder order,
                                                       Long id,
                                                       String materialCode,
                                                       String batchNo,
                                                       Integer quantity,
                                                       BigDecimal pieceWeightTon,
                                                       BigDecimal unitPrice) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setPurchaseOrder(order);
        item.setMaterialCode(materialCode);
        item.setBrand("宝钢");
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setLength("12m");
        item.setUnit("吨");
        item.setWarehouseName(id.equals(201L) ? "一号库" : "二号库");
        item.setBatchNo(batchNo);
        item.setQuantity(quantity);
        item.setQuantityUnit("支");
        item.setPieceWeightTon(pieceWeightTon);
        item.setPiecesPerBundle(1);
        item.setWeightTon(pieceWeightTon.multiply(BigDecimal.valueOf(quantity)));
        item.setUnitPrice(unitPrice);
        item.setAmount(item.getWeightTon().multiply(unitPrice));
        return item;
    }

    private PurchaseInboundItemRequest itemRequest(Long sourcePurchaseOrderItemId,
                                                   String materialCode,
                                                   String warehouseName,
                                                   String settlementMode,
                                                   String batchNo,
                                                   Integer quantity,
                                                   BigDecimal pieceWeightTon,
                                                   BigDecimal unitPrice) {
        BigDecimal weightTon = pieceWeightTon.multiply(BigDecimal.valueOf(quantity));
        return new PurchaseInboundItemRequest(
                null,
                materialCode,
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "12m",
                "吨",
                sourcePurchaseOrderItemId,
                warehouseName,
                settlementMode,
                batchNo,
                quantity,
                "支",
                pieceWeightTon,
                1,
                weightTon,
                null,
                null,
                null,
                unitPrice,
                weightTon.multiply(unitPrice)
        );
    }
}
