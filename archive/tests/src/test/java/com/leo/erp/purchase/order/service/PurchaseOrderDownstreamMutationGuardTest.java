package com.leo.erp.purchase.order.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemRequest;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PurchaseOrderDownstreamMutationGuardTest {

    @Test
    void shouldLockSourceLinesAndRejectMutationWhenActiveInboundExists() {
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PurchaseOrderDownstreamMutationGuard guard = new PurchaseOrderDownstreamMutationGuard(
                inboundItemRepository,
                salesOrderItemRepository,
                lockService
        );
        PurchaseOrder order = orderWithItem(21L);
        when(inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(21L)))
                .thenReturn(List.of(mock(PurchaseInboundItem.class)));

        assertThatThrownBy(() -> guard.assertMutable(order, "反审核"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购入库")
                .hasMessageContaining("不能反审核");

        InOrder flow = inOrder(lockService, inboundItemRepository);
        flow.verify(lockService).lockTradeItemSources(List.of(21L), List.of(), List.of());
        flow.verify(inboundItemRepository).findAllActiveBySourcePurchaseOrderItemIds(List.of(21L));
        verifyNoInteractions(salesOrderItemRepository);
    }

    @Test
    void shouldRejectMutationWhenActiveSalesOrderExists() {
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PurchaseOrderDownstreamMutationGuard guard = new PurchaseOrderDownstreamMutationGuard(
                inboundItemRepository,
                salesOrderItemRepository,
                lockService
        );
        PurchaseOrder order = orderWithItem(21L);
        when(inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(21L)))
                .thenReturn(List.of());
        when(salesOrderItemRepository.findActiveBySourcePurchaseOrderItemIds(List.of(21L)))
                .thenReturn(List.of(mock(SalesOrderItem.class)));

        assertThatThrownBy(() -> guard.assertMutable(order, "删除"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售订单")
                .hasMessageContaining("不能删除");

        verify(lockService).lockTradeItemSources(List.of(21L), List.of(), List.of());
        verify(salesOrderItemRepository).findActiveBySourcePurchaseOrderItemIds(List.of(21L));
    }

    @Test
    void shouldCheckDownstreamWhenSourceLineQuantityChanges() {
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PurchaseOrderDownstreamMutationGuard guard = new PurchaseOrderDownstreamMutationGuard(
                inboundItemRepository,
                salesOrderItemRepository,
                lockService
        );
        PurchaseOrder order = orderWithItem(21L);
        when(inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(21L)))
                .thenReturn(List.of(mock(PurchaseInboundItem.class)));

        assertThatThrownBy(() -> guard.assertSourceLineMutationAllowed(
                order,
                List.of(itemRequest(21L, 2)),
                "修改"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购入库")
                .hasMessageContaining("不能修改");
    }

    @Test
    void shouldAllowHeaderOnlyUpdateWithoutCheckingDownstream() {
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PurchaseOrderDownstreamMutationGuard guard = new PurchaseOrderDownstreamMutationGuard(
                inboundItemRepository,
                salesOrderItemRepository,
                lockService
        );
        PurchaseOrder order = orderWithItem(21L);

        guard.assertSourceLineMutationAllowed(order, List.of(itemRequest(21L, 1)), "修改");

        verify(lockService, never()).lockTradeItemSources(List.of(21L), List.of(), List.of());
        verifyNoInteractions(inboundItemRepository, salesOrderItemRepository);
    }

    @Test
    void shouldRejectCompletionReopenWhenInboundSourceWasSold() {
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PurchaseOrderDownstreamMutationGuard guard = new PurchaseOrderDownstreamMutationGuard(
                inboundItemRepository,
                salesOrderItemRepository,
                lockService
        );
        PurchaseOrder order = orderWithItem(21L);
        PurchaseInboundItem inboundItem = new PurchaseInboundItem();
        inboundItem.setId(31L);
        SalesOrderItemRepository.SourceInboundAllocationSummary summary =
                mock(SalesOrderItemRepository.SourceInboundAllocationSummary.class);
        when(inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(21L)))
                .thenReturn(List.of(inboundItem));
        when(salesOrderItemRepository.findActiveBySourcePurchaseOrderItemIds(List.of(21L)))
                .thenReturn(List.of());
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(List.of(31L), null))
                .thenReturn(List.of(summary));

        assertThatThrownBy(() -> guard.assertCompletionReopenAllowed(order))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售订单")
                .hasMessageContaining("不能撤销完成采购");
    }

    @Test
    void shouldRejectCompletionReopenWhenInboundWasIncludedInSupplierStatement() {
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        SupplierStatementRepository statementRepository = mock(SupplierStatementRepository.class);
        PurchaseOrderDownstreamMutationGuard guard = new PurchaseOrderDownstreamMutationGuard(
                inboundItemRepository,
                salesOrderItemRepository,
                lockService
        );
        guard.setSupplierStatementRepository(statementRepository);
        PurchaseOrder order = orderWithItem(21L);
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(41L);
        PurchaseInboundItem inboundItem = new PurchaseInboundItem();
        inboundItem.setId(31L);
        inboundItem.setPurchaseInbound(inbound);
        when(inboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(21L)))
                .thenReturn(List.of(inboundItem));
        when(salesOrderItemRepository.findActiveBySourcePurchaseOrderItemIds(List.of(21L)))
                .thenReturn(List.of());
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(List.of(31L), null))
                .thenReturn(List.of());
        when(statementRepository.findMatchingOccupiedSourceInboundIdsExcludingCurrentStatement(
                List.of(41L),
                null
        )).thenReturn(List.of(41L));

        assertThatThrownBy(() -> guard.assertCompletionReopenAllowed(order))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商对账单")
                .hasMessageContaining("不能撤销完成采购");
        verify(lockService).lockDocumentSources(List.of(41L), List.of(), List.of(), List.of());
    }

    private PurchaseOrder orderWithItem(Long itemId) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(itemId);
        item.setPurchaseOrder(order);
        item.setLineNo(1);
        item.setMaterialId(101L);
        item.setMaterialCode("M1");
        item.setBrand("宝钢");
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setLength("12m");
        item.setUnit("吨");
        item.setWarehouseId(201L);
        item.setWarehouseName("一号库");
        item.setBatchNo("B1");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("0.10000000"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("0.10000000"));
        item.setUnitPrice(new BigDecimal("4000.00"));
        item.setAmount(new BigDecimal("400.00"));
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }

    private PurchaseOrderItemRequest itemRequest(Long itemId, Integer quantity) {
        return new PurchaseOrderItemRequest(
                itemId,
                101L,
                "M1",
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "12m",
                "吨",
                201L,
                "一号库",
                "B1",
                quantity,
                "件",
                new BigDecimal("0.10000000"),
                1,
                new BigDecimal("0.10000000"),
                new BigDecimal("4000.00"),
                new BigDecimal("400.00")
        );
    }
}
