package com.leo.erp.purchase.order.repository;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderItemRepositoryTest {

    @Mock
    private PurchaseOrderItemRepository repository;

    @Test
    void findActiveByIdIn_shouldReturnItemsForActiveOrders() {
        PurchaseOrderItem item1 = new PurchaseOrderItem();
        item1.setMaterialCode("M001");
        item1.setQuantity(10);
        item1.setAmount(new BigDecimal("1000.00"));
        PurchaseOrderItem item2 = new PurchaseOrderItem();
        item2.setMaterialCode("M002");
        item2.setQuantity(20);
        item2.setAmount(new BigDecimal("2000.00"));
        when(repository.findActiveByIdIn(List.of(1L, 2L))).thenReturn(List.of(item1, item2));

        List<PurchaseOrderItem> result = repository.findActiveByIdIn(List.of(1L, 2L));

        assertThat(result).hasSize(2);
    }

    @Test
    void findActiveByIdIn_shouldNotReturnItemsForDeletedOrders() {
        when(repository.findActiveByIdIn(List.of(1L))).thenReturn(List.of());

        List<PurchaseOrderItem> result = repository.findActiveByIdIn(List.of(1L));

        assertThat(result).isEmpty();
    }

    @Test
    void findActiveByIdIn_shouldReturnEmptyWhenNoMatch() {
        when(repository.findActiveByIdIn(List.of(999L, 1000L))).thenReturn(List.of());

        List<PurchaseOrderItem> result = repository.findActiveByIdIn(List.of(999L, 1000L));

        assertThat(result).isEmpty();
    }
}