package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PurchaseInboundItemQueryService 极端情况测试：null/空短路、透传委托、汇总映射（含 BigDecimal 精度）。
 * <p>
 * 汇总行以私有 record 实现仓库嵌套接口，覆盖 Collectors.toMap 合并路径。
 */
@ExtendWith(MockitoExtension.class)
class PurchaseInboundItemQueryServiceTest {

    @Mock
    private PurchaseInboundItemRepository repository;

    @InjectMocks
    private PurchaseInboundItemQueryService service;

    /** 分配数量汇总行实现。 */
    private record AllocationSummary(
            Long sourcePurchaseOrderItemId,
            Long totalQuantity
    ) implements PurchaseInboundItemRepository.PurchaseOrderAllocationSummary {

        @Override
        public Long getSourcePurchaseOrderItemId() {
            return sourcePurchaseOrderItemId;
        }

        @Override
        public Long getTotalQuantity() {
            return totalQuantity;
        }
    }

    /** 重量调整汇总行实现。 */
    private record WeightAdjustmentSummary(
            Long sourcePurchaseOrderItemId,
            BigDecimal totalWeightAdjustmentTon
    ) implements PurchaseInboundItemRepository.PurchaseOrderWeightAdjustmentSummary {

        @Override
        public Long getSourcePurchaseOrderItemId() {
            return sourcePurchaseOrderItemId;
        }

        @Override
        public BigDecimal getTotalWeightAdjustmentTon() {
            return totalWeightAdjustmentTon;
        }
    }

    // ---------- findAllActiveByIdIn ----------

    @Test
    void findAllActiveByIdIn_shouldReturnEmptyWhenNull() {
        assertThat(service.findAllActiveByIdIn(null)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void findAllActiveByIdIn_shouldReturnEmptyWhenEmpty() {
        assertThat(service.findAllActiveByIdIn(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void findAllActiveByIdIn_shouldDelegateToRepository() {
        PurchaseInboundItem item = new PurchaseInboundItem();
        when(repository.findAllActiveByIdIn(List.of(1L))).thenReturn(List.of(item));

        assertThat(service.findAllActiveByIdIn(List.of(1L))).containsExactly(item);
    }

    // ---------- requireActiveById ----------

    @Test
    void requireActiveById_shouldReturnFirstWhenPresent() {
        PurchaseInboundItem first = new PurchaseInboundItem();
        PurchaseInboundItem second = new PurchaseInboundItem();
        when(repository.findAllActiveByIdIn(List.of(1L))).thenReturn(List.of(first, second));

        assertThat(service.requireActiveById(1L)).isSameAs(first);
    }

    @Test
    void requireActiveById_shouldThrowNotFoundWhenMissing() {
        when(repository.findAllActiveByIdIn(List.of(1L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.requireActiveById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购入库明细不存在")
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    // 防御缺口：requireActiveById(null) 在 Service 内部 List.of(null) 处 NPE（先于仓库调用）。
    // 测试锁定行为，不修改生产代码。
    @Test
    void requireActiveById_shouldThrowNpeWhenNull() {
        assertThatThrownBy(() -> service.requireActiveById(null)).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(repository);
    }

    // ---------- findAllActiveBySourcePurchaseOrderItemIds ----------

    @Test
    void findAllActiveBySourcePurchaseOrderItemIds_shouldReturnEmptyWhenNull() {
        assertThat(service.findAllActiveBySourcePurchaseOrderItemIds(null)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void findAllActiveBySourcePurchaseOrderItemIds_shouldReturnEmptyWhenEmpty() {
        assertThat(service.findAllActiveBySourcePurchaseOrderItemIds(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void findAllActiveBySourcePurchaseOrderItemIds_shouldDelegateToRepository() {
        PurchaseInboundItem item = new PurchaseInboundItem();
        when(repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(10L)))
                .thenReturn(List.of(item));

        assertThat(service.findAllActiveBySourcePurchaseOrderItemIds(List.of(10L))).containsExactly(item);
    }

    // ---------- summarizeAllocatedQuantityBySourcePurchaseOrderItemIds ----------

    @Test
    void summarizeAllocatedQuantityBySourcePurchaseOrderItemIds_shouldReturnEmptyWhenNull() {
        assertThat(service.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(null)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void summarizeAllocatedQuantityBySourcePurchaseOrderItemIds_shouldReturnEmptyWhenEmpty() {
        assertThat(service.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void summarizeAllocatedQuantityBySourcePurchaseOrderItemIds_shouldMapSummaries() {
        when(repository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(1L, 2L)))
                .thenReturn(List.of(
                        new AllocationSummary(1L, 100L),
                        new AllocationSummary(2L, 250L)
                ));

        Map<Long, Long> result = service.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(1L, 2L));

        assertThat(result).containsEntry(1L, 100L).containsEntry(2L, 250L).hasSize(2);
    }

    // ---------- summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound ----------

    @Test
    void summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound_shouldReturnEmptyWhenNull() {
        assertThat(service.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(null, 5L))
                .isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound_shouldReturnEmptyWhenEmpty() {
        assertThat(service.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(List.of(), 5L))
                .isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound_shouldDelegateWithCurrentInboundId() {
        when(repository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                List.of(1L), 5L
        )).thenReturn(List.of(new AllocationSummary(1L, 30L)));

        Map<Long, Long> result =
                service.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(List.of(1L), 5L);

        assertThat(result).containsEntry(1L, 30L);
        verify(repository).summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(1L)), eq(5L)
        );
    }

    // ---------- summarizeWeightAdjustmentBySourcePurchaseOrderItemIds ----------

    @Test
    void summarizeWeightAdjustmentBySourcePurchaseOrderItemIds_shouldReturnEmptyWhenNull() {
        assertThat(service.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(null)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void summarizeWeightAdjustmentBySourcePurchaseOrderItemIds_shouldReturnEmptyWhenEmpty() {
        assertThat(service.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void summarizeWeightAdjustmentBySourcePurchaseOrderItemIds_shouldMapAndPassNullExcludedInbound() {
        when(repository.summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsExcludingInbound(List.of(1L), null))
                .thenReturn(List.of(new WeightAdjustmentSummary(1L, new BigDecimal("12.50"))));

        Map<Long, BigDecimal> result = service.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(List.of(1L));

        assertThat(result).containsOnlyKeys(1L);
        // BigDecimal equals 对 scale 敏感，用 isEqualByComparingTo 比较数值。
        assertThat(result.get(1L)).isEqualByComparingTo(new BigDecimal("12.5"));
        verify(repository).summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsExcludingInbound(any(), eq(null));
    }
}
