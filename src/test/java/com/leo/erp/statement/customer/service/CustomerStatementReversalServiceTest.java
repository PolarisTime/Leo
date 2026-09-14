package com.leo.erp.statement.customer.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import com.leo.erp.sales.returns.domain.entity.SalesReturnItem;
import com.leo.erp.sales.returns.repository.SalesReturnRepository;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
    private SalesReturnRepository salesReturnRepository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @InjectMocks
    private CustomerStatementReversalService service;

    private SalesReturnItem returnItem(Long sourceOrderItemId, int quantity, String weightTon, String amount) {
        SalesReturnItem item = new SalesReturnItem();
        item.setId(sourceOrderItemId == null ? 901L : sourceOrderItemId + 900L);
        item.setLineNo(1);
        item.setMaterialCode("M001");
        item.setMaterialId(500L);
        item.setBrand("品牌A");
        item.setCategory("型钢");
        item.setMaterial("螺纹钢");
        item.setSpec("HRB400");
        item.setLength("12m");
        item.setUnit("吨");
        item.setSourceSalesOutboundItemId(sourceOrderItemId == null ? null : sourceOrderItemId + 100L);
        item.setSourceSalesOrderItemId(sourceOrderItemId);
        item.setWarehouseId(1L);
        item.setBatchNo("B001");
        item.setQuantity(quantity);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("1.000"));
        item.setPiecesPerBundle(100);
        item.setWeightTon(new BigDecimal(weightTon));
        item.setUnitPrice(new BigDecimal("400.00"));
        item.setAmount(new BigDecimal(amount));
        return item;
    }

    private SalesReturn salesReturn(String status, List<SalesReturnItem> items) {
        SalesReturn ret = new SalesReturn();
        ret.setId(1L);
        ret.setReturnNo("SR001");
        ret.setCustomerId(10L);
        ret.setCustomerName("客户A");
        ret.setProjectId(20L);
        ret.setProjectName("项目A");
        ret.setSettlementCompanyId(30L);
        ret.setSettlementCompanyName("结算公司A");
        ret.setReturnDate(LocalDate.of(2026, 9, 1));
        ret.setStatus(status);
        ret.setItems(new ArrayList<>(items));
        return ret;
    }

    @Test
    void reverse_shouldCreateNegativeConfirmedStatementWhenOccupied() {
        SalesReturn ret = salesReturn(StatusConstants.AUDITED,
                List.of(returnItem(11L, 10, "10.00", "4000.00")));
        when(repository.existsBySourceSalesReturnIdAndDeletedFlagFalse(1L)).thenReturn(false);
        when(salesReturnRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(ret));
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
    void reverse_shouldBeIdempotentWhenAlreadyGenerated() {
        when(repository.existsBySourceSalesReturnIdAndDeletedFlagFalse(1L)).thenReturn(true);

        service.reverseForAuditedReturn(1L);

        verify(salesReturnRepository, never()).findByIdAndDeletedFlagFalse(anyLong());
        verify(repository, never()).save(any());
    }

    @Test
    void reverse_shouldSkipWhenSourceNotOccupiedByBlueStatement() {
        SalesReturn ret = salesReturn(StatusConstants.AUDITED,
                List.of(returnItem(11L, 10, "10.00", "4000.00")));
        when(repository.existsBySourceSalesReturnIdAndDeletedFlagFalse(1L)).thenReturn(false);
        when(salesReturnRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(ret));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());

        service.reverseForAuditedReturn(1L);

        verify(repository, never()).save(any());
    }

    @Test
    void reverse_shouldSkipNullReturnId() {
        service.reverseForAuditedReturn(null);
        verify(repository, never()).existsBySourceSalesReturnIdAndDeletedFlagFalse(any());
        verify(repository, never()).save(any());
    }

    @Test
    void reverse_shouldSkipWhenReturnNotFound() {
        when(repository.existsBySourceSalesReturnIdAndDeletedFlagFalse(1L)).thenReturn(false);
        when(salesReturnRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        service.reverseForAuditedReturn(1L);

        verify(repository, never()).save(any());
    }

    @Test
    void reverse_shouldSkipWhenReturnNotAudited() {
        SalesReturn ret = salesReturn(StatusConstants.DRAFT,
                List.of(returnItem(11L, 10, "10.00", "4000.00")));
        when(repository.existsBySourceSalesReturnIdAndDeletedFlagFalse(1L)).thenReturn(false);
        when(salesReturnRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(ret));

        service.reverseForAuditedReturn(1L);

        verify(repository, never()).save(any());
    }

    @Test
    void reverse_shouldIncludeOnlyOccupiedItemsWhenPartiallyOccupied() {
        SalesReturn ret = salesReturn(StatusConstants.AUDITED, List.of(
                returnItem(11L, 10, "10.00", "4000.00"),
                returnItem(12L, 5, "5.00", "2000.00")));
        when(repository.existsBySourceSalesReturnIdAndDeletedFlagFalse(1L)).thenReturn(false);
        when(salesReturnRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(ret));
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
        SalesReturn ret = salesReturn(StatusConstants.AUDITED, List.of(
                returnItem(11L, 10, "10.00", "4000.00"),
                returnItem(11L, 2, "2.00", "800.00")));
        when(repository.existsBySourceSalesReturnIdAndDeletedFlagFalse(1L)).thenReturn(false);
        when(salesReturnRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(ret));
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
        SalesReturn ret = salesReturn(StatusConstants.AUDITED,
                List.of(returnItem(null, 10, "10.00", "4000.00")));
        when(repository.existsBySourceSalesReturnIdAndDeletedFlagFalse(1L)).thenReturn(false);
        when(salesReturnRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(ret));

        service.reverseForAuditedReturn(1L);

        verify(repository, never()).save(any());
    }
}
