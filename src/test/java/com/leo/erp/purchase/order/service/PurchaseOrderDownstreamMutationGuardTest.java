package com.leo.erp.purchase.order.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.purchase.api.PurchaseOrderReferenceGuard;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 采购订单来源行变更守卫边界测试：
 * 活跃入库单引用给出"请先删除相关采购入库单"提示；
 * 仅剩已删除入库单残留明细时**解除残留引用**（只针对被移除的订单行）并放行，
 * 不再用"请先清理残留数据"把已删除单据的影响转嫁给用户；
 * 状态流转/删除路径不受残留明细影响。
 */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderDownstreamMutationGuardTest {

    @Mock
    private PurchaseInboundItemRepository purchaseInboundItemRepository;

    @Mock
    private PurchaseOrderReferenceGuard purchaseOrderReferenceGuard;

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    @InjectMocks
    private PurchaseOrderDownstreamMutationGuard guard;

    @Test
    void assertSourceLineMutationAllowed_whenActiveInboundExists_shouldThrowExistingMessage() {
        PurchaseOrder order = order(1L, 2L);
        when(purchaseInboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(1L, 2L)))
                .thenReturn(List.of(new PurchaseInboundItem()));

        assertThatThrownBy(() -> guard.assertSourceLineMutationAllowed(order, List.of(), "修改"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在采购入库单");

        verify(purchaseInboundItemRepository, never())
                .detachSourceReferencesOfDeletedInbounds(org.mockito.ArgumentMatchers.anyList());
        verifyNoInteractions(purchaseOrderReferenceGuard);
    }

    @Test
    void assertSourceLineMutationAllowed_whenOnlyDeletedInboundResidueExists_shouldDetachRemovedLines() {
        PurchaseOrder order = order(1L, 2L);
        when(purchaseInboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(1L, 2L)))
                .thenReturn(List.of());
        when(purchaseOrderReferenceGuard.hasActivePurchaseOrderItemReferences(List.of(1L, 2L)))
                .thenReturn(false);

        // 两行都被移除：删除订单明细前先解除已删入库单的残留引用，否则 RESTRICT 外键会拒绝删除
        assertThatCode(() -> guard.assertSourceLineMutationAllowed(order, List.of(), "修改"))
                .doesNotThrowAnyException();

        verify(purchaseInboundItemRepository).detachSourceReferencesOfDeletedInbounds(List.of(1L, 2L));
    }

    @Test
    void assertSourceLineMutationAllowed_whenDeletedResidueExistsButOnlyContentChanged_shouldNotDetach() {
        PurchaseOrder order = order(1L, 2L);
        when(purchaseInboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(1L, 2L)))
                .thenReturn(List.of());
        when(purchaseOrderReferenceGuard.hasActivePurchaseOrderItemReferences(List.of(1L, 2L)))
                .thenReturn(false);

        // 行仍然保留（只改数量）：不触发外键删除，不应解除来源链路
        PurchaseOrderItemRequest changed = new PurchaseOrderItemRequest(
                1L, 100L, "M1", "B1", "C1", "MT1", "S1", null, "吨", null, "W1", "LOT1",
                9, "件", new java.math.BigDecimal("1.0"), 10, null, new java.math.BigDecimal("100.00"), null);
        assertThatCode(() -> guard.assertSourceLineMutationAllowed(
                order, List.of(changed, itemRequest(2L)), "修改"))
                .doesNotThrowAnyException();

        verify(purchaseInboundItemRepository, never())
                .detachSourceReferencesOfDeletedInbounds(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void assertSourceLineMutationAllowed_whenPartiallyRemovingLines_shouldDetachOnlyRemovedIds() {
        PurchaseOrder order = order(1L, 2L, 3L);
        when(purchaseInboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(1L, 2L, 3L)))
                .thenReturn(List.of());
        when(purchaseOrderReferenceGuard.hasActivePurchaseOrderItemReferences(List.of(1L, 2L, 3L)))
                .thenReturn(false);

        assertThatCode(() -> guard.assertSourceLineMutationAllowed(
                order, List.of(itemRequest(1L), itemRequest(3L)), "修改"))
                .doesNotThrowAnyException();

        verify(purchaseInboundItemRepository).detachSourceReferencesOfDeletedInbounds(List.of(2L));
    }

    @Test
    void assertSourceLineMutationAllowed_whenNoInboundReferences_shouldPass() {
        PurchaseOrder order = order(1L, 2L);
        when(purchaseInboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(1L, 2L)))
                .thenReturn(List.of());
        when(purchaseOrderReferenceGuard.hasActivePurchaseOrderItemReferences(List.of(1L, 2L)))
                .thenReturn(false);

        assertThatCode(() -> guard.assertSourceLineMutationAllowed(order, List.of(), "修改"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertSourceLineMutationAllowed_whenSourceLinesUnchanged_shouldSkipAllGuards() {
        PurchaseOrder order = order(1L, 2L);
        order.getItems().forEach(item -> {
            item.setLineNo(Math.toIntExact(item.getId()));
            item.setMaterialId(100L);
            item.setMaterialCode("M1");
            item.setBrand("B1");
            item.setCategory("C1");
            item.setMaterial("MT1");
            item.setSpec("S1");
            item.setUnit("吨");
            item.setWarehouseName("W1");
            item.setBatchNo("LOT1");
            item.setQuantity(5);
            item.setQuantityUnit("件");
            item.setPieceWeightTon(new java.math.BigDecimal("1.0"));
            item.setPiecesPerBundle(10);
            item.setUnitPrice(new java.math.BigDecimal("100.00"));
        });

        guard.assertSourceLineMutationAllowed(order, List.of(itemRequest(1L), itemRequest(2L)), "修改");

        verifyNoInteractions(
                purchaseInboundItemRepository, purchaseOrderReferenceGuard, sourceAllocationLockService);
    }

    private PurchaseOrderItemRequest itemRequest(Long id) {
        return new PurchaseOrderItemRequest(
                id, 100L, "M1", "B1", "C1", "MT1", "S1", null, "吨", null, "W1", "LOT1",
                5, "件", new java.math.BigDecimal("1.0"), 10, null, new java.math.BigDecimal("100.00"), null);
    }

    @Test
    void assertMutable_whenOnlyDeletedResidueExists_shouldStayAllowedForStatusAndDeletePaths() {
        PurchaseOrder order = order(1L, 2L);
        when(purchaseInboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(1L, 2L)))
                .thenReturn(List.of());
        when(purchaseOrderReferenceGuard.hasActivePurchaseOrderItemReferences(List.of(1L, 2L)))
                .thenReturn(false);

        assertThatCode(() -> guard.assertMutable(order, "删除"))
                .doesNotThrowAnyException();

        verify(purchaseInboundItemRepository, never())
                .detachSourceReferencesOfDeletedInbounds(org.mockito.ArgumentMatchers.anyList());
    }

    private PurchaseOrder order(Long... itemIds) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(9L);
        for (Long itemId : itemIds) {
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setId(itemId);
            item.setLineNo(Math.toIntExact(itemId));
            order.getItems().add(item);
        }
        return order;
    }
}
