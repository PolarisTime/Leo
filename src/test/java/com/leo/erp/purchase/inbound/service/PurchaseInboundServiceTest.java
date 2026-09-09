package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.mapper.PurchaseInboundMapper;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PurchaseInboundService 显式状态断言序列（原基类内联）边界测试。
 */
@ExtendWith(MockitoExtension.class)
class PurchaseInboundServiceTest {

    @Mock
    private PurchaseInboundRepository repository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private PurchaseInboundMapper purchaseInboundMapper;

    @Mock
    private PurchaseInboundApplyService applyService;

    @Mock
    private PurchaseInboundResponseAssembler responseAssembler;

    @Mock
    private PurchaseInboundMutationGuardService mutationGuardService;

    @Mock
    private PurchaseInboundWorkflowService workflowService;

    @InjectMocks
    private PurchaseInboundService service;

    private PurchaseInbound entity(String status) {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(5L);
        inbound.setInboundNo("IN001");
        inbound.setStatus(status);
        return inbound;
    }

    private PurchaseInboundRequest request(String status) {
        return new PurchaseInboundRequest(
                "IN001", "PO001", 1L, "SUP001", "供应商A", 2L, "仓库A",
                LocalDate.of(2026, 8, 1), "MONTH", status, null, List.of(), false
        );
    }

    // ---------- updateStatus 显式序列 ----------

    @Test
    void updateStatus_shouldRejectBlankStatus() {
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity(StatusConstants.DRAFT)));

        assertThatThrownBy(() -> service.updateStatus(5L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");

        verify(workflowService, never()).saveStatus(any());
        verify(workflowService, never()).publishStatusChanged(any(), anyString(), anyString());
    }

    @Test
    void updateStatus_shouldRejectTransitionOutsideTable() {
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity(StatusConstants.AUDITED)));

        assertThatThrownBy(() -> service.updateStatus(5L, StatusConstants.INBOUND_COMPLETED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从「已审核」变更为「完成入库」");

        verify(mutationGuardService, never()).prepareStatusTransition(any(), anyString(), anyString());
        verify(workflowService, never()).saveStatus(any());
    }

    @Test
    void updateStatus_shouldPrepareTransitionMarkReopenAndPublishInOrder() {
        PurchaseInbound inbound = entity(StatusConstants.AUDITED);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(inbound));
        when(workflowService.saveStatus(inbound)).thenReturn(inbound);
        PurchaseInboundResponse response = mock(PurchaseInboundResponse.class);
        when(response.status()).thenReturn(StatusConstants.DRAFT);
        when(responseAssembler.toDetailResponse(inbound)).thenReturn(response);

        service.updateStatus(5L, StatusConstants.DRAFT);

        verify(mutationGuardService).prepareStatusTransition(inbound, StatusConstants.AUDITED, StatusConstants.DRAFT);
        assertThat(inbound.isSourcePurchaseOrderReopenAllowed()).isTrue();

        InOrder inOrder = inOrder(mutationGuardService, workflowService);
        inOrder.verify(mutationGuardService).prepareStatusTransition(inbound, StatusConstants.AUDITED, StatusConstants.DRAFT);
        inOrder.verify(workflowService).saveStatus(inbound);
        inOrder.verify(workflowService).publishStatusChanged(inbound, StatusConstants.AUDITED, StatusConstants.DRAFT);
    }

    @Test
    void updateStatus_shouldShortCircuitWhenStatusUnchanged() {
        PurchaseInbound inbound = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(inbound));
        PurchaseInboundResponse response = mock(PurchaseInboundResponse.class);
        when(response.status()).thenReturn(StatusConstants.DRAFT);
        when(responseAssembler.toDetailResponse(inbound)).thenReturn(response);

        assertThat(service.updateStatus(5L, StatusConstants.DRAFT)).isSameAs(response);

        verify(mutationGuardService, never()).prepareStatusTransition(any(), anyString(), anyString());
        verify(workflowService, never()).saveStatus(any());
        verifyNoInteractions(workflowService);
    }

    // ---------- update 显式序列 ----------

    @Test
    void update_shouldRejectProtectedEditWithoutPermission() {
        PurchaseInbound inbound = entity(StatusConstants.AUDITED);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(inbound));

        assertThatThrownBy(() -> service.update(5L, request(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前单据状态为「已审核」，不能编辑");

        verify(workflowService, never()).saveUpdated(any(), any());
        verifyNoInteractions(workflowService);
    }

    @Test
    void update_shouldSaveWhenStatusUnchanged() {
        PurchaseInbound inbound = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(inbound));
        when(workflowService.saveUpdated(eq(inbound), any())).thenReturn(inbound);

        service.update(5L, request(null));

        verify(workflowService).saveUpdated(eq(inbound), any());
        assertThat(inbound.getStatus()).isEqualTo(StatusConstants.DRAFT);
        verify(workflowService, never()).saveStatus(any());
    }

    // ---------- delete 显式序列 ----------

    @Test
    void delete_shouldRejectWhenMutationGuardFails() {
        PurchaseInbound inbound = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(inbound));
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "已入库"))
                .when(mutationGuardService).assertDeletionAllowed(inbound);

        assertThatThrownBy(() -> service.delete(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已入库");

        verify(repository, never()).save(any());
        verifyNoInteractions(workflowService);
    }

    @Test
    void delete_shouldSoftDeleteThenRunAfterDeleteInOrder() {
        PurchaseInbound inbound = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(inbound));

        service.delete(5L);

        assertThat(inbound.isDeletedFlag()).isTrue();
        InOrder inOrder = inOrder(repository, workflowService);
        inOrder.verify(repository).save(inbound);
        inOrder.verify(workflowService).afterDelete(inbound);
    }
}
