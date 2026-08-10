package com.leo.erp.statement.freight.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.mapper.FreightStatementWebMapper;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.freight.repository.FreightStatementSummaryAggregate;
import com.leo.erp.statement.freight.repository.FreightStatementSummaryQueryRepository;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
import com.leo.erp.statement.service.StatementSettlementMutationGuard;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FreightStatementService 极端情况测试。
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
    private StatementSettlementSyncService statementSettlementSyncService;

    @Mock
    private FreightStatementWebMapper freightStatementWebMapper;

    @Mock
    private FreightStatementSourceService freightStatementSourceService;

    @Mock
    private FreightStatementViewAssembler viewAssembler;

    @Mock
    private FreightStatementPageAssembler pageAssembler;

    @Mock
    private FreightStatementApplyService freightStatementApplyService;

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    @Mock
    private StatementSettlementMutationGuard settlementMutationGuard;

    @Mock
    private BusinessOperationEventPublisher businessOperationEventPublisher;

    @InjectMocks
    private FreightStatementService service;

    private FreightStatementCommand command(String statementNo, String status) {
        return new FreightStatementCommand(
                statementNo, "C001", "承运商A", 30L, "结算公司A", null, null, null, null,
                null, null, status, null, null, List.of(), 100L);
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
    void validateUpdate_shouldRejectChangedDuplicateNo() {
        FreightStatement entity = new FreightStatement();
        entity.setStatementNo("FS001");
        when(repository.existsByStatementNoAndDeletedFlagFalse("FS999")).thenReturn(true);

        assertThatThrownBy(() -> service.validateUpdate(entity, command("FS999", StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- 结算守卫 ----------

    @Test
    void beforeDelete_shouldGuardSettledAllocations() {
        FreightStatement entity = new FreightStatement();
        entity.setId(5L);
        doThrow(new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "已结算"))
                .when(settlementMutationGuard).assertNoSettledAllocations(any(), any(), any());

        assertThatThrownBy(() -> service.beforeDelete(entity)).isInstanceOf(BusinessException.class);
    }

    @Test
    void beforeStatusUpdate_shouldGuardReverseAudit() {
        FreightStatement entity = new FreightStatement();
        entity.setId(5L);
        doThrow(new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "已结算"))
                .when(settlementMutationGuard).assertNoSettledAllocations(any(), any(), any());

        assertThatThrownBy(() -> service.beforeStatusUpdate(
                entity, StatusConstants.AUDITED, StatusConstants.DRAFT))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- apply ----------

    @Test
    void apply_shouldSkipGuardWhenCreating() {
        FreightStatement entity = new FreightStatement();
        FreightStatementCommand cmd = command("FS001", StatusConstants.DRAFT);

        service.apply(entity, cmd);

        verify(freightStatementApplyService).apply(any(), any(), any());
        verify(settlementMutationGuard, org.mockito.Mockito.never())
                .assertFinancialLinkageMutationAllowed(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void apply_shouldGuardWhenUpdating() {
        FreightStatement entity = new FreightStatement();
        entity.setStatus(StatusConstants.DRAFT);
        entity.setCarrierCode("C001");
        FreightStatementCommand cmd = command("FS001", StatusConstants.DRAFT);

        service.apply(entity, cmd);

        verify(settlementMutationGuard).assertFinancialLinkageMutationAllowed(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(freightStatementApplyService).apply(any(), any(), any());
    }

    // ---------- 删除/状态事件 ----------

    @Test
    void afterDelete_shouldPublishEvent() {
        FreightStatement entity = new FreightStatement();
        entity.setId(5L);
        entity.setStatementNo("FS001");

        service.afterDelete(entity);

        verify(businessOperationEventPublisher).publish(eq("FREIGHT_STATEMENT_DELETED"), anyString(), anyString(),
                anyString(), anyString(), eq(5L), anyString(), anyString());
    }

    @Test
    void updateStatus_shouldPublishEventWhenStatusChanged() {
        FreightStatement entity = new FreightStatement();
        entity.setId(5L);
        entity.setStatementNo("FS001");
        entity.setStatus(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);
        when(statementSettlementSyncService.syncFreightStatement(entity)).thenReturn(entity);
        FreightStatementView view = mock(FreightStatementView.class);
        when(view.status()).thenReturn(StatusConstants.AUDITED);
        when(viewAssembler.toDetailView(entity)).thenReturn(view);

        service.updateStatus(5L, StatusConstants.AUDITED);

        verify(businessOperationEventPublisher).publish(eq("FREIGHT_STATEMENT_STATUS_CHANGED"), anyString(), anyString(),
                anyString(), anyString(), eq(5L), anyString(), anyString());
    }
}
