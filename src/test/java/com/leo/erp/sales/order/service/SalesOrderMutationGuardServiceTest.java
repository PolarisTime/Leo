package com.leo.erp.sales.order.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SalesOrderMutationGuardService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderMutationGuardServiceTest {

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    @Mock
    private SalesOrderDeliveryVerificationGuard deliveryVerificationGuard;

    @Mock
    private SalesOrderDownstreamMutationGuard downstreamMutationGuard;

    @Mock
    private SalesOrderProtectedUpdatePolicy protectedUpdatePolicy;

    @Mock
    private SalesOrderApplyService salesOrderApplyService;

    @InjectMocks
    private SalesOrderMutationGuardService service;

    private SalesOrderRequest request() {
        return new SalesOrderRequest(
                "SO001", null, null, "CUST001", 10L, "客户A", 20L, "项目A", null, null,
                LocalDate.of(2026, 8, 1), "销售员A", StatusConstants.DRAFT, null, List.of(), List.of(), false);
    }

    private SalesOrder entity(String status) {
        SalesOrder entity = new SalesOrder();
        entity.setId(5L);
        entity.setOrderNo("SO001");
        entity.setStatus(status);
        return entity;
    }

    private SalesOrderItem item(Long id, Long sourcePurchaseOrderItemId, Long sourceInboundItemId) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setLineNo(1);
        item.setQuantity(2);
        item.setSourcePurchaseOrderItemId(sourcePurchaseOrderItemId);
        item.setSourceInboundItemId(sourceInboundItemId);
        return item;
    }

    // ---------- lockPurchaseSources ----------

    @Test
    void lockPurchaseSources_shouldLockNothingForNullEntityAndRequest() {
        service.lockPurchaseSources(null, null);

        verify(sourceAllocationLockService).lockTradeItemSources(List.of(), List.of(), List.of());
    }

    @Test
    void lockPurchaseSources_shouldMergeDedupAndSortIds() {
        SalesOrder entity = entity(StatusConstants.DRAFT);
        entity.setItems(List.of(
                item(1L, 30L, 20L),
                item(2L, 10L, 20L)
        ));
        SalesOrderRequest request = new SalesOrderRequest(
                "SO001", null, null, "CUST001", 10L, "客户A", 20L, "项目A", null, null,
                LocalDate.of(2026, 8, 1), "销售员A", StatusConstants.DRAFT, null,
                List.of(new com.leo.erp.sales.order.web.dto.SalesOrderItemRequest(
                        3L, null, "M001", "B", "C", "M", "S", null, "件",
                        50L, 40L, null, "仓", null, 2, null, null, null, null, null, null)),
                List.of(), false);

        service.lockPurchaseSources(entity, request);

        verify(sourceAllocationLockService).lockTradeItemSources(List.of(10L, 30L, 40L), List.of(20L, 50L), List.of());
    }

    // ---------- assertDeletable ----------

    @Test
    void assertDeletable_shouldLockSourcesAndCheckDownstreamGuard() {
        SalesOrder entity = entity(StatusConstants.AUDITED);

        service.assertDeletable(entity);

        verify(sourceAllocationLockService).lockTradeItemSources(List.of(), List.of(), List.of());
        verify(downstreamMutationGuard).assertMutable(entity, "删除");
    }

    @Test
    void assertDeletable_shouldPropagateDownstreamGuardRejection() {
        SalesOrder entity = entity(StatusConstants.AUDITED);
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "已被引用"))
                .when(downstreamMutationGuard).assertMutable(any(), anyString());

        assertThatThrownBy(() -> service.assertDeletable(entity))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被引用");
    }

    // ---------- assertStatusTransitionAllowed ----------

    @Test
    void assertStatusTransitionAllowed_shouldRejectCompleteViaStatus() {
        assertThatThrownBy(() -> service.assertStatusTransitionAllowed(
                entity(StatusConstants.DRAFT), StatusConstants.DRAFT, StatusConstants.SALES_COMPLETED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("专用完成操作");
    }

    @Test
    void assertStatusTransitionAllowed_shouldGuardReverseFromCompleted() {
        SalesOrder entity = entity(StatusConstants.SALES_COMPLETED);
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "已出库"))
                .when(deliveryVerificationGuard).assertMutable(entity, "反审核");

        assertThatThrownBy(() -> service.assertStatusTransitionAllowed(
                entity, StatusConstants.SALES_COMPLETED, StatusConstants.DELIVERY_VERIFICATION))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已出库");
    }

    @Test
    void assertStatusTransitionAllowed_shouldGuardReverseToDraft() {
        SalesOrder entity = entity(StatusConstants.AUDITED);

        service.assertStatusTransitionAllowed(entity, StatusConstants.AUDITED, StatusConstants.DRAFT);

        verify(downstreamMutationGuard).assertMutable(entity, "反审核");
        verifyNoInteractions(deliveryVerificationGuard, salesOrderApplyService);
    }

    @Test
    void assertStatusTransitionAllowed_shouldNotGuardReverseToDraftWhenAlreadyDraft() {
        SalesOrder entity = entity(StatusConstants.DRAFT);

        service.assertStatusTransitionAllowed(entity, StatusConstants.DRAFT, StatusConstants.DRAFT);

        verifyNoInteractions(downstreamMutationGuard);
    }

    @Test
    void assertStatusTransitionAllowed_shouldValidateSnapshotWhenAuditing() {
        SalesOrder entity = entity(StatusConstants.DRAFT);
        entity.setItems(List.of(item(1L, null, null)));

        service.assertStatusTransitionAllowed(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);

        verify(salesOrderApplyService).validateCustomerSnapshot(entity);
    }

    @Test
    void assertStatusTransitionAllowed_shouldRejectNullQuantityWhenAuditing() {
        SalesOrder entity = entity(StatusConstants.DRAFT);
        SalesOrderItem item = item(null, null, null);
        item.setQuantity(null);
        entity.setItems(List.of(item));

        assertThatThrownBy(() -> service.assertStatusTransitionAllowed(
                entity, StatusConstants.DRAFT, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行数量必须至少为1个数量单位");
        verifyNoInteractions(salesOrderApplyService);
    }

    @Test
    void assertStatusTransitionAllowed_shouldRejectZeroQuantityWhenAuditing() {
        SalesOrder entity = entity(StatusConstants.DRAFT);
        SalesOrderItem item = item(null, null, null);
        item.setQuantity(0);
        entity.setItems(List.of(item));

        assertThatThrownBy(() -> service.assertStatusTransitionAllowed(
                entity, StatusConstants.DRAFT, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数量必须至少为1");
    }

    // ---------- assertItemMutationAllowed ----------

    @Test
    void assertItemMutationAllowed_shouldSkipGuardsForNewOrder() {
        SalesOrder entity = entity(StatusConstants.DRAFT);
        entity.setItems(List.of());

        service.assertItemMutationAllowed(entity, request(), false);

        verifyNoInteractions(downstreamMutationGuard, deliveryVerificationGuard);
    }

    @Test
    void assertItemMutationAllowed_shouldSkipGuardsForAuditedPricingUpdate() {
        SalesOrder entity = entity(StatusConstants.AUDITED);
        entity.setItems(List.of(item(1L, null, null)));

        service.assertItemMutationAllowed(entity, request(), true);

        verifyNoInteractions(downstreamMutationGuard, deliveryVerificationGuard);
    }

    @Test
    void assertItemMutationAllowed_shouldGuardExistingItemsOnOrdinaryUpdate() {
        SalesOrder entity = entity(StatusConstants.DRAFT);
        entity.setItems(List.of(item(1L, null, null)));

        service.assertItemMutationAllowed(entity, request(), false);

        verify(downstreamMutationGuard).assertNoFreightReference(entity, "修改");
        verify(downstreamMutationGuard).assertSourceLineMutationAllowed(eq(entity), anyList(), eq("修改"));
    }

    @Test
    void assertItemMutationAllowed_shouldGuardDeliveryVerificationState() {
        SalesOrder entity = entity(StatusConstants.DELIVERY_VERIFICATION);
        entity.setItems(List.of());

        service.assertItemMutationAllowed(entity, request(), false);

        verify(deliveryVerificationGuard).assertMutable(entity, "修改");
    }

    // ---------- allowsProtectedUpdate ----------

    @Test
    void allowsProtectedUpdate_shouldDelegateToPolicy() {
        SalesOrder entity = entity(StatusConstants.AUDITED);
        SalesOrderRequest req = request();
        when(protectedUpdatePolicy.allowsProtectedUpdate(entity, req)).thenReturn(true);

        assertThat(service.allowsProtectedUpdate(entity, req)).isTrue();
    }

    @Test
    void assertStatusTransitionAllowed_shouldLockSourcesFirst() {
        SalesOrder entity = entity(StatusConstants.DRAFT);
        entity.setItems(List.of(item(1L, 10L, 20L)));

        service.assertStatusTransitionAllowed(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);

        verify(sourceAllocationLockService).lockTradeItemSources(List.of(10L), List.of(20L), List.of());
    }

    @Test
    void assertDeletable_shouldNotInvokeDeliveryVerificationGuard() {
        SalesOrder entity = entity(StatusConstants.DRAFT);

        service.assertDeletable(entity);

        verify(deliveryVerificationGuard, never()).assertMutable(any(), anyString());
    }
}
