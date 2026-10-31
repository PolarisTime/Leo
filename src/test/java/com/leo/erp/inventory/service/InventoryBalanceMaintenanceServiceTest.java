package com.leo.erp.inventory.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.inventory.repository.InventoryBalanceSnapshotRepository;
import com.leo.erp.inventory.web.dto.InventoryBalanceMismatchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InventoryBalanceMaintenanceService 重建加锁、校验委托与分页映射测试。
 */
@ExtendWith(MockitoExtension.class)
class InventoryBalanceMaintenanceServiceTest {

    @Mock
    private InventoryBalanceSnapshotRepository snapshotRepository;

    @Mock
    private InventoryTransactionLockService lockService;

    @InjectMocks
    private InventoryBalanceMaintenanceService service;

    @Test
    void rebuild_shouldLockBackfillBeforeRecomputing() {
        when(snapshotRepository.rebuild()).thenReturn(7);

        int affected = service.rebuild();

        assertThat(affected).isEqualTo(7);
        InOrder order = inOrder(lockService, snapshotRepository);
        order.verify(lockService).lockBackfillTxScoped();
        order.verify(snapshotRepository).rebuild();
    }

    @Test
    void reconcile_shouldDelegateToSnapshotRepository() {
        InventoryBalanceSnapshotRepository.BalanceMismatch mismatch =
                new InventoryBalanceSnapshotRepository.BalanceMismatch(
                        100L, 0L, 5L, 4L, new BigDecimal("20000.00"), new BigDecimal("16000.00"));
        when(snapshotRepository.findMismatches()).thenReturn(List.of(mismatch));

        assertThat(service.reconcile()).containsExactly(mismatch);
        verify(snapshotRepository).findMismatches();
    }

    @Test
    void reconcilePaged_shouldMapRepositoryRecordToWebDto() {
        long materialId = 9007199254740993L;
        InventoryBalanceSnapshotRepository.BalanceMismatch mismatch =
                new InventoryBalanceSnapshotRepository.BalanceMismatch(
                        materialId, 0L, 5L, 4L, new BigDecimal("20000.00"), new BigDecimal("16000.00"));
        when(snapshotRepository.countMismatches()).thenReturn(3L);
        when(snapshotRepository.findMismatches(
                " ORDER BY material_id ASC, warehouse_id ASC", 20, 20L)).thenReturn(List.of(mismatch));

        PageResponse<InventoryBalanceMismatchResponse> page = service.reconcile(new PageQuery(1, 20, null, null));

        assertThat(page.totalElements()).isEqualTo(3L);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.currentPage()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(20);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.content()).singleElement().satisfies(row -> {
            assertThat(row.materialId()).isEqualTo(materialId);
            assertThat(row.warehouseId()).isZero();
            assertThat(row.ledgerQuantity()).isEqualTo(5L);
            assertThat(row.snapshotQuantity()).isEqualTo(4L);
            assertThat(row.ledgerAmount()).isEqualByComparingTo("20000.00");
            assertThat(row.snapshotAmount()).isEqualByComparingTo("16000.00");
        });
    }

    @Test
    void reconcilePaged_shouldUseWhitelistedSortColumnWithTieBreaker() {
        when(snapshotRepository.countMismatches()).thenReturn(1L);
        when(snapshotRepository.findMismatches(anyString(), anyInt(), anyLong())).thenReturn(List.of());

        service.reconcile(new PageQuery(0, 20, "ledgerQuantity", "desc"));

        verify(snapshotRepository).findMismatches(
                " ORDER BY ledger_quantity DESC, material_id ASC, warehouse_id ASC", 20, 0L);
    }

    @Test
    void reconcilePaged_shouldFallBackToDefaultOrderForUnknownSortField() {
        when(snapshotRepository.countMismatches()).thenReturn(1L);
        when(snapshotRepository.findMismatches(anyString(), anyInt(), anyLong())).thenReturn(List.of());

        service.reconcile(new PageQuery(0, 20, "quantity", "desc"));

        verify(snapshotRepository).findMismatches(" ORDER BY material_id ASC, warehouse_id ASC", 20, 0L);
    }

    @Test
    void reconcilePaged_shouldSkipDataQueryWhenNothingMismatches() {
        when(snapshotRepository.countMismatches()).thenReturn(0L);

        PageResponse<InventoryBalanceMismatchResponse> page = service.reconcile(new PageQuery(0, 20, null, null));

        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
        assertThat(page.content()).isEmpty();
        verify(snapshotRepository, never()).findMismatches(anyString(), anyInt(), anyLong());
    }
}
