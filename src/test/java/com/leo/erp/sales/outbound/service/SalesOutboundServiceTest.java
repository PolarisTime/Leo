package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SalesOutboundService 门面职责测试：查询、校验、规范化与协作服务委托。
 * 写侧工作流与删除回退的详细行为见对应协作服务测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOutboundServiceTest {

    @Mock
    private SalesOutboundRepository repository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private SalesOutboundResponseAssembler responseAssembler;

    @Mock
    private SalesOutboundWorkflowService workflowService;

    @Mock
    private SalesOutboundDeleteRollbackService deleteRollbackService;

    @InjectMocks
    private SalesOutboundService service;

    private SalesOutboundRequest request(String outboundNo, String salesOrderNo, String status) {
        return new SalesOutboundRequest(
                outboundNo, salesOrderNo, 10L, "客户A", 20L, "项目A", 1L, "库房A",
                LocalDate.of(2026, 8, 1), status, null, List.of(), false);
    }

    private SalesOutbound entity(String status) {
        SalesOutbound entity = new SalesOutbound();
        entity.setId(5L);
        entity.setOutboundNo("OB001");
        entity.setStatus(status);
        return entity;
    }

    // ---------- 单号/导入校验 ----------

    @Test
    void validateCreate_shouldRejectDuplicateOutboundNo() {
        when(repository.existsByOutboundNoAndDeletedFlagFalse("OB001")).thenReturn(true);

        assertThatThrownBy(() -> service.validateCreate(request("OB001", "SO001", StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("出库单号已存在");
    }

    @Test
    void validateCreate_shouldRejectNonDraftStatus() {
        when(repository.existsByOutboundNoAndDeletedFlagFalse("OB001")).thenReturn(false);

        assertThatThrownBy(() -> service.validateCreate(request("OB001", "SO001", StatusConstants.AUDITED)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能保存为草稿");
    }

    @Test
    void validateCreate_shouldRejectMissingSalesOrderNo() {
        when(repository.existsByOutboundNoAndDeletedFlagFalse("OB001")).thenReturn(false);

        assertThatThrownBy(() -> service.validateCreate(request("OB001", "", StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须从已审核销售订单导入");
    }

    @Test
    void validateUpdate_shouldRejectChangedDuplicateNo() {
        SalesOutbound outbound = entity(StatusConstants.DRAFT);
        when(repository.existsByOutboundNoAndDeletedFlagFalse("OB999")).thenReturn(true);

        assertThatThrownBy(() -> service.validateUpdate(outbound, request("OB999", "SO001", StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- 写侧委托 ----------

    @Test
    void apply_shouldDelegateToWorkflow() {
        SalesOutbound outbound = entity(StatusConstants.DRAFT);
        SalesOutboundRequest req = request("OB001", "SO001", StatusConstants.DRAFT);

        service.apply(outbound, req);

        verify(workflowService).apply(any(), any(), any());
    }

    @Test
    void beforeStatusUpdate_shouldDelegateToWorkflow() {
        SalesOutbound outbound = entity(StatusConstants.AUDITED);

        service.beforeStatusUpdate(outbound, StatusConstants.AUDITED, StatusConstants.DRAFT);

        verify(workflowService).beforeStatusUpdate(outbound, StatusConstants.AUDITED, StatusConstants.DRAFT);
    }

    @Test
    void beforeDelete_shouldLockSourcesAndDelegate() {
        SalesOutbound outbound = entity(StatusConstants.DRAFT);
        SalesOutboundItem item = new SalesOutboundItem();
        item.setSourceSalesOrderItemId(11L);
        outbound.setItems(List.of(item));

        service.beforeDelete(outbound);

        verify(workflowService).lockSourceSalesOrderItems(outbound.getItems(), List.of());
        verify(deleteRollbackService).beforeDelete(outbound);
    }

    @Test
    void afterDelete_shouldDelegatePublish() {
        SalesOutbound outbound = entity(StatusConstants.DRAFT);

        service.afterDelete(outbound);

        verify(workflowService).publishDeleted(outbound);
    }

    @Test
    void saveCreatedEntity_shouldDelegateToWorkflow() {
        SalesOutbound outbound = entity(StatusConstants.DRAFT);
        SalesOutboundRequest req = request("OB001", "SO001", StatusConstants.DRAFT);

        service.saveCreatedEntity(outbound, req);

        verify(workflowService).saveCreated(outbound, req);
    }

    @Test
    void saveUpdatedEntity_shouldDelegateToWorkflow() {
        SalesOutbound outbound = entity(StatusConstants.DRAFT);
        SalesOutboundRequest req = request("OB001", "SO001", StatusConstants.DRAFT);

        service.saveUpdatedEntity(outbound, req);

        verify(workflowService).saveUpdated(outbound, req);
    }

    // ---------- 状态变更事件 ----------

    @Test
    void updateStatus_shouldPublishEventWhenChanged() {
        SalesOutbound outbound = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(java.util.Optional.of(outbound));
        when(workflowService.save(outbound)).thenReturn(outbound);
        SalesOutboundResponse response = mock(SalesOutboundResponse.class);
        when(response.status()).thenReturn(StatusConstants.AUDITED);
        when(responseAssembler.toDetailResponse(outbound)).thenReturn(response);

        service.updateStatus(5L, StatusConstants.AUDITED);

        verify(workflowService).publishStatusChanged(outbound, StatusConstants.DRAFT, StatusConstants.AUDITED);
    }

    @Test
    void updateStatus_shouldNotPublishWhenUnchanged() {
        SalesOutbound outbound = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(java.util.Optional.of(outbound));
        SalesOutboundResponse response = mock(SalesOutboundResponse.class);
        when(response.status()).thenReturn(StatusConstants.DRAFT);
        when(responseAssembler.toDetailResponse(outbound)).thenReturn(response);

        service.updateStatus(5L, StatusConstants.DRAFT);

        verify(workflowService, org.mockito.Mockito.never()).publishStatusChanged(any(), any(), any());
    }

    // ---------- 导入出库更新限制（委托真实策略） ----------

    @Test
    void normalizeUpdateRequest_shouldRestrictImportedOutbound() {
        SalesOutbound outbound = entity(StatusConstants.DRAFT);
        outbound.setSalesOrderNo("SO001");
        outbound.setCustomerId(10L);
        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(100L);
        item.setLineNo(1);
        item.setSourceSalesOrderItemId(11L);
        item.setWeightTon(new BigDecimal("12.500"));
        item.setUnitPrice(new BigDecimal("4000"));
        outbound.setItems(List.of(item));
        SalesOutboundItemRequest reqItem = new SalesOutboundItemRequest(
                100L, null, 11L, 500L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                1L, "库房A", "B001", 5, "件", new BigDecimal("1.250"), 100, null,
                new BigDecimal("4000"), null);

        SalesOutboundRequest req = new SalesOutboundRequest(
                "OB001", "SO001", 99L, "其它客户", 20L, "项目A", 9L, "库房A",
                LocalDate.of(2026, 8, 2), StatusConstants.DRAFT, null, List.of(reqItem), false);

        SalesOutboundRequest normalized = service.normalizeUpdateRequest(outbound, req);

        assertThat(normalized.items()).hasSize(1);
        assertThat(normalized.items().get(0).weightTon()).isEqualByComparingTo("12.500"); // 保留实体重量
        assertThat(normalized.customerId()).isEqualTo(10L); // 保留导入时的客户
        assertThat(normalized.salesOrderNo()).isEqualTo("SO001");
    }

    // ---------- 显式状态断言序列（原基类内联） ----------

    @Test
    void update_shouldRejectIllegalStatusTransitionAfterApply() {
        SalesOutbound outbound = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(java.util.Optional.of(outbound));
        doAnswer(invocation -> {
            outbound.setStatus(StatusConstants.PENDING_CONFIRM);
            return null;
        }).when(workflowService).apply(any(), any(), any());

        assertThatThrownBy(() -> service.update(5L, request("OB001", "SO001", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从「草稿」变更为「待确认」");

        verify(workflowService, never()).saveUpdated(any(), any());
    }

    @Test
    void update_shouldRejectEditInProtectedStatus() {
        SalesOutbound outbound = entity(StatusConstants.AUDITED);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(java.util.Optional.of(outbound));

        assertThatThrownBy(() -> service.update(5L, request("OB001", "SO001", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能编辑");

        verify(workflowService, never()).saveUpdated(any(), any());
        verify(workflowService, never()).save(any());
    }

    @Test
    void update_shouldAllowLegalTransitionAndPassFinalStatusGuard() {
        SalesOutbound outbound = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(java.util.Optional.of(outbound));
        doAnswer(invocation -> {
            outbound.setStatus(StatusConstants.AUDITED);
            return null;
        }).when(workflowService).apply(any(), any(), any());
        when(workflowService.saveUpdated(eq(outbound), any())).thenReturn(outbound);
        SalesOutboundResponse response = mock(SalesOutboundResponse.class);
        when(responseAssembler.toDetailResponse(outbound)).thenReturn(response);

        SalesOutboundResponse result = service.update(5L, request("OB001", "SO001", null));

        assertThat(result).isSameAs(response);
        verify(workflowService).saveUpdated(eq(outbound), any());
    }

    @Test
    void updateStatus_shouldRejectBlankStatus() {
        SalesOutbound outbound = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(java.util.Optional.of(outbound));

        assertThatThrownBy(() -> service.updateStatus(5L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");

        verify(workflowService, never()).beforeStatusUpdate(any(), any(), any());
        verify(workflowService, never()).save(any());
        verify(workflowService, never()).publishStatusChanged(any(), any(), any());
    }

    @Test
    void updateStatus_shouldRejectFinalStatusOutsideTransitionTable() {
        SalesOutbound outbound = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(java.util.Optional.of(outbound));

        assertThatThrownBy(() -> service.updateStatus(5L, StatusConstants.COMPLETED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从「草稿」变更为「已完成」");

        verify(workflowService, never()).beforeStatusUpdate(any(), any(), any());
        verify(workflowService, never()).save(any());
    }

    @Test
    void updateStatus_shouldGuardThenSaveThenPublishInOrder() {
        SalesOutbound outbound = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(java.util.Optional.of(outbound));
        when(workflowService.save(outbound)).thenReturn(outbound);
        SalesOutboundResponse response = mock(SalesOutboundResponse.class);
        when(response.status()).thenReturn(StatusConstants.AUDITED);
        when(responseAssembler.toDetailResponse(outbound)).thenReturn(response);

        service.updateStatus(5L, StatusConstants.AUDITED);

        InOrder inOrder = inOrder(workflowService);
        inOrder.verify(workflowService).beforeStatusUpdate(outbound, StatusConstants.DRAFT, StatusConstants.AUDITED);
        inOrder.verify(workflowService).save(outbound);
        inOrder.verify(workflowService).publishStatusChanged(outbound, StatusConstants.DRAFT, StatusConstants.AUDITED);
    }

    // ---------- 查询 ----------

    @Test
    void page_shouldMapEntities() {
        PageQuery query = mock(PageQuery.class);
        when(query.toPageable("id")).thenReturn(org.springframework.data.domain.PageRequest.of(0, 10));
        SalesOutbound outbound = entity(StatusConstants.DRAFT);
        SalesOutboundResponse response = mock(SalesOutboundResponse.class);
        when(responseAssembler.toSummaryResponse(outbound)).thenReturn(response);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(outbound)));

        org.springframework.data.domain.Page<SalesOutboundResponse> result =
                service.page(query, mock(PageFilter.class), null);

        assertThat(result.getContent()).containsExactly(response);
    }
}
