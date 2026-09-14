package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.inventory.api.InventorySourceDocumentType;
import com.leo.erp.inventory.api.InventoryTransactionCommand;
import com.leo.erp.inventory.api.InventoryTransactionInput;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SalesOutboundWorkflowService 边界测试：来源锁定、明细应用、
 * 审核/反审核校验与操作事件发布。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalesOutboundWorkflowServiceTest {

    @Mock
    private SalesOutboundApplyService applyService;

    @Mock
    private SalesOutboundSaveService saveService;

    @Mock
    private SalesOutboundPurchaseInboundGuard purchaseInboundGuard;

    @Mock
    private SalesOutboundCoverageValidator coverageValidator;

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    @Mock
    private SalesOutboundDownstreamMutationGuard downstreamMutationGuard;

    @Mock
    private BusinessOperationEventPublisher businessOperationEventPublisher;

    @Mock
    private InventoryTransactionCommand inventoryCommand;

    @InjectMocks
    private SalesOutboundWorkflowService service;

    @BeforeEach
    void setUp() {
        service.setCoverageValidator(coverageValidator);
    }

    private SalesOutboundRequest request(String status) {
        return new SalesOutboundRequest(
                "OB001", "SO001", 10L, "客户A", 20L, "项目A", 1L, "库房A",
                LocalDate.of(2026, 8, 1), status, "备注", List.of(), false);
    }

    private SalesOutbound entity(String status) {
        SalesOutbound entity = new SalesOutbound();
        entity.setId(5L);
        entity.setOutboundNo("OB001");
        entity.setStatus(status);
        return entity;
    }

    // ---------- 来源锁定 ----------

    @Test
    void lockSourceItems_shouldCollectDistinctSortedSourceIds() {
        SalesOutboundItem existingA = new SalesOutboundItem();
        existingA.setSourceSalesOrderItemId(22L);
        SalesOutboundItem existingB = new SalesOutboundItem();
        existingB.setSourceSalesOrderItemId(11L);
        SalesOutboundItem existingNull = new SalesOutboundItem();
        SalesOutboundItemRequest requested = new SalesOutboundItemRequest(
                null, null, 33L, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);

        service.lockSourceSalesOrderItems(List.of(existingA, existingB, existingNull), List.of(requested));

        verify(sourceAllocationLockService).lockTradeItemSources(
                List.of(), List.of(), List.of(11L, 22L, 33L));
    }

    @Test
    void lockSourceItems_shouldHandleEmptyInputs() {
        service.lockSourceSalesOrderItems(List.of(), List.of());

        verify(sourceAllocationLockService).lockTradeItemSources(List.of(), List.of(), List.of());
    }

    // ---------- apply ----------

    @Test
    void apply_shouldSetFieldsAndValidateCoverage() {
        SalesOutbound entity = entity(null);

        service.apply(entity, request(StatusConstants.DRAFT), () -> 99L);

        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DRAFT);
        assertThat(entity.getCustomerId()).isEqualTo(10L);
        assertThat(entity.getProjectName()).isEqualTo("项目A");
        verify(applyService).applyItems(any(), any(), any());
        verify(coverageValidator).assertCumulativeCoverage(entity);
        verifyNoInteractions(purchaseInboundGuard);
    }

    @Test
    void apply_shouldKeepExistingOutboundNo() {
        SalesOutbound entity = entity(null);
        entity.setOutboundNo("OB-EXIST");

        service.apply(entity, request(StatusConstants.DRAFT), () -> 99L);

        assertThat(entity.getOutboundNo()).isEqualTo("OB-EXIST");
    }

    @Test
    void apply_shouldGuardPurchaseInboundWhenAudited() {
        SalesOutbound entity = entity(null);
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "采购未完成"))
                .when(purchaseInboundGuard).assertPurchaseInboundCompletedBeforeAudit(any());

        assertThatThrownBy(() -> service.apply(entity, request(StatusConstants.AUDITED), () -> 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购未完成");
    }

    @Test
    void apply_shouldPropagateApplyServiceFailure() {
        SalesOutbound entity = entity(null);
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "明细错误"))
                .when(applyService).applyItems(any(), any(), any());

        assertThatThrownBy(() -> service.apply(entity, request(StatusConstants.DRAFT), () -> 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("明细错误");
    }

    // ---------- beforeStatusUpdate ----------

    @Test
    void beforeStatusUpdate_shouldLockDocumentSourceOnReverseAudit() {
        SalesOutbound entity = entity(StatusConstants.AUDITED);

        service.beforeStatusUpdate(entity, StatusConstants.AUDITED, StatusConstants.DRAFT);

        verify(sourceAllocationLockService).lockDocumentSources(
                List.of(), List.of(), List.of(entity.getId()), List.of());
        verify(downstreamMutationGuard).assertReverseAuditAllowed(entity);
        verifyNoInteractions(purchaseInboundGuard);
    }

    @Test
    void beforeStatusUpdate_shouldGuardOnAudit() {
        SalesOutbound entity = entity(StatusConstants.DRAFT);

        service.beforeStatusUpdate(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);

        verify(purchaseInboundGuard).assertPurchaseInboundCompletedBeforeAudit(entity);
        verify(coverageValidator).assertCumulativeCoverage(entity);
        verify(downstreamMutationGuard, org.mockito.Mockito.never()).assertReverseAuditAllowed(any());
    }

    @Test
    void beforeStatusUpdate_shouldPropagateReverseAuditRejection() {
        SalesOutbound entity = entity(StatusConstants.AUDITED);
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "已使用"))
                .when(downstreamMutationGuard).assertReverseAuditAllowed(any());

        assertThatThrownBy(() -> service.beforeStatusUpdate(entity, StatusConstants.AUDITED, StatusConstants.DRAFT))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- 保存与事件 ----------

    @Test
    void save_shouldDelegateToSaveService() {
        SalesOutbound entity = entity(StatusConstants.DRAFT);
        when(saveService.save(entity)).thenReturn(entity);

        assertThat(service.save(entity)).isSameAs(entity);
    }

    @Test
    void saveCreated_shouldPublishCreatedEvent() {
        SalesOutbound entity = entity(StatusConstants.DRAFT);
        when(saveService.save(entity)).thenReturn(entity);

        service.saveCreated(entity, request(StatusConstants.DRAFT));

        verify(businessOperationEventPublisher).publish(
                eq("SALES_OUTBOUND_CREATED"), eq("sales-outbound"), eq("销售出库"),
                eq("新增"), eq("SalesOutbound"), eq(5L), eq("OB001"), any());
    }

    @Test
    void saveUpdated_shouldPublishUpdatedEvent() {
        SalesOutbound entity = entity(StatusConstants.DRAFT);
        when(saveService.save(entity)).thenReturn(entity);

        service.saveUpdated(entity, request(StatusConstants.DRAFT));

        verify(businessOperationEventPublisher).publish(
                eq("SALES_OUTBOUND_UPDATED"), eq("sales-outbound"), eq("销售出库"),
                eq("编辑"), eq("SalesOutbound"), eq(5L), eq("OB001"), any());
    }

    @Test
    void publishStatusChanged_shouldUseReverseAuditActionForDraft() {
        SalesOutbound entity = entity(StatusConstants.AUDITED);

        service.publishStatusChanged(entity, StatusConstants.AUDITED, StatusConstants.DRAFT);

        verify(businessOperationEventPublisher).publish(
                eq("SALES_OUTBOUND_STATUS_CHANGED"), eq("sales-outbound"), eq("销售出库"),
                eq("反审核"), eq("SalesOutbound"), eq(5L), eq("OB001"),
                eq("销售出库状态 " + StatusConstants.AUDITED + " -> " + StatusConstants.DRAFT));
    }

    @Test
    void publishStatusChanged_shouldUseAuditActionForAudited() {
        SalesOutbound entity = entity(StatusConstants.DRAFT);

        service.publishStatusChanged(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);

        verify(businessOperationEventPublisher).publish(
                eq("SALES_OUTBOUND_STATUS_CHANGED"), any(), any(),
                eq("审核"), any(), eq(5L), any(), any());
    }

    @Test
    void publishDeleted_shouldPublishDeletedEvent() {
        SalesOutbound entity = entity(StatusConstants.DRAFT);

        service.publishDeleted(entity);

        verify(businessOperationEventPublisher).publish(
                eq("SALES_OUTBOUND_DELETED"), eq("sales-outbound"), eq("销售出库"),
                eq("删除"), eq("SalesOutbound"), eq(5L), eq("OB001"), eq("删除销售出库 OB001"));
    }

    @Test
    void afterStatusChanged_shouldRecordInventoryOnAudit() {
        SalesOutbound entity = entity(StatusConstants.DRAFT);

        service.afterStatusChanged(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);

        verify(inventoryCommand).recordSalesOut(any(InventoryTransactionInput.class));
    }

    @Test
    void afterStatusChanged_shouldSoftDeleteInventoryOnReverseAudit() {
        SalesOutbound entity = entity(StatusConstants.AUDITED);

        service.afterStatusChanged(entity, StatusConstants.AUDITED, StatusConstants.DRAFT);

        verify(inventoryCommand).softDeleteBySource(
                InventorySourceDocumentType.SALES_OUTBOUND.name(), 5L);
    }

    @Test
    void afterStatusChanged_shouldIgnoreUnrelatedTransition() {
        SalesOutbound entity = entity(StatusConstants.DRAFT);

        service.afterStatusChanged(entity, StatusConstants.DRAFT, StatusConstants.DRAFT);

        verifyNoInteractions(inventoryCommand);
    }

    @Test
    void apply_shouldTolerateNullCoverageValidator() {
        SalesOutboundWorkflowService bareService = new SalesOutboundWorkflowService(
                applyService, saveService, purchaseInboundGuard, sourceAllocationLockService,
                downstreamMutationGuard, businessOperationEventPublisher, inventoryCommand);
        SalesOutbound entity = entity(null);

        assertThatCode(() -> bareService.apply(entity, request(StatusConstants.DRAFT), () -> 99L))
                .doesNotThrowAnyException();
        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DRAFT);
    }

    @Test
    void lockSourceItems_shouldNotInvokeOtherLockMethods() {
        service.lockSourceSalesOrderItems(List.of(), List.of());

        verify(sourceAllocationLockService, org.mockito.Mockito.never())
                .lockDocumentSources(anyList(), anyList(), anyList(), anyList());
    }

    @Test
    void apply_shouldPassIdSupplierToApplyItems() {
        SalesOutbound entity = entity(null);
        LongSupplier supplier = () -> 42L;

        service.apply(entity, request(StatusConstants.DRAFT), supplier);

        verify(applyService).applyItems(entity, request(StatusConstants.DRAFT), supplier);
    }

    @Test
    void apply_shouldNormalizeIllegalStatusFromRequest() {
        SalesOutbound entity = entity(null);
        SalesOutboundRequest badRequest = new SalesOutboundRequest(
                "OB001", "SO001", 10L, "客户A", 20L, "项目A", 1L, "库房A",
                LocalDate.of(2026, 8, 1), "不存在的状态", null,
                List.of(new SalesOutboundItemRequest(
                        null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, BigDecimal.ONE, null, null)),
                false);

        assertThatThrownBy(() -> service.apply(entity, badRequest, () -> 99L))
                .isInstanceOf(BusinessException.class);
    }
}
