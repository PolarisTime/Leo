package com.leo.erp.sales.order.repository;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderRepositoryTest {

    @Mock
    private SalesOrderRepository repository;

    @Test
    void existsByOrderNoAndDeletedFlagFalse_shouldReturnTrueWhenExists() {
        when(repository.existsByOrderNoAndDeletedFlagFalse("SO001")).thenReturn(true);

        boolean result = repository.existsByOrderNoAndDeletedFlagFalse("SO001");

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
        when(repository.existsByOrderNoAndDeletedFlagFalse("SO002")).thenReturn(false);

        boolean result = repository.existsByOrderNoAndDeletedFlagFalse("SO002");

        assertThat(result).isFalse();
    }

    @Test
    void findByOrderNoInAndDeletedFlagFalse_shouldReturnMatchingOrders() {
        SalesOrder order1 = new SalesOrder();
        order1.setOrderNo("SO001");
        order1.setDeletedFlag(false);

        SalesOrder order2 = new SalesOrder();
        order2.setOrderNo("SO002");
        order2.setDeletedFlag(false);

        when(repository.findByOrderNoInAndDeletedFlagFalse(List.of("SO001", "SO002", "SO003")))
                .thenReturn(List.of(order1, order2));

        List<SalesOrder> result = repository.findByOrderNoInAndDeletedFlagFalse(List.of("SO001", "SO002", "SO003"));

        assertThat(result).hasSize(2);
    }

    @Test
    void findAllByDeletedFlagFalse_shouldReturnNonDeletedOrders() {
        SalesOrder order1 = new SalesOrder();
        order1.setOrderNo("SO001");
        order1.setDeletedFlag(false);

        SalesOrder order2 = new SalesOrder();
        order2.setOrderNo("SO002");
        order2.setDeletedFlag(false);

        when(repository.findAllByDeletedFlagFalse()).thenReturn(List.of(order1, order2));

        List<SalesOrder> result = repository.findAllByDeletedFlagFalse();

        assertThat(result).hasSize(2);
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnOrderWhenExistsAndNotDeleted() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO001");
        order.setDeletedFlag(false);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));

        Optional<SalesOrder> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getOrderNo()).isEqualTo("SO001");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<SalesOrder> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isEmpty();
    }
}
