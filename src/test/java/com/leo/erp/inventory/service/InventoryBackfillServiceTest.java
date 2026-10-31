package com.leo.erp.inventory.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.inventory.api.InventoryTransactionCommand;
import com.leo.erp.inventory.api.InventoryTransactionInput;
import com.leo.erp.inventory.api.InventoryTransactionType;
import com.leo.erp.inventory.repository.InventoryTransactionRepository;
import com.leo.erp.inventory.web.dto.InventoryBackfillResponse;
import com.leo.erp.purchase.api.PurchaseInboundPostedQuery;
import com.leo.erp.sales.api.SalesOutboundPostedQuery;
import com.leo.erp.sales.api.SalesReturnPostedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InventoryBackfillService 幂等差集、分批扫描、排序加锁与统计行为测试。
 *
 * <p>来源读取已从「直接依赖 purchase/sales 实体与仓储」收敛为三个 api 只读端口
 * （{@link PurchaseInboundPostedQuery} / {@link SalesOutboundPostedQuery} /
 * {@link SalesReturnPostedQuery}）；本测试锁定端口化后的行为与改造前一致：
 * 扫描状态集、500 条 keyset 分页、明细快照到记账输入的字段映射（含数量空值归一）、
 * 批次独立事务、全局加锁顺序与幂等跳过。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryBackfillServiceTest {

    private static final int SCAN_BATCH_SIZE = 500;

    @Mock
    private PurchaseInboundPostedQuery purchaseInboundPostedQuery;

    @Mock
    private SalesOutboundPostedQuery salesOutboundPostedQuery;

    @Mock
    private SalesReturnPostedQuery salesReturnPostedQuery;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @Mock
    private InventoryTransactionLockService lockService;

    @Mock
    private InventoryTransactionCommand inventoryCommand;

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private InventoryBackfillService service;

    /**
     * 回填改为「每批一个独立事务」后，需要让 TransactionTemplate 真正执行传入的回调，
     * 否则批次逻辑不会被调用。同时会话级互斥锁要返回 true（拿到锁）。
     */
    @BeforeEach
    void stubBatchTransactionAndBackfillLock() {
        when(transactionManager.getTransaction(any()))
                .thenReturn(mock(TransactionStatus.class));
        when(lockService.tryLockBackfill()).thenReturn(true);
    }

    /**
     * 并发回填保护：拿不到会话级锁时必须**快速失败**，不能排队等待。
     *
     * <p>原实现用事务级 advisory lock 且包在单个大事务里，第二个回填会一直等待、
     * 占着数据库连接，把回填的慢放大成全站连接池压力。改 try-lock 后语义是立即失败。</p>
     */
    @Test
    void backfill_shouldFailFastWhenAnotherBackfillHoldsTheLock() {
        when(lockService.tryLockBackfill()).thenReturn(false);

        assertThatThrownBy(() -> service.backfill())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有库存期初回填正在执行");

        // 未拿到锁就不应扫描任何来源，也不应尝试记账
        verify(purchaseInboundPostedQuery, never())
                .findPostedAfter(any(), any(), anyLong(), anyInt());
        verify(salesOutboundPostedQuery, never())
                .findPostedAfter(any(), any(), anyLong(), anyInt());
        verify(salesReturnPostedQuery, never())
                .findPostedAfter(any(), any(), anyLong(), anyInt());
        verify(inventoryCommand, never()).recordBackfill(any(), any());
        verify(lockService, never()).lockBackfillTxScoped();
    }

    /**
     * 每个扫描批次必须在**独立事务**中执行并提交：这是「不长事务、不长持锁」的核心。
     */
    @Test
    void backfill_shouldRunEachBatchInsideItsOwnTransaction() {
        when(purchaseInboundPostedQuery.findPostedAfter(any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(purchaseInbound(7L, LocalDate.of(2026, 9, 2), purchaseItem(17L, 100L, 5))))
                .thenReturn(List.of());
        when(salesOutboundPostedQuery.findPostedAfter(any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of());
        when(salesReturnPostedQuery.findPostedAfter(any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of());
        when(transactionRepository.findActiveSourceKeysBySourceItemIdIn(any())).thenReturn(List.of());

        service.backfill();

        // 每批次一个事务边界，事后再显式提交
        verify(transactionManager, atLeastOnce()).getTransaction(any());
        verify(transactionManager, atLeastOnce()).commit(any());
        // 收尾必须把会话级锁释放掉，否则下一次回填永远拿不到锁
        verify(lockService).unlockBackfill();
    }

    @Test
    void backfill_shouldSkipSourcesWithActiveTransactionAndNotRecord() {
        when(purchaseInboundPostedQuery.findPostedAfter(any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(purchaseInbound(5L, LocalDate.of(2026, 9, 1), purchaseItem(11L, 100L, 5))));
        when(transactionRepository.findActiveSourceKeysBySourceItemIdIn(any()))
                .thenReturn(List.of(activeKey("PURCHASE_INBOUND", 11L, "PURCHASE_IN")));

        InventoryBackfillResponse response = service.backfill();

        assertThat(response.purchaseInCreated()).isZero();
        assertThat(response.salesOutCreated()).isZero();
        assertThat(response.salesReturnCreated()).isZero();
        assertThat(response.skipped()).isEqualTo(1);
        verify(inventoryCommand, never()).recordBackfill(any(), any());
        verify(transactionRepository, never())
                .existsBySourceDocumentTypeAndSourceItemIdAndTransactionTypeAndDeletedFlagFalse(any(), any(), any());
    }

    @Test
    void backfill_shouldQueryOnlyPostedDocuments() {
        service.backfill();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> statusesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(purchaseInboundPostedQuery).findPostedAfter(
                statusesCaptor.capture(), any(), anyLong(), eq(SCAN_BATCH_SIZE));
        assertThat(statusesCaptor.getValue())
                .containsExactlyInAnyOrder(StatusConstants.AUDITED, StatusConstants.INBOUND_COMPLETED);

        verify(salesOutboundPostedQuery).findPostedAfter(
                eq(StatusConstants.AUDITED), any(), anyLong(), eq(SCAN_BATCH_SIZE));
        verify(salesReturnPostedQuery).findPostedAfter(
                eq(StatusConstants.AUDITED), any(), anyLong(), eq(SCAN_BATCH_SIZE));
        verify(lockService).tryLockBackfill();
    }

    @Test
    void backfill_shouldCreateMissingTransactionsAndReportCounts() {
        when(purchaseInboundPostedQuery.findPostedAfter(any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(purchaseInbound(5L, LocalDate.of(2026, 9, 1), purchaseItem(11L, 100L, 5))));
        when(salesOutboundPostedQuery.findPostedAfter(any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(salesOutbound(6L, LocalDate.of(2026, 9, 2),
                        salesOutboundItem(21L, 100L, 2), salesOutboundItem(22L, 101L, 3))));
        when(salesReturnPostedQuery.findPostedAfter(any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(salesReturn(7L, LocalDate.of(2026, 9, 3), salesReturnItem(31L, 100L, 1))));

        InventoryBackfillResponse response = service.backfill();

        assertThat(response.purchaseInCreated()).isEqualTo(1);
        assertThat(response.salesOutCreated()).isEqualTo(2);
        assertThat(response.salesReturnCreated()).isEqualTo(1);
        assertThat(response.skipped()).isZero();

        ArgumentCaptor<InventoryTransactionInput> captor = ArgumentCaptor.forClass(InventoryTransactionInput.class);
        verify(inventoryCommand).recordBackfill(captor.capture(), eq(InventoryTransactionType.PURCHASE_IN));
        assertThat(captor.getValue().sourceDocumentType()).isEqualTo("PURCHASE_INBOUND");
        assertThat(captor.getValue().sourceDocumentId()).isEqualTo(5L);
        assertThat(captor.getValue().sourceDocumentNo()).isEqualTo("PI5");
        assertThat(captor.getValue().defaultWarehouseId()).isEqualTo(9L);
        assertThat(captor.getValue().lines()).hasSize(1);
        assertThat(captor.getValue().lines().get(0).sourceItemId()).isEqualTo(11L);
        assertThat(captor.getValue().lines().get(0).materialId()).isEqualTo(100L);
        assertThat(captor.getValue().lines().get(0).quantity()).isEqualTo(5);
        verify(inventoryCommand).recordBackfill(any(), eq(InventoryTransactionType.SALES_OUT));
        verify(inventoryCommand).recordBackfill(any(), eq(InventoryTransactionType.SALES_RETURN_IN));
        verify(lockService).tryLockBackfill();
    }

    @Test
    void backfill_shouldProcessDocumentsInBusinessDateOrderAcrossSources() {
        when(purchaseInboundPostedQuery.findPostedAfter(any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(purchaseInbound(5L, LocalDate.of(2026, 9, 10), purchaseItem(11L, 100L, 5))));
        when(salesOutboundPostedQuery.findPostedAfter(any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(salesOutbound(6L, LocalDate.of(2026, 9, 1), salesOutboundItem(21L, 100L, 2))));

        service.backfill();

        InOrder order = inOrder(inventoryCommand);
        order.verify(inventoryCommand).recordBackfill(any(), eq(InventoryTransactionType.SALES_OUT));
        order.verify(inventoryCommand).recordBackfill(any(), eq(InventoryTransactionType.PURCHASE_IN));
    }

    @Test
    void backfill_shouldCountInvalidLinesAsSkipped() {
        when(purchaseInboundPostedQuery.findPostedAfter(any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(purchaseInbound(5L, LocalDate.of(2026, 9, 1),
                        purchaseItem(11L, 100L, 0), purchaseItem(12L, null, 5))));

        InventoryBackfillResponse response = service.backfill();

        assertThat(response.purchaseInCreated()).isZero();
        assertThat(response.skipped()).isEqualTo(2);
        verify(inventoryCommand, never()).recordBackfill(any(), any());
    }

    /**
     * 端口快照的数量可空，映射为记账输入时必须归一为 0（与改造前实体路径一致），
     * 否则会空指针或把空数量当成有效行。
     */
    @Test
    void backfill_shouldTreatNullQuantityAsZeroAndSkipTheLine() {
        when(purchaseInboundPostedQuery.findPostedAfter(any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(purchaseInbound(5L, LocalDate.of(2026, 9, 1), purchaseItem(11L, 100L, null))));

        InventoryBackfillResponse response = service.backfill();

        assertThat(response.purchaseInCreated()).isZero();
        assertThat(response.skipped()).isEqualTo(1);
        verify(inventoryCommand, never()).recordBackfill(any(), any());
    }

    @Test
    void backfill_shouldLockBatchDimensionsBeforeRecording() {
        when(purchaseInboundPostedQuery.findPostedAfter(any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(purchaseInbound(5L, LocalDate.of(2026, 9, 1),
                        purchaseItem(11L, 100L, 5), purchaseItem(12L, 50L, 2))));

        service.backfill();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<InventoryTransactionLockService.Dimension>> captor =
                ArgumentCaptor.forClass(Collection.class);
        verify(lockService).lockAll(captor.capture());
        assertThat(captor.getValue())
                .containsExactlyInAnyOrder(
                        new InventoryTransactionLockService.Dimension(100L, 9L),
                        new InventoryTransactionLockService.Dimension(50L, 9L));
        verify(lockService).tryLockBackfill();
        InOrder order = inOrder(lockService, inventoryCommand);
        order.verify(lockService).tryLockBackfill();
        order.verify(lockService).lockAll(any());
        order.verify(inventoryCommand).recordBackfill(any(), eq(InventoryTransactionType.PURCHASE_IN));
    }

    @Test
    void backfill_shouldScanSourcesInKeysetBatches() {
        List<PurchaseInboundPostedQuery.PostedInboundSnapshot> firstPage = new ArrayList<>();
        for (long i = 1; i <= SCAN_BATCH_SIZE; i++) {
            firstPage.add(purchaseInbound(i, LocalDate.of(2026, 9, 1), purchaseItem(1000L + i, 100L, (int) i)));
        }
        PurchaseInboundPostedQuery.PostedInboundSnapshot overflow =
                purchaseInbound(501L, LocalDate.of(2026, 9, 2), purchaseItem(2000L, 100L, 1));
        when(purchaseInboundPostedQuery.findPostedAfter(any(), any(), anyLong(), anyInt()))
                .thenReturn(firstPage)
                .thenReturn(List.of(overflow));

        InventoryBackfillResponse response = service.backfill();

        verify(purchaseInboundPostedQuery, times(2))
                .findPostedAfter(any(), any(), anyLong(), anyInt());
        assertThat(response.purchaseInCreated()).isEqualTo(501);
    }

    private InventoryTransactionRepository.ActiveSourceKey activeKey(String sourceDocumentType,
                                                                     Long sourceItemId,
                                                                     String transactionType) {
        return new InventoryTransactionRepository.ActiveSourceKey() {
            @Override
            public String getSourceDocumentType() {
                return sourceDocumentType;
            }

            @Override
            public Long getSourceItemId() {
                return sourceItemId;
            }

            @Override
            public String getTransactionType() {
                return transactionType;
            }
        };
    }

    private PurchaseInboundPostedQuery.PostedInboundSnapshot purchaseInbound(
            long id,
            LocalDate date,
            PurchaseInboundPostedQuery.LineSnapshot... lines) {
        return new PurchaseInboundPostedQuery.PostedInboundSnapshot(
                id, "PI" + id, date, 9L, "库房A", List.of(lines));
    }

    private PurchaseInboundPostedQuery.LineSnapshot purchaseItem(long id, Long materialId, Integer quantity) {
        return new PurchaseInboundPostedQuery.LineSnapshot(
                id, materialId, materialId == null ? null : "M" + materialId,
                9L, "库房A", null, quantity, "件", new BigDecimal("100.00"));
    }

    private SalesOutboundPostedQuery.PostedOutboundSnapshot salesOutbound(
            long id,
            LocalDate date,
            SalesOutboundPostedQuery.LineSnapshot... lines) {
        return new SalesOutboundPostedQuery.PostedOutboundSnapshot(
                id, "SO" + id, date, 9L, "库房A", List.of(lines));
    }

    private SalesOutboundPostedQuery.LineSnapshot salesOutboundItem(long id, Long materialId, Integer quantity) {
        return new SalesOutboundPostedQuery.LineSnapshot(
                id, materialId, "M" + materialId,
                9L, "库房A", null, quantity, "件", new BigDecimal("120.00"));
    }

    private SalesReturnPostedQuery.PostedReturnSnapshot salesReturn(
            long id,
            LocalDate date,
            SalesReturnPostedQuery.LineSnapshot... lines) {
        return new SalesReturnPostedQuery.PostedReturnSnapshot(
                id, "SR" + id, date, 9L, "库房A", List.of(lines));
    }

    private SalesReturnPostedQuery.LineSnapshot salesReturnItem(long id, Long materialId, Integer quantity) {
        return new SalesReturnPostedQuery.LineSnapshot(
                id, materialId, "M" + materialId,
                9L, "库房A", null, quantity, "件", new BigDecimal("150.00"));
    }
}
