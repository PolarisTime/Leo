package com.leo.erp.sales.returns.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.inventory.api.InventorySourceDocumentType;
import com.leo.erp.inventory.api.InventoryTransactionCommand;
import com.leo.erp.inventory.api.InventoryTransactionInput;
import com.leo.erp.sales.api.SalesReturnReversalCommand;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
}
