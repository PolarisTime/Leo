package com.leo.erp.purchase.order.repository;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderRepositoryTest {

    @Mock
    private PurchaseOrderRepository repository;

    @Test
    void existsByOrderNoAndDeletedFlagFalse_shouldReturnTrueWhenExists() {
        when(repository.existsByOrderNoAndDeletedFlagFalse("PO001")).thenReturn(true);

        boolean result = repository.existsByOrderNoAndDeletedFlagFalse("PO001");

        assertThat(result).isTrue();
    }

    @Test
    void existsByOrderNoAndDeletedFlagFalse_shouldReturnFalseWhenNotExists() {
        when(repository.existsByOrderNoAndDeletedFlagFalse("NONEXIST")).thenReturn(false);

        boolean result = repository.existsByOrderNoAndDeletedFlagFalse("NONEXIST");

        assertThat(result).isFalse();
    }

    @Test
    void existsByOrderNoAndDeletedFlagFalse_shouldReturnFalseWhenDeleted() {
        when(repository.existsByOrderNoAndDeletedFlagFalse("PO002")).thenReturn(false);

        boolean result = repository.existsByOrderNoAndDeletedFlagFalse("PO002");

        assertThat(result).isFalse();
    }

    @Test
    void findAllByDeletedFlagFalse_shouldReturnNonDeletedOrders() {
        PurchaseOrder order1 = new PurchaseOrder();
        order1.setOrderNo("PO001");
        PurchaseOrder order2 = new PurchaseOrder();
        order2.setOrderNo("PO002");
        when(repository.findAllByDeletedFlagFalse()).thenReturn(List.of(order1, order2));

        List<PurchaseOrder> result = repository.findAllByDeletedFlagFalse();

        assertThat(result).hasSize(2);
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnOrderWhenExistsAndNotDeleted() {
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo("PO001");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));

        Optional<PurchaseOrder> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getOrderNo()).isEqualTo("PO001");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<PurchaseOrder> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdInAndDeletedFlagFalse_shouldReturnMatchingOrders() {
        PurchaseOrder order1 = new PurchaseOrder();
        order1.setOrderNo("PO001");
        PurchaseOrder order2 = new PurchaseOrder();
        order2.setOrderNo("PO002");
        when(repository.findByIdInAndDeletedFlagFalse(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(order1, order2));

        List<PurchaseOrder> result = repository.findByIdInAndDeletedFlagFalse(List.of(1L, 2L, 3L));

        assertThat(result).hasSize(2);
    }
}