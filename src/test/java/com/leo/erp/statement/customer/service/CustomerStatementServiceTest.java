package com.leo.erp.statement.customer.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.api.SalesOrderLogisticsSourceQuery;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.customer.repository.CustomerStatementSummaryAggregate;
import com.leo.erp.statement.customer.repository.CustomerStatementSummaryQueryRepository;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import com.leo.erp.statement.service.StatementSettlementMutationGuard;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CustomerStatementService 极端情况测试。
 * <p>
 * 聚焦子类自定义逻辑：page/summary/search/candidatePage 查询、单号唯一性校验、
 * 删除与状态变更的结算守卫、apply 的财务联动守卫。
 */
@ExtendWith(MockitoExtension.class)
class CustomerStatementServiceTest {

    @Mock
    private CustomerStatementRepository repository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private CustomerStatementSummaryQueryRepository summaryQueryRepository;

    @Mock
    private CustomerStatementResponseAssembler responseAssembler;

    @Mock
    private CustomerStatementSourceService customerStatementSourceService;

    @Mock
    private CustomerStatementApplyService applyService;

    @Mock
    private SalesOrderLogisticsSourceQuery salesOrderSourceQuery;

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    @Mock
    private StatementSettlementMutationGuard settlementMutationGuard;

    @InjectMocks
    private CustomerStatementService service;

    private CustomerStatementRequest request() {
        return new CustomerStatementRequest(
                "CS001", null, "客户A", null, "项目A", null, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                new BigDecimal("5000"), null, null, null, null, List.of(), null, false);
    }

    // ---------- 查询 ----------

    @Test
    void candidatePage_shouldDelegate() {
        Page<CustomerStatementCandidateResponse> expected = mock(Page.class);
        when(customerStatementSourceService.candidatePage(any(), any())).thenReturn(expected);

        assertThat(service.candidatePage(mock(PageQuery.class), mock(PageFilter.class))).isSameAs(expected);
    }

    @Test
    void summary_shouldSummarize() {
        when(summaryQueryRepository.summarize(any()))
                .thenReturn(new CustomerStatementSummaryAggregate(3L, new BigDecimal("15000"),
                        new BigDecimal("5000"), new BigDecimal("10000")));

        var result = service.summary(mock(PageFilter.class));

        assertThat(result.documentCount()).isEqualTo(3);
        assertThat(result.salesAmount()).isEqualByComparingTo("15000");
        assertThat(result.closingAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void page_shouldMapEntitiesViaAssembler() {
        PageQuery query = mock(PageQuery.class);
        when(query.toPageable("id")).thenReturn(PageRequest.of(0, 10));
        CustomerStatement entity = new CustomerStatement();
        CustomerStatementResponse response = mock(CustomerStatementResponse.class);
        when(responseAssembler.toSummaryResponse(entity)).thenReturn(response);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1));

        Page<CustomerStatementResponse> result = service.page(query, mock(PageFilter.class));

        assertThat(result.getContent()).containsExactly(response);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ---------- 单号唯一性 ----------

    @Test
    void validateCreate_shouldRejectDuplicateStatementNo() {
        when(repository.existsByStatementNoAndDeletedFlagFalse("CS001")).thenReturn(true);

        assertThatThrownBy(() -> service.validateCreate(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对账单号已存在");
    }

    @Test
    void validateCreate_shouldPassWhenNoDuplicate() {
        when(repository.existsByStatementNoAndDeletedFlagFalse("CS001")).thenReturn(false);

        service.validateCreate(request()); // 不抛
    }

    @Test
    void validateUpdate_shouldRejectChangedDuplicateStatementNo() {
        CustomerStatement entity = new CustomerStatement();
        entity.setStatementNo("CS001");
        CustomerStatementRequest req = new CustomerStatementRequest(
                "CS999", null, "客户A", null, "项目A", null, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                new BigDecimal("5000"), null, null, null, null, List.of(), null, false);
        // 单号变更且重复 → 抛
        when(repository.existsByStatementNoAndDeletedFlagFalse("CS999")).thenReturn(true);

        assertThatThrownBy(() -> service.validateUpdate(entity, req))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- 结算守卫 ----------

    @Test
    void beforeDelete_shouldGuardSettledAllocations() {
        CustomerStatement entity = new CustomerStatement();
        entity.setId(5L);
        doThrow(new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "已结算"))
                .when(settlementMutationGuard).assertNoSettledAllocations(any(), any(), any());

        assertThatThrownBy(() -> service.beforeDelete(entity))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void beforeStatusUpdate_shouldGuardOnReverseConfirmation() {
        CustomerStatement entity = new CustomerStatement();
        entity.setId(5L);
        doThrow(new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "已结算"))
                .when(settlementMutationGuard).assertNoSettledAllocations(any(), any(), any());

        assertThatThrownBy(() -> service.beforeStatusUpdate(
                entity, StatusConstants.CONFIRMED, StatusConstants.PENDING_CONFIRM))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- apply ----------

    @Test
    void apply_shouldSkipFinancialGuardWhenCreating() {
        CustomerStatement entity = new CustomerStatement(); // status null → creating
        CustomerStatementRequest req = request();

        service.apply(entity, req);

        verify(applyService).apply(any(), any(), any());
        verify(settlementMutationGuard, org.mockito.Mockito.never())
                .assertFinancialLinkageMutationAllowed(any(), any(), anyBoolean());
    }

    @Test
    void apply_shouldGuardFinancialLinkageWhenUpdating() {
        CustomerStatement entity = new CustomerStatement();
        entity.setStatus(StatusConstants.PENDING_CONFIRM); // 非 creating
        entity.setCustomerName("客户A");
        CustomerStatementRequest req = request();

        service.apply(entity, req);

        verify(settlementMutationGuard).assertFinancialLinkageMutationAllowed(any(), any(), anyBoolean());
        verify(applyService).apply(any(), any(), any());
    }

    @Test
    void apply_shouldRejectWhenGuardBlocksUpdate() {
        CustomerStatement entity = new CustomerStatement();
        entity.setStatus(StatusConstants.PENDING_CONFIRM);
        entity.setCustomerName("客户A");
        doThrow(new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "已结算"))
                .when(settlementMutationGuard).assertFinancialLinkageMutationAllowed(any(), any(), anyBoolean());

        assertThatThrownBy(() -> service.apply(entity, request()))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- 雪花 ID 边界 ----------

    @Test
    void create_shouldFailForZeroSnowflakeId() {
        // resolveCreateBusinessNo 对非正 entityId 抛
        when(idGenerator.nextId()).thenReturn(0L);

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void detail_shouldReturnNotFoundWhenAbsent() {
        when(repository.findById(1L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.detail(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户对账单不存在");
    }
}
