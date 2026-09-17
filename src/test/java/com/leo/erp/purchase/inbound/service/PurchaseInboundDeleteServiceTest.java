package com.leo.erp.purchase.inbound.service;

import com.leo.erp.purchase.api.PurchaseOrderReferenceGuard;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 删除采购入库单后的明细引用释放边界测试：
 * 未被下游物理引用的明细行应被物理删除以释放采购订单明细的 RESTRICT 外键，
 * 仍被销售订单明细引用的行保留，且重量回写必须先于引用释放执行。
 */
@ExtendWith(MockitoExtension.class)
class PurchaseInboundDeleteServiceTest {

    @Mock
    private PurchaseInboundWeightWriteBackService weightWriteBackService;

    @Mock
    private PurchaseInboundItemRepository purchaseInboundItemRepository;

    @Mock
    private PurchaseOrderReferenceGuard purchaseOrderReferenceGuard;

    @InjectMocks
    private PurchaseInboundDeleteService service;

    @Test
    void afterDelete_shouldRemoveUnreferencedItemsAndKeepReferencedOnes() {
        PurchaseInbound inbound = inbound(11L, 12L, 13L);
        when(purchaseOrderReferenceGuard.findReferencedInboundItemIds(List.of(11L, 12L, 13L)))
                .thenReturn(Set.of(12L));

        service.afterDelete(inbound);

        assertThat(inbound.getItems())
                .extracting(PurchaseInboundItem::getId)
                .containsExactly(12L);
        InOrder order = inOrder(weightWriteBackService, purchaseInboundItemRepository);
        order.verify(weightWriteBackService).synchronizeAfterSave(inbound);
        order.verify(purchaseInboundItemRepository).flush();
    }

    @Test
    void afterDelete_whenAllItemsReferenced_shouldKeepAllItemsAndSkipFlush() {
        PurchaseInbound inbound = inbound(21L, 22L);
        when(purchaseOrderReferenceGuard.findReferencedInboundItemIds(List.of(21L, 22L)))
                .thenReturn(Set.of(21L, 22L));

        service.afterDelete(inbound);

        assertThat(inbound.getItems()).extracting(PurchaseInboundItem::getId)
                .containsExactly(21L, 22L);
        verify(weightWriteBackService).synchronizeAfterSave(inbound);
        verify(purchaseInboundItemRepository, never()).flush();
    }

    @Test
    void afterDelete_withoutPersistedItemIds_shouldSkipReferenceQuery() {
        PurchaseInbound inbound = inbound((Long) null);

        service.afterDelete(inbound);

        verify(weightWriteBackService).synchronizeAfterSave(inbound);
        verifyNoInteractions(purchaseOrderReferenceGuard);
        verify(purchaseInboundItemRepository, never()).flush();
    }

    @Test
    void afterDelete_shouldIgnoreUnpersistedItemsWhenReleasing() {
        PurchaseInbound inbound = inbound(31L, null);
        when(purchaseOrderReferenceGuard.findReferencedInboundItemIds(List.of(31L)))
                .thenReturn(Set.of());

        service.afterDelete(inbound);

        assertThat(inbound.getItems()).extracting(PurchaseInboundItem::getId)
                .containsExactly((Long) null);
        verify(purchaseInboundItemRepository).flush();
    }

    private PurchaseInbound inbound(Long... itemIds) {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(5L);
        inbound.setInboundNo("PI001");
        for (Long itemId : itemIds) {
            PurchaseInboundItem item = new PurchaseInboundItem();
            item.setId(itemId);
            item.setSourcePurchaseOrderItemId(itemId == null ? null : itemId + 100);
            inbound.getItems().add(item);
        }
        return inbound;
    }
}
