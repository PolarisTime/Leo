package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        return inboundWithSourceItem(sourceItemId, 5);
    }

    private PurchaseInbound inboundWithSourceItem(Long sourceItemId, Integer quantity) {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(5L);
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(21L);
        item.setLineNo(1);
        item.setSourcePurchaseOrderItemId(sourceItemId);
        item.setQuantity(quantity);
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

    // ---------- 行级部分审核放行 ----------

    /** 订单 10 件，本单只入 5 件（另 5 件由其他已审核单占用），允许审核。 */
    @Test
    void audit_shouldAllowPartialAllocationOnAuditedDraft() {
        PurchaseInbound inbound = inboundWithSourceItem(11L);
        PurchaseOrderItem sourceItem = orderItem(100L, 11L);
        sourceItem.setQuantity(10);
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(11L)))
                .thenReturn(List.of(sourceItem));
        when(allocationService.loadAllocatedQuantityMap(List.of(11L), 5L))
                .thenReturn(Map.of(11L, 5));

        assertThatCode(() -> guard.assertStatusTransitionAllowed(
                inbound, StatusConstants.DRAFT, StatusConstants.AUDITED)).doesNotThrowAnyException();
    }

    /** 其他入库单 + 本次入库数量超过订单量时必须拒绝。 */
    @Test
    void audit_shouldRejectWhenCumulativeWithCurrentLineExceedsOrdered() {
        PurchaseInbound inbound = inboundWithSourceItem(11L, 5);
        PurchaseOrderItem sourceItem = orderItem(100L, 11L);
        sourceItem.setQuantity(10);
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(11L)))
                .thenReturn(List.of(sourceItem));
        when(allocationService.loadAllocatedQuantityMap(List.of(11L), 5L))
                .thenReturn(Map.of(11L, 6));

        assertThatThrownBy(() -> guard.assertStatusTransitionAllowed(
                inbound, StatusConstants.DRAFT, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("累计入库数量超过订单数量");
    }

    /** 5+5 分两次审核：第二单在累计恰好等于订单量时仍允许通过。 */
    @Test
    void audit_shouldAllowSecondPartialBatchWhenCumulativeReachesOrdered() {
        PurchaseInbound inbound = inboundWithSourceItem(11L, 5);
        PurchaseOrderItem sourceItem = orderItem(100L, 11L);
        sourceItem.setQuantity(10);
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(11L)))
                .thenReturn(List.of(sourceItem));
        when(allocationService.loadAllocatedQuantityMap(List.of(11L), 5L))
                .thenReturn(Map.of(11L, 5));

        assertThatCode(() -> guard.assertStatusTransitionAllowed(
                inbound, StatusConstants.DRAFT, StatusConstants.AUDITED)).doesNotThrowAnyException();
    }

    /** 累计入库不得超过订单量。 */
    @Test
    void audit_shouldRejectWhenAllocatedExceedsOrdered() {
        PurchaseInbound inbound = inboundWithSourceItem(11L);
        PurchaseOrderItem sourceItem = orderItem(100L, 11L);
        sourceItem.setQuantity(10);
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(11L)))
                .thenReturn(List.of(sourceItem));
        when(allocationService.loadAllocatedQuantityMap(List.of(11L), 5L))
                .thenReturn(Map.of(11L, 11));

        assertThatThrownBy(() -> guard.assertStatusTransitionAllowed(
                inbound, StatusConstants.DRAFT, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("累计入库数量超过订单数量");
    }

    /** 来源行数量为 0 的订单不能审核入库。 */
    @Test
    void audit_shouldRejectWhenOrderedQuantityNotPositive() {
        PurchaseInbound inbound = inboundWithSourceItem(11L);
        PurchaseOrderItem sourceItem = orderItem(100L, 11L);
        sourceItem.setQuantity(0);
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(11L)))
                .thenReturn(List.of(sourceItem));
        when(allocationService.loadAllocatedQuantityMap(List.of(11L), 5L))
                .thenReturn(Map.of());

        assertThatThrownBy(() -> guard.assertStatusTransitionAllowed(
                inbound, StatusConstants.DRAFT, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数量必须大于0");
    }

    /** 多张采购订单来源行的合并入库允许审核。 */
    @Test
    void audit_shouldAllowMultipleSourcePurchaseOrders() {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(5L);
        PurchaseInboundItem first = new PurchaseInboundItem();
        first.setId(21L);
        first.setLineNo(1);
        first.setSourcePurchaseOrderItemId(11L);
        PurchaseInboundItem second = new PurchaseInboundItem();
        second.setId(22L);
        second.setLineNo(2);
        second.setSourcePurchaseOrderItemId(12L);
        inbound.setItems(List.of(first, second));
        PurchaseOrderItem firstSource = orderItem(100L, 11L);
        firstSource.setQuantity(5);
        PurchaseOrderItem secondSource = orderItem(200L, 12L);
        secondSource.setQuantity(5);
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(11L, 12L)))
                .thenReturn(List.of(firstSource, secondSource));
        when(allocationService.loadAllocatedQuantityMap(List.of(11L, 12L), 5L))
                .thenReturn(Map.of(11L, 5, 12L, 3));

        assertThatCode(() -> guard.assertStatusTransitionAllowed(
                inbound, StatusConstants.DRAFT, StatusConstants.AUDITED)).doesNotThrowAnyException();
    }

    /** 来源行失效仍必须拒绝。 */
    @Test
    void audit_shouldRejectWhenSourceItemExpired() {
        PurchaseInbound inbound = inboundWithSourceItem(11L);
        when(purchaseOrderItemQueryService.findActiveByIdIn(List.of(11L))).thenReturn(List.of());

        assertThatThrownBy(() -> guard.assertStatusTransitionAllowed(
                inbound, StatusConstants.DRAFT, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已失效");
    }

    private PurchaseInboundItem inboundItem(Long id) {
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(id);
        return item;
    }
}
