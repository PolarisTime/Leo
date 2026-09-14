package com.leo.erp.inventory.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.inventory.api.InventorySourceDocumentType;
import com.leo.erp.inventory.api.InventoryTransactionInput;
import com.leo.erp.inventory.domain.entity.InventoryTransaction;
import com.leo.erp.inventory.repository.InventoryTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InventoryTransactionService 核心行为测试：幂等、移动加权平均成本、来源单价兜底、软删。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryTransactionServiceTest {

    @Mock
    private InventoryTransactionRepository repository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private InventoryTransactionLockService lockService;

    @Mock
    private InventoryBalanceReader balanceReader;

    @InjectMocks
    private InventoryTransactionService service;

    private InventoryTransactionInput input(InventoryTransactionInput.Line... lines) {
        return new InventoryTransactionInput(
                InventorySourceDocumentType.PURCHASE_INBOUND.name(),
                5L,
                "PI001",
                LocalDate.of(2026, 9, 1),
                7L,
                "库房A",
                List.of(lines)
        );
    }

    private InventoryTransactionInput.Line line(Long sourceItemId, int quantity, String sourceUnitPrice) {
        return new InventoryTransactionInput.Line(
                sourceItemId, 100L, "M001", 3L, "库房B", "B001",
                quantity, "件", new BigDecimal(sourceUnitPrice));
    }

    @Test
    void recordPurchaseIn_shouldSkipWhenActiveTransactionExists() {
        when(repository.existsBySourceDocumentTypeAndSourceItemIdAndTransactionTypeAndDeletedFlagFalse(
                "PURCHASE_INBOUND", 11L, "PURCHASE_IN")).thenReturn(true);

        service.recordPurchaseIn(input(line(11L, 5, "4000")));

        verify(repository, never()).save(any());
    }

    @Test
    void recordPurchaseIn_shouldUseSourcePriceAndPositiveAmount() {
        when(idGenerator.nextId()).thenReturn(1000L);
        when(repository.existsBySourceDocumentTypeAndSourceItemIdAndTransactionTypeAndDeletedFlagFalse(
                eq("PURCHASE_INBOUND"), eq(11L), eq("PURCHASE_IN"))).thenReturn(false);

        service.recordPurchaseIn(input(line(11L, 5, "4000")));

        InventoryTransaction saved = captureSaved();
        assertThat(saved.getTransactionNo()).isEqualTo("1000");
        assertThat(saved.getTransactionType()).isEqualTo("PURCHASE_IN");
        assertThat(saved.getDirection()).isEqualTo((short) 1);
        assertThat(saved.getUnitCost()).isEqualByComparingTo("4000.00");
        assertThat(saved.getAmount()).isEqualByComparingTo("20000.00");
        assertThat(saved.getWarehouseId()).isEqualTo(3L);
        verify(repository).flush();
    }

    @Test
    void recordSalesOut_shouldUseMovingAverageCost() {
        when(idGenerator.nextId()).thenReturn(2000L);
        when(balanceReader.currentBalance(100L, 3L))
                .thenReturn(new InventoryBalanceTotals(10L, new BigDecimal("30000.00")));

        service.recordSalesOut(input(line(11L, 4, "5000")));

        InventoryTransaction saved = captureSaved();
        assertThat(saved.getTransactionType()).isEqualTo("SALES_OUT");
        assertThat(saved.getDirection()).isEqualTo((short) -1);
        assertThat(saved.getUnitCost()).isEqualByComparingTo("3000.00");
        assertThat(saved.getAmount()).isEqualByComparingTo("-12000.00");
        verify(lockService).lock(100L, 3L);
    }

    @Test
    void recordSalesOut_shouldFallbackToSourcePriceWhenNoStock() {
        when(idGenerator.nextId()).thenReturn(2001L);
        when(balanceReader.currentBalance(100L, 3L)).thenReturn(InventoryBalanceTotals.EMPTY);

        service.recordSalesOut(input(line(11L, 3, "5000")));

        InventoryTransaction saved = captureSaved();
        assertThat(saved.getUnitCost()).isEqualByComparingTo("5000.00");
        assertThat(saved.getAmount()).isEqualByComparingTo("-15000.00");
    }

    @Test
    void recordSalesReturnIn_shouldUseMovingAverageCost() {
        when(idGenerator.nextId()).thenReturn(3000L);
        when(balanceReader.currentBalance(100L, 3L))
                .thenReturn(new InventoryBalanceTotals(10L, new BigDecimal("30000.00")));

        service.recordSalesReturnIn(input(line(11L, 2, "5000")));

        InventoryTransaction saved = captureSaved();
        assertThat(saved.getTransactionType()).isEqualTo("SALES_RETURN_IN");
        assertThat(saved.getDirection()).isEqualTo((short) 1);
        assertThat(saved.getUnitCost()).isEqualByComparingTo("3000.00");
        assertThat(saved.getAmount()).isEqualByComparingTo("6000.00");
    }

    @Test
    void record_shouldFallbackWarehouseToHeaderWhenLineMissing() {
        when(idGenerator.nextId()).thenReturn(4000L);
        InventoryTransactionInput.Line noWarehouse = new InventoryTransactionInput.Line(
                11L, 100L, "M001", null, null, null, 5, "件", new BigDecimal("4000"));

        service.recordPurchaseIn(input(noWarehouse));

        InventoryTransaction saved = captureSaved();
        assertThat(saved.getWarehouseId()).isEqualTo(7L);
        assertThat(saved.getWarehouseName()).isEqualTo("库房A");
        verify(lockService).lock(100L, 7L);
    }

    @Test
    void record_shouldSkipInvalidLine() {
        service.recordPurchaseIn(input(line(11L, 0, "4000")));

        verify(repository, never()).save(any());
    }

    @Test
    void softDeleteBySource_shouldFlagActiveTransactions() {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setId(9L);
        when(repository.findBySourceDocumentTypeAndSourceDocumentIdAndDeletedFlagFalse(
                "PURCHASE_INBOUND", 5L)).thenReturn(List.of(transaction));

        service.softDeleteBySource("PURCHASE_INBOUND", 5L);

        assertThat(transaction.isDeletedFlag()).isTrue();
        verify(repository).saveAll(List.of(transaction));
        verify(repository).flush();
    }

    @Test
    void softDeleteBySource_shouldNoOpWhenNothingActive() {
        when(repository.findBySourceDocumentTypeAndSourceDocumentIdAndDeletedFlagFalse(
                "SALES_OUTBOUND", 5L)).thenReturn(List.of());

        service.softDeleteBySource("SALES_OUTBOUND", 5L);

        verify(repository, never()).saveAll(any());
    }

    @Test
    void softDeleteBySource_shouldIgnoreNullArguments() {
        service.softDeleteBySource(null, 5L);
        service.softDeleteBySource("SALES_OUTBOUND", null);

        verify(repository, never()).findBySourceDocumentTypeAndSourceDocumentIdAndDeletedFlagFalse(any(), any());
    }

    private InventoryTransaction captureSaved() {
        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
