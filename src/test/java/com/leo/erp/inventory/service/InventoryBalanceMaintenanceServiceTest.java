package com.leo.erp.inventory.service;

import com.leo.erp.inventory.repository.InventoryBalanceSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InventoryBalanceMaintenanceService 重建加锁与校验委托测试。
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
        order.verify(lockService).lockBackfill();
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
}
