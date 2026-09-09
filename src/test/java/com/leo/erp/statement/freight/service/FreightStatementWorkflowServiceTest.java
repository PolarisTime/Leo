package com.leo.erp.statement.freight.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.service.StatementSettlementMutationGuard;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FreightStatementWorkflowService 边界测试：来源锁定、结算联动守卫、
 * 审核/删除守卫、保存同步与操作事件发布。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FreightStatementWorkflowServiceTest {

    @Mock
    private FreightStatementRepository repository;

    @Mock
    private StatementSettlementSyncService statementSettlementSyncService;

    @Mock
    private FreightStatementApplyService applyService;

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    @Mock
    private StatementSettlementMutationGuard settlementMutationGuard;

    @Mock
    private BusinessOperationEventPublisher businessOperationEventPublisher;

    @InjectMocks
    private FreightStatementWorkflowService service;

    private FreightStatementCommand command(Long sourceFreightBillId, Long carrierId) {
        return new FreightStatementCommand(
                "FS001", "C001", "承运商A", 30L, "结算公司A", null, null, null, null,
                null, null, StatusConstants.DRAFT, null, null,
                List.of(new FreightStatementItemCommand(
                        null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null,
                        sourceFreightBillId, null, null, null, null, null)),
                carrierId, false);
    }

    private FreightStatement statement(String status) {
        FreightStatement entity = new FreightStatement();
        entity.setId(5L);
        entity.setStatementNo("FS001");
        entity.setStatus(status);
        return entity;
    }

    // ---------- 来源锁定 ----------

    @Test
    void lockSourceBills_shouldCollectDistinctSortedSourceIds() {
        FreightStatement entity = statement(StatusConstants.DRAFT);
        FreightStatementItem itemA = new FreightStatementItem();
        itemA.setSourceFreightBillId(22L);
        FreightStatementItem itemB = new FreightStatementItem();
        itemB.setSourceFreightBillId(11L);
        FreightStatementItem itemNull = new FreightStatementItem();
        entity.setItems(List.of(itemA, itemB, itemNull));

        service.lockSourceFreightBills(entity, command(33L, null));

        verify(sourceAllocationLockService).lockDocumentSources(
                List.of(), List.of(), List.of(), List.of(11L, 22L, 33L));
    }

    @Test
    void lockSourceBills_shouldIgnoreCommandWhenNull() {
        FreightStatement entity = statement(StatusConstants.DRAFT);
        entity.setItems(List.of());

        service.lockSourceFreightBills(entity, null);

        verify(sourceAllocationLockService).lockDocumentSources(
                List.of(), List.of(), List.of(), List.of());
    }

    // ---------- apply ----------

    @Test
    void apply_shouldSkipFinancialGuardWhenCreating() {
        FreightStatement entity = statement(null);
        FreightStatementCommand cmd = command(null, null);

        service.apply(entity, cmd, () -> 99L);

        verify(settlementMutationGuard, never())
                .assertFinancialLinkageMutationAllowed(any(), any(), anyBoolean());
        verify(applyService).apply(eq(entity), eq(cmd), any(LongSupplier.class));
    }

    @Test
    void apply_shouldGuardFinancialLinkageWhenUpdating() {
        FreightStatement entity = statement(StatusConstants.DRAFT);
        entity.setCarrierId(100L);
        entity.setCarrierCode("C001");
        entity.setCarrierName("承运商A");
        entity.setSettlementCompanyId(30L);
        entity.setItems(List.of());
        FreightStatementCommand cmd = command(null, 100L);

        service.apply(entity, cmd, () -> 99L);

        ArgumentCaptor<Boolean> changed = ArgumentCaptor.forClass(Boolean.class);
        verify(settlementMutationGuard).assertFinancialLinkageMutationAllowed(
                eq(StatementSettlementMutationGuard.StatementType.FREIGHT), eq(5L), changed.capture());
        assertThat(changed.getValue()).isFalse();
    }

    @Test
    void apply_shouldDetectCarrierIdentityChange() {
        FreightStatement entity = statement(StatusConstants.DRAFT);
        entity.setCarrierId(100L);
        entity.setCarrierCode("C001");
        entity.setItems(List.of());
        FreightStatementCommand cmd = command(null, 200L);

        service.apply(entity, cmd, () -> 99L);

        ArgumentCaptor<Boolean> changed = ArgumentCaptor.forClass(Boolean.class);
        verify(settlementMutationGuard).assertFinancialLinkageMutationAllowed(
                any(), any(), changed.capture());
        assertThat(changed.getValue()).isTrue();
    }

    @Test
    void apply_shouldDetectSourceItemChange() {
        FreightStatement entity = statement(StatusConstants.DRAFT);
        entity.setCarrierId(100L);
        entity.setCarrierCode("C001");
        entity.setItems(List.of());
        FreightStatementCommand cmd = command(77L, 100L);

        service.apply(entity, cmd, () -> 99L);

        ArgumentCaptor<Boolean> changed = ArgumentCaptor.forClass(Boolean.class);
        verify(settlementMutationGuard).assertFinancialLinkageMutationAllowed(
                any(), any(), changed.capture());
        assertThat(changed.getValue()).isTrue();
    }

    @Test
    void apply_shouldPropagateGuardRejection() {
        FreightStatement entity = statement(StatusConstants.DRAFT);
        entity.setCarrierId(100L);
        entity.setItems(List.of());
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "已结算"))
                .when(settlementMutationGuard)
                .assertFinancialLinkageMutationAllowed(any(), any(), anyBoolean());

        assertThatThrownBy(() -> service.apply(entity, command(null, 100L), () -> 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结算");
        verify(applyService, never()).apply(any(), any(), any());
    }

    // ---------- 状态流转与删除守卫 ----------

    @Test
    void beforeStatusUpdate_shouldGuardReverseAudit() {
        FreightStatement entity = statement(StatusConstants.AUDITED);

        service.beforeStatusUpdate(entity, StatusConstants.AUDITED, StatusConstants.DRAFT);

        verify(settlementMutationGuard).assertNoSettledAllocations(
                StatementSettlementMutationGuard.StatementType.FREIGHT, 5L, "反审核");
    }

    @Test
    void beforeStatusUpdate_shouldSkipGuardForAudit() {
        FreightStatement entity = statement(StatusConstants.DRAFT);

        service.beforeStatusUpdate(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);

        verify(settlementMutationGuard, never()).assertNoSettledAllocations(any(), any(), any());
    }

    @Test
    void assertDeleteAllowed_shouldGuardSettledAllocations() {
        FreightStatement entity = statement(StatusConstants.DRAFT);
        entity.setItems(List.of());

        service.assertDeleteAllowed(entity);

        verify(sourceAllocationLockService).lockDocumentSources(
                List.of(), List.of(), List.of(), List.of());
        verify(settlementMutationGuard).assertNoSettledAllocations(
                StatementSettlementMutationGuard.StatementType.FREIGHT, 5L, "删除");
    }

    @Test
    void assertDeleteAllowed_shouldPropagateRejection() {
        FreightStatement entity = statement(StatusConstants.DRAFT);
        entity.setItems(List.of());
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "已结算"))
                .when(settlementMutationGuard).assertNoSettledAllocations(any(), any(), any());

        assertThatThrownBy(() -> service.assertDeleteAllowed(entity))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- 保存与事件 ----------

    @Test
    void save_shouldSyncSettlementAfterSave() {
        FreightStatement entity = statement(StatusConstants.DRAFT);
        FreightStatement synced = statement(StatusConstants.DRAFT);
        when(repository.save(entity)).thenReturn(entity);
        when(statementSettlementSyncService.syncFreightStatement(entity)).thenReturn(synced);

        assertThat(service.save(entity)).isSameAs(synced);
    }

    @Test
    void saveCreated_shouldPublishCreatedEvent() {
        FreightStatement entity = statement(StatusConstants.DRAFT);
        when(repository.save(entity)).thenReturn(entity);
        when(statementSettlementSyncService.syncFreightStatement(entity)).thenReturn(entity);

        service.saveCreated(entity, command(null, null));

        verify(businessOperationEventPublisher).publish(
                eq("FREIGHT_STATEMENT_CREATED"), eq("freight-statement"), eq("物流对账单"),
                eq("新增"), eq("FreightStatement"), eq(5L), eq("FS001"), any());
    }

    @Test
    void saveUpdated_shouldPublishUpdatedEvent() {
        FreightStatement entity = statement(StatusConstants.DRAFT);
        when(repository.save(entity)).thenReturn(entity);
        when(statementSettlementSyncService.syncFreightStatement(entity)).thenReturn(entity);

        service.saveUpdated(entity, command(null, null));

        verify(businessOperationEventPublisher).publish(
                eq("FREIGHT_STATEMENT_UPDATED"), eq("freight-statement"), eq("物流对账单"),
                eq("编辑"), eq("FreightStatement"), eq(5L), eq("FS001"), any());
    }

    @Test
    void publishStatusChanged_shouldUseReverseAuditActionForDraft() {
        FreightStatement entity = statement(StatusConstants.AUDITED);

        service.publishStatusChanged(entity, StatusConstants.AUDITED, StatusConstants.DRAFT);

        verify(businessOperationEventPublisher).publish(
                eq("FREIGHT_STATEMENT_STATUS_CHANGED"), eq("freight-statement"), eq("物流对账单"),
                eq("反审核"), eq("FreightStatement"), eq(5L), eq("FS001"),
                eq("物流对账单状态 " + StatusConstants.AUDITED + " -> " + StatusConstants.DRAFT));
    }

    @Test
    void publishStatusChanged_shouldUseAuditActionForAudited() {
        FreightStatement entity = statement(StatusConstants.DRAFT);

        service.publishStatusChanged(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);

        verify(businessOperationEventPublisher).publish(
                eq("FREIGHT_STATEMENT_STATUS_CHANGED"), any(), any(),
                eq("审核"), any(), eq(5L), any(), any());
    }

    @Test
    void publishDeleted_shouldPublishDeletedEvent() {
        FreightStatement entity = statement(StatusConstants.DRAFT);

        service.publishDeleted(entity);

        verify(businessOperationEventPublisher).publish(
                eq("FREIGHT_STATEMENT_DELETED"), eq("freight-statement"), eq("物流对账单"),
                eq("删除"), eq("FreightStatement"), eq(5L), eq("FS001"), eq("删除物流对账单 FS001"));
    }

    @Test
    void lockSourceBills_shouldNotLockWhenNoSources() {
        FreightStatement entity = statement(StatusConstants.DRAFT);
        entity.setItems(List.of());

        assertThatCode(() -> service.lockSourceFreightBills(entity, null)).doesNotThrowAnyException();

        verify(sourceAllocationLockService).lockDocumentSources(anyList(), anyList(), anyList(), anyList());
        verifyNoInteractions(applyService);
    }
}
