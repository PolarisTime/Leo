package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.purchase.api.PurchaseOrderReferenceGuard;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PurchaseInboundSourceStatusGuard 反审核/删除来源锁测试：
 * 采购订单明细与采购入库明细必须一次性按全局 rank（入库 20/21 → 采购订单 30/31）获取。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseInboundSourceStatusGuardTest {

    @Mock
    private PurchaseOrderItemQueryService purchaseOrderItemQueryService;

    @Mock
    private PurchaseInboundItemRepository purchaseInboundItemRepository;

    @Mock
    private PurchaseOrderReferenceGuard purchaseOrderReferenceGuard;

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    @Mock
    private PurchaseInboundAllocationService allocationService;

    @InjectMocks
    private PurchaseInboundSourceStatusGuard guard;

    private PurchaseInbound inboundWithSourceItem(Long sourceItemId) {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(5L);
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(21L);
        item.setLineNo(1);
        item.setSourcePurchaseOrderItemId(sourceItemId);
        inbound.setItems(List.of(item));
        return inbound;
    }

    private PurchaseOrderItem orderItem(Long orderId, Long... itemIds) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(orderId);
        List<PurchaseOrderItem> items = java.util.Arrays.stream(itemIds)
                .map(id -> {
                    PurchaseOrderItem item = new PurchaseOrderItem();
                    item.setId(id);
                    item.setPurchaseOrder(order);
                    return item;
                })
                .toList();
        order.setItems(new java.util.ArrayList<>(items));
        return items.getFirst();
    }

    @Test
    void lockReverseReferenceSources_shouldLockInboundAndPurchaseOrderSourcesInOneCall() {
        PurchaseInbound inbound = inboundWithSourceItem(11L);
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(11L)))
                .thenReturn(List.of(orderItem(100L, 11L, 12L)));
        when(purchaseInboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(11L, 12L)))
                .thenReturn(List.of(inboundItem(31L), inboundItem(32L)));

        guard.lockReverseReferenceSources(inbound);

        verify(sourceAllocationLockService).lockTradeItemSources(
                List.of(11L, 12L), List.of(31L, 32L), List.of());
    }

    @Test
    void lockReverseReferenceSources_shouldLockNothingWhenInboundHasNoSourceItems() {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(5L);
        inbound.setItems(List.of());

        guard.lockReverseReferenceSources(inbound);

        verify(sourceAllocationLockService).lockTradeItemSources(List.of(), List.of(), List.of());
    }

    private PurchaseInboundItem inboundItem(Long id) {
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(id);
        return item;
    }
}
