package com.leo.erp.inventory.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * InventoryTransactionLockService 全局加锁顺序测试：所有维度必须按 (materialId, warehouseId) 升序获取。
 */
@ExtendWith(MockitoExtension.class)
class InventoryTransactionLockServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private InventoryTransactionLockService service;

    @Test
    void lockAll_shouldAcquireDimensionsInAscendingOrderWithNullWarehouseLast() {
        service.lockAll(List.of(
                new InventoryTransactionLockService.Dimension(20L, 5L),
                new InventoryTransactionLockService.Dimension(10L, 9L),
                new InventoryTransactionLockService.Dimension(20L, 1L),
                new InventoryTransactionLockService.Dimension(10L, null),
                new InventoryTransactionLockService.Dimension(10L, 1L)));

        ArgumentCaptor<SqlParameterSource> captor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate, times(5)).query(anyString(), captor.capture(), any(RowCallbackHandler.class));
        assertThat(captor.getAllValues())
                .extracting(source -> source.getValue("lockKey"))
                .containsExactly(
                        "inv:10:1",
                        "inv:10:9",
                        "inv:10:0",
                        "inv:20:1",
                        "inv:20:5");
    }

    @Test
    void lockAll_shouldIgnoreNullMaterialDimension() {
        service.lockAll(List.of(new InventoryTransactionLockService.Dimension(null, 5L)));

        verify(jdbcTemplate, never()).query(anyString(), any(SqlParameterSource.class), any(RowCallbackHandler.class));
    }

    @Test
    void lockAll_shouldNoOpWhenEmpty() {
        service.lockAll(List.of());
        service.lockAll(null);

        verify(jdbcTemplate, never()).query(anyString(), any(SqlParameterSource.class), any(RowCallbackHandler.class));
    }

    /**
     * 互斥协议不变量：重建/回填的排他锁与记账的共享锁必须落在<b>同一</b>
     * {@code inv:backfill} 键上，且模式不同（排他 vs 共享）；否则覆盖写仍会吞掉并发记账。
     */
    @Test
    void backfillLocks_shouldUseSameKeyWithExclusiveForRebuildAndSharedForRecording() {
        service.lockBackfillTxScoped();
        service.lockBackfillSharedTxScoped();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate, times(2)).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowCallbackHandler.class));

        assertThat(sqlCaptor.getAllValues())
                .hasSize(2)
                .satisfiesExactly(
                        exclusive -> {
                            assertThat(exclusive).contains("pg_advisory_xact_lock(");
                            assertThat(exclusive).doesNotContain("_shared");
                        },
                        shared -> assertThat(shared).contains("pg_advisory_xact_lock_shared("));
        assertThat(paramsCaptor.getAllValues())
                .extracting(source -> source.getValue("lockKey"))
                .containsExactly("inv:backfill", "inv:backfill");
    }
}
