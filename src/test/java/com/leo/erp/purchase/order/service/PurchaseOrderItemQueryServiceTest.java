package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PurchaseOrderItemQueryService 极端情况测试：null/空短路、透传委托、取首语义、NOT_FOUND 守卫。
 */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderItemQueryServiceTest {

    @Mock
    private PurchaseOrderItemRepository repository;

    @InjectMocks
    private PurchaseOrderItemQueryService service;

    // ---------- findActiveByIdIn ----------

    @Test
    void findActiveByIdIn_shouldReturnEmptyWhenNull() {
        assertThat(service.findActiveByIdIn(null)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void findActiveByIdIn_shouldReturnEmptyWhenEmpty() {
        assertThat(service.findActiveByIdIn(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void findActiveByIdIn_shouldDelegateToRepository() {
        PurchaseOrderItem item = new PurchaseOrderItem();
        when(repository.findActiveByIdIn(List.of(1L, 2L))).thenReturn(List.of(item));

        assertThat(service.findActiveByIdIn(List.of(1L, 2L))).containsExactly(item);
    }

    // ---------- findSnapshotsByIdIn ----------

    @Test
    void findSnapshotsByIdIn_shouldReturnEmptyWhenNull() {
        assertThat(service.findSnapshotsByIdIn(null)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void findSnapshotsByIdIn_shouldReturnEmptyWhenEmpty() {
        assertThat(service.findSnapshotsByIdIn(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void findSnapshotsByIdIn_shouldDelegateToRepository() {
        PurchaseOrderItem item = new PurchaseOrderItem();
        when(repository.findSnapshotsByIdIn(List.of(1L))).thenReturn(List.of(item));

        assertThat(service.findSnapshotsByIdIn(List.of(1L))).containsExactly(item);
    }

    // ---------- requireActiveById ----------

    @Test
    void requireActiveById_shouldReturnFirstWhenPresent() {
        PurchaseOrderItem first = new PurchaseOrderItem();
        PurchaseOrderItem second = new PurchaseOrderItem();
        when(repository.findActiveByIdIn(List.of(1L))).thenReturn(List.of(first, second));

        assertThat(service.requireActiveById(1L)).isSameAs(first);
    }

    @Test
    void requireActiveById_shouldThrowNotFoundWhenMissing() {
        when(repository.findActiveByIdIn(List.of(1L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.requireActiveById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购订单明细不存在")
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
}
