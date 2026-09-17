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
 * 仅剩已删除入库单残留明细时给出明确的残留数据提示，不再暴露数据库外键错误；
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

        verify(purchaseInboundItemRepository, never()).findAllBySourcePurchaseOrderItemIds(List.of(1L, 2L));
        verifyNoInteractions(purchaseOrderReferenceGuard);
    }

    @Test
    void assertSourceLineMutationAllowed_whenOnlyDeletedInboundResidueExists_shouldThrowResidueMessage() {
        PurchaseOrder order = order(1L, 2L);
        when(purchaseInboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(1L, 2L)))
                .thenReturn(List.of());
        when(purchaseOrderReferenceGuard.hasActivePurchaseOrderItemReferences(List.of(1L, 2L)))
                .thenReturn(false);
        when(purchaseInboundItemRepository.findAllBySourcePurchaseOrderItemIds(List.of(1L, 2L)))
                .thenReturn(List.of(softDeletedInboundItem()));

        assertThatThrownBy(() -> guard.assertSourceLineMutationAllowed(order, List.of(), "修改"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仍被已删除的采购入库单引用");
    }

    @Test
    void assertSourceLineMutationAllowed_whenNoInboundReferences_shouldPass() {
        PurchaseOrder order = order(1L, 2L);
        when(purchaseInboundItemRepository.findAllActiveBySourcePurchaseOrderItemIds(List.of(1L, 2L)))
                .thenReturn(List.of());
        when(purchaseOrderReferenceGuard.hasActivePurchaseOrderItemReferences(List.of(1L, 2L)))
                .thenReturn(false);
        when(purchaseInboundItemRepository.findAllBySourcePurchaseOrderItemIds(List.of(1L, 2L)))
                .thenReturn(List.of());

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

        verify(purchaseInboundItemRepository, never()).findAllBySourcePurchaseOrderItemIds(List.of(1L, 2L));
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

    private PurchaseInboundItem softDeletedInboundItem() {
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(101L);
        item.setSourcePurchaseOrderItemId(1L);
        return item;
    }
}
