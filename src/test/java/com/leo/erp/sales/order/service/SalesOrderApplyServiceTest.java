package com.leo.erp.sales.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemPieceWeightAppService;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
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

class SalesOrderApplyServiceTest {

    @Test
    void shouldApplyOrderWithSourceInboundAndDerivedHeaderValues() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                purchaseItemQueryAppService,
                pieceWeightAppService,
                salesOrderItemRepository,
                workflowTransitionGuard
        );

        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setStatus(StatusConstants.DRAFT);
        SalesOrderItem oldItem = new SalesOrderItem();
        oldItem.setId(99L);
        order.getItems().add(oldItem);

        SalesOrderRequest request = request(List.of(itemRequest(101L, null, 4)));

        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");
        when(purchaseItemQueryAppService.findSourceInboundItemsByIds(List.of(101L)))
                .thenReturn(List.of(sourceInboundRecord(101L)));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(
                eq(List.of(101L)),
                eq(1L)
        )).thenReturn(List.of());

        AtomicLong nextId = new AtomicLong(11L);
        service.apply(order, request, nextId::getAndIncrement);

        verify(workflowTransitionGuard).assertAuditPermissionForProtectedValue(
                "sales-order",
                StatusConstants.DRAFT,
                StatusConstants.AUDITED,
                StatusConstants.AUDITED,
                StatusConstants.SALES_COMPLETED
        );
        verify(pieceWeightAppService).releaseSalesOrderItems(List.of(99L));
        assertThat(order.getPurchaseInboundNo()).isEqualTo("PI-001");
        assertThat(order.getPurchaseOrderNo()).isEqualTo("PO-001");
        assertThat(order.getTotalWeight()).isEqualByComparingTo("0.400");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("1600.00");
        assertThat(order.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(11L);
            assertThat(item.getLineNo()).isEqualTo(1);
            assertThat(item.getSourceInboundItemId()).isEqualTo(101L);
            assertThat(item.getAmount()).isEqualByComparingTo("1600.00");
        });
    }

    @Test
    void shouldApplyOrderWithoutSourceDocuments() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                pieceWeightAppService,
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class)
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = request(List.of(itemRequest(null, null, 2)));

        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", material()));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.normalizeWarehouseName("一号库", 1, true)).thenReturn("一号库");

        service.apply(order, request, new AtomicLong(21L)::getAndIncrement);

        assertThat(order.getPurchaseInboundNo()).isEqualTo("REQ-PI");
        assertThat(order.getPurchaseOrderNo()).isEqualTo("REQ-PO");
        assertThat(order.getTotalWeight()).isEqualByComparingTo("0.200");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("800.00");
        assertThat(order.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSourceInboundItemId()).isNull();
            assertThat(item.getSourcePurchaseOrderItemId()).isNull();
            assertThat(item.getWeightTon()).isEqualByComparingTo("0.200");
        });
        verify(pieceWeightAppService).releaseSalesOrderItems(List.of());
    }

    private SalesOrderApplyService service(TradeItemMaterialSupport materialSupport,
                                           WarehouseSelectionSupport warehouseSelectionSupport,
                                           PurchaseItemQueryAppService purchaseItemQueryAppService,
                                           PurchaseItemPieceWeightAppService pieceWeightAppService,
                                           SalesOrderItemRepository salesOrderItemRepository,
                                           WorkflowTransitionGuard workflowTransitionGuard) {
        SalesOrderPurchaseAllocationService purchaseAllocationService =
                new SalesOrderPurchaseAllocationService(purchaseItemQueryAppService, pieceWeightAppService);
        return new SalesOrderApplyService(
                materialSupport,
                new SalesOrderSourceAllocationService(purchaseItemQueryAppService, salesOrderItemRepository),
                new SalesOrderWeightResolver(pieceWeightAppService),
                purchaseAllocationService,
                new SalesOrderItemMapper(materialSupport, warehouseSelectionSupport),
                workflowTransitionGuard
        );
    }

    private SalesOrderRequest request(List<SalesOrderItemRequest> items) {
        return new SalesOrderRequest(
                "SO-001",
                "REQ-PI",
                "REQ-PO",
                "C001",
                "客户A",
                1001L,
                "项目A",
                LocalDate.of(2026, 4, 26),
                "张三",
                StatusConstants.AUDITED,
                "备注",
                items
        );
    }

    private SalesOrderItemRequest itemRequest(Long sourceInboundItemId,
                                              Long sourcePurchaseOrderItemId,
                                              Integer quantity) {
        BigDecimal pieceWeightTon = new BigDecimal("0.100");
        BigDecimal weightTon = pieceWeightTon.multiply(BigDecimal.valueOf(quantity));
        return new SalesOrderItemRequest(
                "M1",
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "12m",
                "吨",
                sourceInboundItemId,
                sourcePurchaseOrderItemId,
                "一号库",
                "B1",
                quantity,
                "支",
                pieceWeightTon,
                1,
                weightTon,
                new BigDecimal("4000.00"),
                weightTon.multiply(new BigDecimal("4000.00"))
        );
    }

    private PurchaseItemQueryAppService.SourceInboundItemRecord sourceInboundRecord(Long id) {
        return new PurchaseItemQueryAppService.SourceInboundItemRecord(
                id,
                "PI-001",
                StatusConstants.AUDITED,
                "PO-001",
                10,
                null,
                "宝钢",
                "HRB400",
                "18",
                "M1",
                "螺纹钢",
                "吨",
                "一号库",
                "B1"
        );
    }

    private TradeMaterialSnapshot material() {
        return new TradeMaterialSnapshot("M1", Boolean.TRUE);
    }
}
