package com.leo.erp.statement.customer.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.api.SalesReturnReversalSourceQuery;
import com.leo.erp.sales.api.SalesReturnReversalSourceQuery.ItemSnapshot;
import com.leo.erp.sales.api.SalesReturnReversalSourceQuery.ReturnSnapshot;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.service.StatementSettlementMutationGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CustomerStatementReversalService 极端情况测试：红字生成、幂等、未占用不生成、非审核跳过。
 */
@ExtendWith(MockitoExtension.class)
class CustomerStatementReversalServiceTest {

    @Mock
    private CustomerStatementRepository repository;

    @Mock
    private SalesReturnReversalSourceQuery salesReturnSourceQuery;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private StatementSettlementMutationGuard settlementMutationGuard;

    @InjectMocks
    private CustomerStatementReversalService service;

    private ItemSnapshot returnItem(Long sourceOrderItemId, int quantity, String weightTon, String amount) {
        return new ItemSnapshot(
                sourceOrderItemId,
                quantity,
                new BigDecimal(weightTon),
                new BigDecimal(amount),
                500L,
                1L,
                "M001",
                "品牌A",
                "型钢",
                "螺纹钢",
                "HRB400",
                "12m",
                "吨",
                "B001",
                "件",
                new BigDecimal("1.000"),
                100,
                new BigDecimal("400.00")
        );
    }

    private ReturnSnapshot salesReturn(String status, List<ItemSnapshot> items) {
        return new ReturnSnapshot(
                1L,
                "SR001",
                status,
                10L,
                "客户A",
                20L,
                "项目A",
                30L,
                "结算公司A",
                LocalDate.of(2026, 9, 1),
                items
        );
    }

    @Test
    void reverse_shouldCreateNegativeConfirmedStatementWhenOccupied() {
        when(salesReturnSourceQuery.findById(1L)).thenReturn(salesReturn(StatusConstants.AUDITED,
                List.of(returnItem(11L, 10, "10.00", "4000.00"))));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of(11L));
        when(idGenerator.nextId()).thenReturn(500L, 501L);

        service.reverseForAuditedReturn(1L);

        ArgumentCaptor<CustomerStatement> captor = ArgumentCaptor.forClass(CustomerStatement.class);
        verify(repository).save(captor.capture());
        CustomerStatement saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(500L);
        assertThat(saved.getDirection()).isEqualTo(StatusConstants.STATEMENT_DIRECTION_RED);
        assertThat(saved.getStatus()).isEqualTo(StatusConstants.CONFIRMED);
        assertThat(saved.getSourceSalesReturnId()).isEqualTo(1L);
        assertThat(saved.getSourceSalesReturnNo()).isEqualTo("SR001");
        assertThat(saved.getSalesAmount()).isEqualByComparingTo("-4000.00");
        assertThat(saved.getReceiptAmount()).isEqualByComparingTo("0");
        assertThat(saved.getClosingAmount()).isEqualByComparingTo("-4000.00");
        assertThat(saved.getItems()).hasSize(1);
        CustomerStatementItem item = saved.getItems().get(0);
        assertThat(item.getQuantity()).isEqualTo(-10);
        assertThat(item.getWeightTon()).isEqualByComparingTo("-10.00");
        assertThat(item.getAmount()).isEqualByComparingTo("-4000.00");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("400.00");
        assertThat(item.getSourceSalesOrderItemId()).isEqualTo(11L);
    }

    @Test
    void reverse_shouldCleanupExistingActiveReversalThenRebuild() {
        when(salesReturnSourceQuery.findById(1L)).thenReturn(salesReturn(StatusConstants.AUDITED,
                List.of(returnItem(11L, 10, "10.00", "4000.00"))));
        CustomerStatement stale = new CustomerStatement();
        stale.setId(700L);
        stale.setDirection(StatusConstants.STATEMENT_DIRECTION_RED);
        stale.setDeletedFlag(false);
        when(repository.findBySourceSalesReturnIdAndDeletedFlagFalse(1L)).thenReturn(List.of(stale));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of(11L));
        when(idGenerator.nextId()).thenReturn(500L, 501L);

        service.reverseForAuditedReturn(1L);

        assertThat(stale.isDeletedFlag()).isTrue();
        verify(settlementMutationGuard).assertNoSettledAllocations(
                StatementSettlementMutationGuard.StatementType.CUSTOMER, 700L, "重新审核销售退货单");
        ArgumentCaptor<CustomerStatement> captor = ArgumentCaptor.forClass(CustomerStatement.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(500L);
        verify(repository).saveAndFlush(stale);
    }

    @Test
    void reverse_shouldSkipWhenSourceNotOccupiedByBlueStatement() {
        when(salesReturnSourceQuery.findById(1L)).thenReturn(salesReturn(StatusConstants.AUDITED,
                List.of(returnItem(11L, 10, "10.00", "4000.00"))));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());

        service.reverseForAuditedReturn(1L);

        verify(repository, never()).save(any());
    }

    @Test
    void reverse_shouldSkipNullReturnId() {
        service.reverseForAuditedReturn(null);
        verify(repository, never()).findBySourceSalesReturnIdAndDeletedFlagFalse(any());
        verify(repository, never()).save(any());
    }

    @Test
    void revertForReturn_shouldSoftDeleteActiveReversal() {
        CustomerStatement active = new CustomerStatement();
        active.setId(700L);
        active.setDirection(StatusConstants.STATEMENT_DIRECTION_RED);
        active.setDeletedFlag(false);
        when(repository.findBySourceSalesReturnIdAndDeletedFlagFalse(1L)).thenReturn(List.of(active));

        service.revertForReturn(1L);

        assertThat(active.isDeletedFlag()).isTrue();
        verify(settlementMutationGuard).assertNoSettledAllocations(
                StatementSettlementMutationGuard.StatementType.CUSTOMER, 700L, "反审核销售退货单");
        verify(repository).saveAndFlush(active);
    }

    @Test
    void revertForReturn_shouldBeIdempotentWhenNoActiveReversal() {
        when(repository.findBySourceSalesReturnIdAndDeletedFlagFalse(1L)).thenReturn(List.of());

        service.revertForReturn(1L);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void revertForReturn_shouldSkipNullReturnId() {
        service.revertForReturn(null);

        verify(repository, never()).findBySourceSalesReturnIdAndDeletedFlagFalse(any());
    }

    @Test
    void revertForReturn_shouldRejectWhenReversalSettled() {
        CustomerStatement active = new CustomerStatement();
        active.setId(700L);
        active.setDeletedFlag(false);
        when(repository.findBySourceSalesReturnIdAndDeletedFlagFalse(1L)).thenReturn(List.of(active));
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "客户对账单已存在已收款核销"))
                .when(settlementMutationGuard)
                .assertNoSettledAllocations(
                        eq(StatementSettlementMutationGuard.StatementType.CUSTOMER),
                        eq(700L),
                        any());

        assertThatThrownBy(() -> service.revertForReturn(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已收款核销");
        assertThat(active.isDeletedFlag()).isFalse();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void reverse_shouldSkipWhenReturnNotFound() {
        when(salesReturnSourceQuery.findById(1L)).thenReturn(null);

        service.reverseForAuditedReturn(1L);

        verify(repository, never()).save(any());
    }

    @Test
    void reverse_shouldSkipWhenReturnNotAudited() {
        when(salesReturnSourceQuery.findById(1L)).thenReturn(salesReturn(StatusConstants.DRAFT,
                List.of(returnItem(11L, 10, "10.00", "4000.00"))));

        service.reverseForAuditedReturn(1L);

        verify(repository, never()).save(any());
    }

    @Test
    void reverse_shouldIncludeOnlyOccupiedItemsWhenPartiallyOccupied() {
        when(salesReturnSourceQuery.findById(1L)).thenReturn(salesReturn(StatusConstants.AUDITED, List.of(
                returnItem(11L, 10, "10.00", "4000.00"),
                returnItem(12L, 5, "5.00", "2000.00"))));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of(11L));
        when(idGenerator.nextId()).thenReturn(500L, 501L);

        service.reverseForAuditedReturn(1L);

        ArgumentCaptor<CustomerStatement> captor = ArgumentCaptor.forClass(CustomerStatement.class);
        verify(repository).save(captor.capture());
        CustomerStatement saved = captor.getValue();
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getSourceSalesOrderItemId()).isEqualTo(11L);
        assertThat(saved.getSalesAmount()).isEqualByComparingTo("-4000.00");
    }

    @Test
    void reverse_shouldAggregateLinesSharingSourceOrderItem() {
        when(salesReturnSourceQuery.findById(1L)).thenReturn(salesReturn(StatusConstants.AUDITED, List.of(
                returnItem(11L, 10, "10.00", "4000.00"),
                returnItem(11L, 2, "2.00", "800.00"))));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of(11L));
        when(idGenerator.nextId()).thenReturn(500L, 501L);

        service.reverseForAuditedReturn(1L);

        ArgumentCaptor<CustomerStatement> captor = ArgumentCaptor.forClass(CustomerStatement.class);
        verify(repository).save(captor.capture());
        CustomerStatement saved = captor.getValue();
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getQuantity()).isEqualTo(-12);
        assertThat(saved.getItems().get(0).getWeightTon()).isEqualByComparingTo("-12.00");
        assertThat(saved.getSalesAmount()).isEqualByComparingTo("-4800.00");
    }

    @Test
    void reverse_shouldSkipWhenNoSourceOrderItemId() {
        when(salesReturnSourceQuery.findById(1L)).thenReturn(salesReturn(StatusConstants.AUDITED,
                List.of(returnItem(null, 10, "10.00", "4000.00"))));

        service.reverseForAuditedReturn(1L);

        verify(repository, never()).save(any());
    }
}
