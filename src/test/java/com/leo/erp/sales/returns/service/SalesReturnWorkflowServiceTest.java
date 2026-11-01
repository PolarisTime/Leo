package com.leo.erp.sales.returns.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.inventory.api.InventorySourceDocumentType;
import com.leo.erp.inventory.api.InventoryTransactionCommand;
import com.leo.erp.inventory.api.InventoryTransactionInput;
import com.leo.erp.sales.api.SalesReturnReversalCommand;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import com.leo.erp.sales.returns.domain.entity.SalesReturnItem;
import com.leo.erp.sales.returns.web.dto.SalesReturnItemRequest;
import com.leo.erp.sales.returns.web.dto.SalesReturnRequest;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * SalesReturnWorkflowService 库存/红冲联动测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalesReturnWorkflowServiceTest {

    @Mock
    private SalesReturnApplyService applyService;

    @Mock
    private SalesReturnCoverageValidator coverageValidator;

    @Mock
    private SalesReturnSaveService saveService;

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    @Mock
    private BusinessOperationEventPublisher businessOperationEventPublisher;

    @Mock
    private SalesReturnReversalCommand salesReturnReversalCommand;

    @Mock
    private InventoryTransactionCommand inventoryCommand;

    @InjectMocks
    private SalesReturnWorkflowService service;

    private SalesReturn entity(String status) {
        SalesReturn salesReturn = new SalesReturn();
        salesReturn.setId(5L);
        salesReturn.setReturnNo("SR001");
        salesReturn.setStatus(status);
        return salesReturn;
    }

    @Test
    void afterStatusChanged_shouldReverseStatementsAndRecordInventoryOnAudit() {
        SalesReturn salesReturn = entity(StatusConstants.DRAFT);

        service.afterStatusChanged(salesReturn, StatusConstants.DRAFT, StatusConstants.AUDITED);

        verify(salesReturnReversalCommand).reverseForAuditedReturn(5L);
        verify(inventoryCommand).recordSalesReturnIn(any(InventoryTransactionInput.class));
    }

    @Test
    void afterStatusChanged_shouldSoftDeleteInventoryOnReverseAudit() {
        SalesReturn salesReturn = entity(StatusConstants.AUDITED);

        service.afterStatusChanged(salesReturn, StatusConstants.AUDITED, StatusConstants.DRAFT);

        verify(salesReturnReversalCommand).revertForReturn(5L);
        verify(inventoryCommand).softDeleteBySource(
                InventorySourceDocumentType.SALES_RETURN.name(), 5L);
        verify(salesReturnReversalCommand, never()).reverseForAuditedReturn(anyLong());
    }

    @Test
    void publishDeleted_shouldRevertReversalSoftDeleteInventoryAndPublish() {
        SalesReturn salesReturn = entity(StatusConstants.DRAFT);

        service.publishDeleted(salesReturn);

        verify(salesReturnReversalCommand).revertForReturn(5L);
        verify(inventoryCommand).softDeleteBySource(
                InventorySourceDocumentType.SALES_RETURN.name(), 5L);
        verify(businessOperationEventPublisher).publish(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void beforeStatusUpdate_shouldLockSourceOutboundItemsBeforeCoverageCheck() {
        SalesReturn salesReturn = entity(StatusConstants.DRAFT);

        service.beforeStatusUpdate(salesReturn, StatusConstants.DRAFT, StatusConstants.AUDITED);

        InOrder inOrder = Mockito.inOrder(sourceAllocationLockService, coverageValidator);
        inOrder.verify(sourceAllocationLockService).lockSalesOutboundItemSources(any());
        inOrder.verify(coverageValidator).assertCoverage(salesReturn);
    }

    @Test
    void beforeStatusUpdate_shouldNotLockOrValidateOnNonAuditTransition() {
        SalesReturn salesReturn = entity(StatusConstants.AUDITED);

        service.beforeStatusUpdate(salesReturn, StatusConstants.AUDITED, StatusConstants.DRAFT);

        verify(sourceAllocationLockService, never()).lockSalesOutboundItemSources(any());
        verify(coverageValidator, never()).assertCoverage(any());
    }

    // ---------- 保存路径（含草稿）来源出库明细锁 ----------

    private SalesReturnRequest request(String status, Long... sourceOutboundItemIds) {
        List<SalesReturnItemRequest> items = java.util.Arrays.stream(sourceOutboundItemIds)
                .map(id -> new SalesReturnItemRequest(
                        null, id, null, null, null, null, null, null, null, null, null,
                        null, null, null, 1, null, new BigDecimal("1.00000000"), 1, null,
                        new BigDecimal("0.00"), null))
                .toList();
        return new SalesReturnRequest("SR001", null, null, null, null, null, null, null,
                LocalDate.of(2026, 9, 1), status, null, items, false);
    }

    /** 草稿保存也必须先取来源出库明细锁（50→51），再做来源校验/明细应用。 */
    @Test
    void apply_shouldLockRequestedSourceOutboundItemsBeforeApplyingItemsOnDraftSave() {
        SalesReturn salesReturn = entity(null);
        SalesReturnRequest request = request(StatusConstants.DRAFT, 11L, 12L);

        service.apply(salesReturn, request, () -> 100L);

        InOrder inOrder = Mockito.inOrder(sourceAllocationLockService, applyService);
        inOrder.verify(sourceAllocationLockService).lockSalesOutboundItemSources(List.of(11L, 12L));
        inOrder.verify(applyService).applyItems(eq(salesReturn), eq(request), any());
        verify(coverageValidator, never()).assertCoverage(any());
    }

    /** 编辑草稿换来源时，单据已有明细与本次请求明细的来源出库明细都要锁（新旧两端）。 */
    @Test
    void apply_shouldLockBothExistingAndRequestedSourceOutboundItemsOnUpdate() {
        SalesReturn salesReturn = entity(StatusConstants.DRAFT);
        SalesReturnItem existing = new SalesReturnItem();
        existing.setId(7L);
        existing.setSourceSalesOutboundItemId(9L);
        salesReturn.setItems(List.of(existing));
        SalesReturnRequest request = request(StatusConstants.DRAFT, 11L);

        service.apply(salesReturn, request, () -> 100L);

        verify(sourceAllocationLockService).lockSalesOutboundItemSources(List.of(9L, 11L));
    }

    /** 保存并审核：先锁请求来源 → 应用明细 → 再锁应用后的来源 → 覆盖校验。 */
    @Test
    void apply_shouldLockThenApplyThenValidateCoverageForAuditedSave() {
        SalesReturn salesReturn = entity(null);
        SalesReturnRequest request = request(StatusConstants.AUDITED, 11L);

        service.apply(salesReturn, request, () -> 100L);

        InOrder inOrder = Mockito.inOrder(sourceAllocationLockService, applyService, coverageValidator);
        inOrder.verify(sourceAllocationLockService).lockSalesOutboundItemSources(List.of(11L));
        inOrder.verify(applyService).applyItems(eq(salesReturn), eq(request), any());
        inOrder.verify(sourceAllocationLockService).lockSalesOutboundItemSources(List.of());
        inOrder.verify(coverageValidator).assertCoverage(salesReturn);
    }
}
