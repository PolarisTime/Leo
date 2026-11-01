package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.sales.api.SalesReturnReferenceQuery;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.service.SalesOrderDeliveryVerificationGuard;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 销售出库反审核/删除前的下游守卫：除「来源销售订单已完成/客户对账单/收款」外，
 * 还必须拦截已被销售退货单引用的出库明细（退货审核会生成红字对账单并记退货入库）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalesOutboundDownstreamMutationGuardTest {

    @Mock
    private SalesOrderDeliveryVerificationGuard deliveryVerificationGuard;

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private SalesReturnReferenceQuery salesReturnReferenceQuery;

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    @InjectMocks
    private SalesOutboundDownstreamMutationGuard guard;

    private SalesOutbound outboundWithItem(Long outboundItemId, Long sourceSalesOrderItemId) {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(5L);
        outbound.setOutboundNo("OB001");
        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(outboundItemId);
        item.setSourceSalesOrderItemId(sourceSalesOrderItemId);
        outbound.setItems(List.of(item));
        return outbound;
    }

    @Test
    void assertReverseAuditAllowed_shouldRejectWhenSalesReturnReferencesOutboundItems() {
        SalesOutbound outbound = outboundWithItem(11L, 21L);
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(List.of(21L))).thenReturn(List.of());
        when(salesReturnReferenceQuery.hasActiveReturnReferences(List.of(11L))).thenReturn(true);

        assertThatThrownBy(() -> guard.assertReverseAuditAllowed(outbound))
                .isInstanceOf(BusinessException.class)
                .hasMessage("销售出库已存在销售退货单，不能反审核，请先处理相关退货单");

        // 先按全局 rank 锁出库单/明细（50/51），再查退货引用
        InOrder inOrder = inOrder(sourceAllocationLockService, salesReturnReferenceQuery);
        inOrder.verify(sourceAllocationLockService).lockSalesOutboundItemSources(List.of(11L));
        inOrder.verify(salesReturnReferenceQuery).hasActiveReturnReferences(List.of(11L));
    }

    @Test
    void assertReverseAuditAllowed_shouldPassWhenNoSalesReturnReferences() {
        SalesOutbound outbound = outboundWithItem(11L, 21L);
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(List.of(21L))).thenReturn(List.of());
        when(salesReturnReferenceQuery.hasActiveReturnReferences(List.of(11L))).thenReturn(false);

        assertThatCode(() -> guard.assertReverseAuditAllowed(outbound)).doesNotThrowAnyException();
        verify(deliveryVerificationGuard).assertMutableByItemIds(List.of(21L), "反审核");
    }

    @Test
    void assertDeleteAllowed_shouldRejectWhenSalesReturnReferencesOutboundItems() {
        SalesOutbound outbound = outboundWithItem(11L, 21L);
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(List.of(21L))).thenReturn(List.of());
        when(salesReturnReferenceQuery.hasActiveReturnReferences(List.of(11L))).thenReturn(true);

        assertThatThrownBy(() -> guard.assertDeleteAllowed(outbound))
                .isInstanceOf(BusinessException.class)
                .hasMessage("销售出库已存在销售退货单，不能删除，请先处理相关退货单");
    }

    @Test
    void assertDeleteAllowed_shouldSkipReturnCheckWhenOutboundHasNoItems() {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(5L);
        outbound.setItems(List.of());

        assertThatCode(() -> guard.assertDeleteAllowed(outbound)).doesNotThrowAnyException();
        verifyNoInteractions(salesReturnReferenceQuery, sourceAllocationLockService);
    }

    @Test
    void assertDeleteAllowed_shouldRejectWhenSourceOrderAlreadyCompleted() {
        SalesOutbound outbound = outboundWithItem(11L, 21L);
        com.leo.erp.sales.order.domain.entity.SalesOrder order =
                new com.leo.erp.sales.order.domain.entity.SalesOrder();
        order.setId(1L);
        order.setStatus(com.leo.erp.common.support.StatusConstants.SALES_COMPLETED);
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(anyList())).thenReturn(List.of(order));

        assertThatThrownBy(() -> guard.assertDeleteAllowed(outbound))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单已完成销售");
        verifyNoInteractions(salesReturnReferenceQuery);
        verify(sourceAllocationLockService, org.mockito.Mockito.never())
                .lockSalesOutboundItemSources(any());
    }
}
