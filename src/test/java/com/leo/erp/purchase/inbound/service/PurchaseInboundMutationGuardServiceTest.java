package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * PurchaseInboundMutationGuardService 边界测试：来源行锁定、
 * 保存状态锁定、审核行校验与状态流转/删除守卫。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseInboundMutationGuardServiceTest {

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    @Mock
    private PurchaseInboundSourceStatusGuard purchaseInboundSourceStatusGuard;

    @InjectMocks
    private PurchaseInboundMutationGuardService service;

    private PurchaseInbound inbound(String status) {
        PurchaseInbound entity = new PurchaseInbound();
        entity.setId(5L);
        entity.setInboundNo("PI001");
        entity.setStatus(status);
        return entity;
    }

    private PurchaseInboundItem item(Long sourceId, Integer quantity, String settlementMode, String weighWeight) {
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setLineNo(1);
        item.setSourcePurchaseOrderItemId(sourceId);
        item.setQuantity(quantity);
        item.setSettlementMode(settlementMode);
        item.setWeighWeightTon(weighWeight == null ? null : new BigDecimal(weighWeight));
        return item;
    }

    private PurchaseInboundRequest request(Long... sourceIds) {
        return new PurchaseInboundRequest(
                "PI001", "PO001", 1L, "S001", "供应商A", 2L, "库房A",
                null, "过磅", null, null,
                java.util.Arrays.stream(sourceIds)
                        .map(id -> new PurchaseInboundItemRequest(
                                null, null, null, null, null, null, null, null, null,
                                id, null, null, null, null, null, null, null, null,
                                null, null, null, null, null, null))
                        .toList(),
                false);
    }

    // ---------- 来源锁定 ----------

    @Test
    void lockSources_shouldConcatDistinctSortedIds() {
        PurchaseInbound entity = inbound(null);
        PurchaseInboundItem existingA = item(22L, 1, null, null);
        PurchaseInboundItem existingB = item(11L, 1, null, null);
        PurchaseInboundItem existingDup = item(11L, 1, null, null);
        PurchaseInboundItem existingNull = item(null, 1, null, null);
        entity.setItems(List.of(existingA, existingB, existingDup, existingNull));

        service.lockSourcePurchaseOrderItems(entity, request(33L));

        verify(sourceAllocationLockService).lockTradeItemSources(List.of(11L, 22L, 33L), List.of(), List.of());
    }

    @Test
    void lockSources_shouldTolerateNullEntityAndRequest() {
        service.lockSourcePurchaseOrderItems(null, null);

        verify(sourceAllocationLockService).lockTradeItemSources(List.of(), List.of(), List.of());
    }

    // ---------- 保存状态锁定 ----------

    @Test
    void assertSaveDoesNotChangeStatus_shouldAcceptDraftForNewEntity() {
        assertThatCode(() -> service.assertSaveDoesNotChangeStatus(inbound(null), StatusConstants.DRAFT))
                .doesNotThrowAnyException();
    }

    @Test
    void assertSaveDoesNotChangeStatus_shouldRejectAuditedStatusForNewEntity() {
        assertThatThrownBy(() -> service.assertSaveDoesNotChangeStatus(inbound(null), StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("新建采购入库只能保存为草稿");
    }

    @Test
    void assertSaveDoesNotChangeStatus_shouldAcceptSameStatus() {
        assertThatCode(() -> service.assertSaveDoesNotChangeStatus(inbound(StatusConstants.DRAFT), StatusConstants.DRAFT))
                .doesNotThrowAnyException();
    }

    @Test
    void assertSaveDoesNotChangeStatus_shouldRejectStatusChange() {
        assertThatThrownBy(() -> service.assertSaveDoesNotChangeStatus(
                inbound(StatusConstants.DRAFT), StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("普通保存不能修改采购入库状态");
    }

    // ---------- 状态流转准备 ----------

    @Test
    void prepareStatusTransition_shouldGuardAndDelegate() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);
        entity.setItems(List.of(item(11L, 5, "过磅", "1.500")));

        service.prepareStatusTransition(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);

        verify(sourceAllocationLockService).lockTradeItemSources(List.of(11L), List.of(), List.of());
        verify(purchaseInboundSourceStatusGuard).assertStatusTransitionAllowed(
                entity, StatusConstants.DRAFT, StatusConstants.AUDITED);
    }

    @Test
    void prepareStatusTransition_shouldSkipLineCheckForDraft() {
        PurchaseInbound entity = inbound(StatusConstants.AUDITED);
        entity.setItems(List.of(item(11L, 0, null, null)));

        service.prepareStatusTransition(entity, StatusConstants.AUDITED, StatusConstants.DRAFT);

        verify(sourceAllocationLockService).lockTradeItemSources(List.of(11L), List.of(), List.of());
        verify(purchaseInboundSourceStatusGuard).assertStatusTransitionAllowed(
                entity, StatusConstants.AUDITED, StatusConstants.DRAFT);
    }

    @Test
    void prepareStatusTransition_shouldAcquireReverseReferenceLocksBeforePurchaseOrderLock() {
        PurchaseInbound entity = inbound(StatusConstants.AUDITED);
        entity.setItems(List.of(item(11L, 5, null, null)));

        service.prepareStatusTransition(entity, StatusConstants.AUDITED, StatusConstants.DRAFT);

        InOrder inOrder = inOrder(purchaseInboundSourceStatusGuard, sourceAllocationLockService);
        inOrder.verify(purchaseInboundSourceStatusGuard).lockReverseReferenceSources(entity);
        inOrder.verify(sourceAllocationLockService).lockTradeItemSources(List.of(11L), List.of(), List.of());
        inOrder.verify(purchaseInboundSourceStatusGuard).assertStatusTransitionAllowed(
                entity, StatusConstants.AUDITED, StatusConstants.DRAFT);
    }

    @Test
    void prepareStatusTransition_shouldNotPreLockReverseReferenceSourcesWhenAuditing() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);
        entity.setItems(List.of(item(11L, 5, "过磅", "1.500")));

        service.prepareStatusTransition(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);

        verify(purchaseInboundSourceStatusGuard, never()).lockReverseReferenceSources(any());
    }

    @Test
    void prepareStatusTransition_shouldRejectZeroQuantityOnAudit() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);
        PurchaseInboundItem bad = item(11L, 0, null, null);
        bad.setLineNo(3);
        entity.setItems(List.of(bad));

        assertThatThrownBy(() -> service.prepareStatusTransition(
                entity, StatusConstants.DRAFT, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第3行入库数量必须大于0");
    }

    @Test
    void prepareStatusTransition_shouldRejectNullQuantityOnAudit() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);
        entity.setItems(List.of(item(null, null, null, null)));

        assertThatThrownBy(() -> service.prepareStatusTransition(
                entity, StatusConstants.DRAFT, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("入库数量必须大于0");
    }

    @Test
    void prepareStatusTransition_shouldRejectWeighModeWithoutWeightOnAudit() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);
        entity.setItems(List.of(item(11L, 5, "过磅", null)));

        assertThatThrownBy(() -> service.prepareStatusTransition(
                entity, StatusConstants.DRAFT, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过磅重量");
    }

    @Test
    void prepareStatusTransition_shouldRejectZeroWeighWeightOnAudit() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);
        entity.setItems(List.of(item(11L, 5, "过磅", "0")));

        assertThatThrownBy(() -> service.prepareStatusTransition(
                entity, StatusConstants.DRAFT, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过磅重量");
    }

    @Test
    void prepareStatusTransition_shouldAcceptValidWeighLines() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);
        entity.setItems(List.of(item(11L, 5, "过磅", "2.350")));

        assertThatCode(() -> service.prepareStatusTransition(
                entity, StatusConstants.DRAFT, StatusConstants.AUDITED)).doesNotThrowAnyException();
    }

    // ---------- 删除守卫 ----------

    @Test
    void assertDeletionAllowed_shouldLockAndGuard() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);
        entity.setItems(List.of());

        service.assertDeletionAllowed(entity);

        InOrder inOrder = inOrder(purchaseInboundSourceStatusGuard, sourceAllocationLockService);
        inOrder.verify(purchaseInboundSourceStatusGuard).lockReverseReferenceSources(entity);
        inOrder.verify(sourceAllocationLockService).lockTradeItemSources(List.of(), List.of(), List.of());
        inOrder.verify(purchaseInboundSourceStatusGuard).assertDeletionAllowed(entity);
    }

    @Test
    void assertDeletionAllowed_shouldPropagateRejection() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);
        entity.setItems(List.of());
        org.mockito.Mockito.doThrow(new BusinessException(
                        com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "被引用"))
                .when(purchaseInboundSourceStatusGuard).assertDeletionAllowed(entity);

        assertThatThrownBy(() -> service.assertDeletionAllowed(entity))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("被引用");
    }

    @Test
    void prepareStatusTransition_shouldNotDelegateWhenLineCheckFails() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);
        entity.setItems(List.of(item(11L, -1, null, null)));

        assertThatThrownBy(() -> service.prepareStatusTransition(
                entity, StatusConstants.DRAFT, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class);
        verify(purchaseInboundSourceStatusGuard, never()).assertStatusTransitionAllowed(
                any(), any(), any());
    }
}
