package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PurchaseInboundWorkflowService 边界测试：完成状态同步、
 * 过磅重量回写顺序、来源订单同步与操作事件发布。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseInboundWorkflowServiceTest {

    @Mock
    private PurchaseInboundRepository repository;

    @Mock
    private PurchaseInboundCompletionSyncService completionSyncService;

    @Mock
    private PurchaseInboundWeightWriteBackService weightWriteBackService;

    @Mock
    private PurchaseInboundDeleteService deleteService;

    @Mock
    private BusinessOperationEventPublisher businessOperationEventPublisher;

    @InjectMocks
    private PurchaseInboundWorkflowService service;

    private PurchaseInbound inbound(String status) {
        PurchaseInbound entity = new PurchaseInbound();
        entity.setId(5L);
        entity.setInboundNo("PI001");
        entity.setStatus(status);
        return entity;
    }

    private PurchaseInboundRequest request(boolean audit) {
        return new PurchaseInboundRequest(
                "PI001", "PO001", 1L, "S001", "供应商A", 2L, "库房A",
                LocalDate.of(2026, 8, 1), "过磅", StatusConstants.DRAFT, null, List.of(), audit);
    }

    @Test
    void save_shouldCompleteInboundWhenServerDecides() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);
        when(completionSyncService.shouldCompleteInbound(entity)).thenReturn(true);
        when(repository.save(entity)).thenReturn(entity);

        PurchaseInbound saved = service.save(entity);

        assertThat(saved.getStatus()).isEqualTo(StatusConstants.INBOUND_COMPLETED);
        verify(repository).save(entity);
        verify(repository).flush();
        verify(weightWriteBackService).synchronizeAfterSave(entity);
        verify(completionSyncService).synchronizeSourcePurchaseOrders(entity, false);
        assertThat(saved.isSourcePurchaseOrderReopenAllowed()).isFalse();
    }

    @Test
    void save_shouldKeepDraftWhenNotCompleted() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);
        when(completionSyncService.shouldCompleteInbound(entity)).thenReturn(false);
        when(repository.save(entity)).thenReturn(entity);

        PurchaseInbound saved = service.save(entity);

        assertThat(saved.getStatus()).isEqualTo(StatusConstants.DRAFT);
        verify(completionSyncService).synchronizeSourcePurchaseOrders(eq(entity), anyBoolean());
    }

    @Test
    void save_shouldSynchronizeOrderAfterWeightWriteBack() {
        PurchaseInbound entity = inbound(StatusConstants.AUDITED);
        when(completionSyncService.shouldCompleteInbound(entity)).thenReturn(false);
        when(repository.save(entity)).thenReturn(entity);

        service.save(entity);

        InOrder inOrder = inOrder(repository, weightWriteBackService, completionSyncService);
        inOrder.verify(repository).save(entity);
        inOrder.verify(repository).flush();
        inOrder.verify(weightWriteBackService).synchronizeAfterSave(entity);
        inOrder.verify(completionSyncService).synchronizeSourcePurchaseOrders(eq(entity), anyBoolean());
    }

    @Test
    void save_shouldResetReopenFlagAfterSync() {
        PurchaseInbound entity = inbound(StatusConstants.AUDITED);
        when(completionSyncService.shouldCompleteInbound(entity)).thenReturn(false);
        when(repository.save(entity)).thenReturn(entity);
        entity.setSourcePurchaseOrderReopenAllowed(true);

        PurchaseInbound saved = service.save(entity);

        verify(completionSyncService).synchronizeSourcePurchaseOrders(entity, true);
        assertThat(saved.isSourcePurchaseOrderReopenAllowed()).isFalse();
    }

    @Test
    void saveCreated_shouldPublishCreatedEvent() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);
        when(completionSyncService.shouldCompleteInbound(entity)).thenReturn(false);
        when(repository.save(entity)).thenReturn(entity);

        service.saveCreated(entity, request(false));

        verify(businessOperationEventPublisher).publish(
                eq("PURCHASE_INBOUND_CREATED"), eq("purchase-inbound"), eq("采购入库"),
                eq("新增"), eq("PurchaseInbound"), eq(5L), eq("PI001"), anyString());
    }

    @Test
    void saveUpdated_shouldPublishUpdatedEvent() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);
        when(completionSyncService.shouldCompleteInbound(entity)).thenReturn(false);
        when(repository.save(entity)).thenReturn(entity);

        service.saveUpdated(entity, request(false));

        verify(businessOperationEventPublisher).publish(
                eq("PURCHASE_INBOUND_UPDATED"), eq("purchase-inbound"), eq("采购入库"),
                eq("编辑"), eq("PurchaseInbound"), eq(5L), eq("PI001"), anyString());
    }

    @Test
    void saveStatus_shouldNotPublishEvent() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);
        when(completionSyncService.shouldCompleteInbound(entity)).thenReturn(false);
        when(repository.save(entity)).thenReturn(entity);

        service.saveStatus(entity);

        verify(businessOperationEventPublisher, never())
                .publish(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void publishStatusChanged_shouldUseReverseAuditActionForDraft() {
        PurchaseInbound entity = inbound(StatusConstants.AUDITED);

        service.publishStatusChanged(entity, StatusConstants.AUDITED, StatusConstants.DRAFT);

        verify(businessOperationEventPublisher).publish(
                eq("PURCHASE_INBOUND_STATUS_CHANGED"), eq("purchase-inbound"), eq("采购入库"),
                eq("反审核"), eq("PurchaseInbound"), eq(5L), eq("PI001"),
                eq("采购入库状态 " + StatusConstants.AUDITED + " -> " + StatusConstants.DRAFT));
    }

    @Test
    void publishStatusChanged_shouldUseGenericActionForOtherTransitions() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);

        service.publishStatusChanged(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);

        verify(businessOperationEventPublisher).publish(
                eq("PURCHASE_INBOUND_STATUS_CHANGED"), any(), any(),
                eq("状态变更"), any(), eq(5L), any(), any());
    }

    @Test
    void afterDelete_shouldFlushDeleteAndPublishInOrder() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);

        service.afterDelete(entity);

        InOrder inOrder = inOrder(repository, deleteService, businessOperationEventPublisher);
        inOrder.verify(repository).flush();
        inOrder.verify(deleteService).afterDelete(entity);
        inOrder.verify(businessOperationEventPublisher).publish(
                eq("PURCHASE_INBOUND_DELETED"), eq("purchase-inbound"), eq("采购入库"),
                eq("删除"), eq("PurchaseInbound"), eq(5L), eq("PI001"), anyString());
    }

    @Test
    void save_shouldSetInboundCompletedBeforeSave() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);
        when(repository.save(entity)).thenReturn(entity);
        when(completionSyncService.shouldCompleteInbound(entity)).thenReturn(true);

        service.save(entity);

        InOrder inOrder = inOrder(completionSyncService, repository);
        inOrder.verify(completionSyncService).shouldCompleteInbound(entity);
        inOrder.verify(repository).save(entity);
        assertThat(entity.getStatus()).isEqualTo(StatusConstants.INBOUND_COMPLETED);
    }

    @Test
    void saveCreated_shouldSyncWeightWithWeighModeRequest() {
        PurchaseInbound entity = inbound(StatusConstants.DRAFT);
        when(completionSyncService.shouldCompleteInbound(entity)).thenReturn(false);
        when(repository.save(entity)).thenReturn(entity);
        PurchaseInboundRequest weighRequest = new PurchaseInboundRequest(
                "PI001", "PO001", 1L, "S001", "供应商A", 2L, "库房A",
                LocalDate.of(2026, 8, 1), "过磅", StatusConstants.DRAFT, null,
                List.of(), false);

        service.saveCreated(entity, weighRequest);

        verify(weightWriteBackService).synchronizeAfterSave(entity);
        assertThat(weighRequest.settlementMode()).isEqualTo("过磅");
    }
}
