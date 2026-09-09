package com.leo.erp.statement.freight.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.mapper.FreightStatementWebMapper;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.freight.repository.FreightStatementSummaryAggregate;
import com.leo.erp.statement.freight.repository.FreightStatementSummaryQueryRepository;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FreightStatementService 门面职责测试：查询、汇总、单号校验与协作服务委托。
 * 写侧工作流（锁定/守卫/保存/事件）见 FreightStatementWorkflowServiceTest。
 */
@ExtendWith(MockitoExtension.class)
class FreightStatementServiceTest {

    @Mock
    private FreightStatementRepository repository;

    @Mock
    private FreightStatementSummaryQueryRepository summaryQueryRepository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private FreightStatementWebMapper freightStatementWebMapper;

    @Mock
    private FreightStatementSourceService freightStatementSourceService;

    @Mock
    private FreightStatementViewAssembler viewAssembler;

    @Mock
    private FreightStatementPageAssembler pageAssembler;

    @Mock
    private FreightStatementWorkflowService workflowService;

    @InjectMocks
    private FreightStatementService service;

    private FreightStatementCommand command(String statementNo, String status) {
        return new FreightStatementCommand(
                statementNo, "C001", "承运商A", 30L, "结算公司A", null, null, null, null,
                null, null, status, null, null, List.of(), 100L, false);
    }

    // ---------- 查询 ----------

    @Test
    void candidatePage_shouldDelegate() {
        Page<FreightStatementCandidateResponse> expected = mock(Page.class);
        when(freightStatementSourceService.candidatePage(any(), any(), any())).thenReturn(expected);

        assertThat(service.candidatePage(mock(PageQuery.class), mock(PageFilter.class), null)).isSameAs(expected);
    }

    @Test
    void summary_shouldSummarize() {
        when(summaryQueryRepository.summarize(any()))
                .thenReturn(new FreightStatementSummaryAggregate(2L, new BigDecimal("200"),
                        new BigDecimal("10000"), new BigDecimal("4000"), new BigDecimal("6000")));

        var result = service.summary(mock(PageFilter.class), null);

        assertThat(result.documentCount()).isEqualTo(2);
        assertThat(result.totalFreight()).isEqualByComparingTo("10000");
        assertThat(result.unpaidAmount()).isEqualByComparingTo("6000");
    }

    @Test
    void page_shouldMapViaPageAssembler() {
        PageQuery query = mock(PageQuery.class);
        when(query.toPageable("id")).thenReturn(PageRequest.of(0, 10));
        FreightStatement entity = new FreightStatement();
        FreightStatementView view = mock(FreightStatementView.class);
        when(pageAssembler.toViewPage(any())).thenReturn(new PageImpl<>(List.of(view)));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        Page<FreightStatementView> result = service.page(query, mock(PageFilter.class), null);

        assertThat(result.getContent()).containsExactly(view);
    }

    // ---------- 单号唯一性 ----------

    @Test
    void validateCreate_shouldRejectDuplicateStatementNo() {
        when(repository.existsByStatementNoAndDeletedFlagFalse("FS001")).thenReturn(true);

        assertThatThrownBy(() -> service.validateCreate(command("FS001", StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对账单号已存在");
    }

    @Test
    void validateCreate_shouldRejectNonDraftStatus() {
        when(repository.existsByStatementNoAndDeletedFlagFalse("FS001")).thenReturn(false);

        assertThatThrownBy(() -> service.validateCreate(command("FS001", StatusConstants.AUDITED)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能保存为草稿");
    }

    @Test
    void validateCreate_shouldAcceptNullStatus() {
        when(repository.existsByStatementNoAndDeletedFlagFalse("FS001")).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatCode(
                () -> service.validateCreate(command("FS001", null))).doesNotThrowAnyException();
    }

    @Test
    void validateUpdate_shouldRejectChangedDuplicateNo() {
        FreightStatement entity = new FreightStatement();
        entity.setStatementNo("FS001");
        when(repository.existsByStatementNoAndDeletedFlagFalse("FS999")).thenReturn(true);

        assertThatThrownBy(() -> service.validateUpdate(entity, command("FS999", StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- 写侧委托 ----------

    @Test
    void apply_shouldDelegateToWorkflow() {
        FreightStatement entity = new FreightStatement();
        FreightStatementCommand cmd = command("FS001", StatusConstants.DRAFT);

        service.apply(entity, cmd);

        verify(workflowService).apply(any(), any(), any());
    }

    @Test
    void beforeStatusUpdate_shouldDelegateToWorkflow() {
        FreightStatement entity = new FreightStatement();

        service.beforeStatusUpdate(entity, StatusConstants.AUDITED, StatusConstants.DRAFT);

        verify(workflowService).beforeStatusUpdate(entity, StatusConstants.AUDITED, StatusConstants.DRAFT);
    }

    @Test
    void beforeDelete_shouldDelegateToWorkflow() {
        FreightStatement entity = new FreightStatement();

        service.beforeDelete(entity);

        verify(workflowService).assertDeleteAllowed(entity);
    }

    @Test
    void afterDelete_shouldDelegatePublish() {
        FreightStatement entity = new FreightStatement();

        service.afterDelete(entity);

        verify(workflowService).publishDeleted(entity);
    }

    @Test
    void saveCreatedEntity_shouldDelegateToWorkflow() {
        FreightStatement entity = new FreightStatement();
        FreightStatementCommand cmd = command("FS001", StatusConstants.DRAFT);

        service.saveCreatedEntity(entity, cmd);

        verify(workflowService).saveCreated(entity, cmd);
    }

    @Test
    void saveUpdatedEntity_shouldDelegateToWorkflow() {
        FreightStatement entity = new FreightStatement();
        FreightStatementCommand cmd = command("FS001", StatusConstants.DRAFT);

        service.saveUpdatedEntity(entity, cmd);

        verify(workflowService).saveUpdated(entity, cmd);
    }

    // ---------- 状态变更事件 ----------

    @Test
    void updateStatus_shouldPublishEventWhenStatusChanged() {
        FreightStatement entity = new FreightStatement();
        entity.setId(5L);
        entity.setStatementNo("FS001");
        entity.setStatus(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(java.util.Optional.of(entity));
        when(workflowService.save(entity)).thenReturn(entity);
        FreightStatementView view = mock(FreightStatementView.class);
        when(view.status()).thenReturn(StatusConstants.AUDITED);
        when(viewAssembler.toDetailView(entity)).thenReturn(view);

        service.updateStatus(5L, StatusConstants.AUDITED);

        verify(workflowService).publishStatusChanged(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);
    }

    // ---------- normalize ----------

    @Test
    void normalizeUpdateRequest_shouldRejectStatusChange() {
        FreightStatement entity = new FreightStatement();
        entity.setStatementNo("FS001");
        entity.setStatus(StatusConstants.DRAFT);
        FreightStatementCommand cmd = command("FS001", StatusConstants.AUDITED);

        assertThatThrownBy(() -> service.normalizeUpdateRequest(entity, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能通过审核或反审核操作变更");
    }

    @Test
    void normalizeUpdateRequest_shouldKeepStatementNo() {
        FreightStatement entity = new FreightStatement();
        entity.setStatementNo("FS001");
        entity.setStatus(StatusConstants.DRAFT);
        FreightStatementCommand cmd = command("FS999", StatusConstants.DRAFT);

        FreightStatementCommand normalized = service.normalizeUpdateRequest(entity, cmd);

        assertThat(normalized.statementNo()).isEqualTo("FS001"); // 保留实体单号
    }

    // ---------- 显式状态断言序列（原基类内联） ----------

    @Test
    void update_shouldRejectIllegalStatusTransitionAfterApply() {
        FreightStatement entity = new FreightStatement();
        entity.setId(5L);
        entity.setStatementNo("FS001");
        entity.setStatus(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(java.util.Optional.of(entity));
        doAnswer(invocation -> {
            entity.setStatus(StatusConstants.PENDING_CONFIRM);
            return null;
        }).when(workflowService).apply(any(), any(), any());

        assertThatThrownBy(() -> service.update(5L, command("FS001", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从「草稿」变更为「待确认」");

        verify(workflowService, never()).saveUpdated(any(), any());
    }

    @Test
    void update_shouldRejectEditInProtectedStatus() {
        FreightStatement entity = new FreightStatement();
        entity.setId(5L);
        entity.setStatementNo("FS001");
        entity.setStatus(StatusConstants.AUDITED);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(java.util.Optional.of(entity));

        assertThatThrownBy(() -> service.update(5L, command("FS001", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能编辑");

        verify(workflowService, never()).saveUpdated(any(), any());
    }

    @Test
    void updateStatus_shouldRejectBlankStatus() {
        FreightStatement entity = new FreightStatement();
        entity.setId(5L);
        entity.setStatus(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(java.util.Optional.of(entity));

        assertThatThrownBy(() -> service.updateStatus(5L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");

        verify(workflowService, never()).beforeStatusUpdate(any(), any(), any());
        verify(workflowService, never()).save(any());
        verify(workflowService, never()).publishStatusChanged(any(), any(), any());
    }

    @Test
    void updateStatus_shouldRejectFinalStatusOutsideTransitionTable() {
        FreightStatement entity = new FreightStatement();
        entity.setId(5L);
        entity.setStatus(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(java.util.Optional.of(entity));

        assertThatThrownBy(() -> service.updateStatus(5L, StatusConstants.COMPLETED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从「草稿」变更为「已完成」");

        verify(workflowService, never()).beforeStatusUpdate(any(), any(), any());
        verify(workflowService, never()).save(any());
    }

    @Test
    void updateStatus_shouldShortCircuitWhenStatusUnchanged() {
        FreightStatement entity = new FreightStatement();
        entity.setId(5L);
        entity.setStatementNo("FS001");
        entity.setStatus(StatusConstants.AUDITED);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(java.util.Optional.of(entity));
        FreightStatementView view = mock(FreightStatementView.class);
        when(view.status()).thenReturn(StatusConstants.AUDITED);
        when(viewAssembler.toDetailView(entity)).thenReturn(view);

        FreightStatementView result = service.updateStatus(5L, StatusConstants.AUDITED);

        assertThat(result.status()).isEqualTo(StatusConstants.AUDITED);
        verify(workflowService, never()).save(any());
        verify(workflowService, never()).publishStatusChanged(any(), any(), any());
    }

    @Test
    void updateStatus_shouldGuardThenSaveThenPublishInOrder() {
        FreightStatement entity = new FreightStatement();
        entity.setId(5L);
        entity.setStatementNo("FS001");
        entity.setStatus(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(java.util.Optional.of(entity));
        when(workflowService.save(entity)).thenReturn(entity);
        FreightStatementView view = mock(FreightStatementView.class);
        when(view.status()).thenReturn(StatusConstants.AUDITED);
        when(viewAssembler.toDetailView(entity)).thenReturn(view);

        service.updateStatus(5L, StatusConstants.AUDITED);

        InOrder inOrder = inOrder(workflowService);
        inOrder.verify(workflowService).beforeStatusUpdate(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);
        inOrder.verify(workflowService).save(entity);
        inOrder.verify(workflowService).publishStatusChanged(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);
    }
}
