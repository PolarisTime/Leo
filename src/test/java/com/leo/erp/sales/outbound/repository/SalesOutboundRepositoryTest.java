package com.leo.erp.sales.outbound.repository;

import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOutboundRepositoryTest {

    @Mock
    private SalesOutboundRepository repository;

    @Test
    void existsByOutboundNoAndDeletedFlagFalse_shouldReturnTrueWhenExists() {
        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO001")).thenReturn(true);

        boolean result = repository.existsByOutboundNoAndDeletedFlagFalse("SOO001");

        assertThat(result).isTrue();
    }

    @Test
    void existsByOutboundNoAndDeletedFlagFalse_shouldReturnFalseWhenNotExists() {
        when(repository.existsByOutboundNoAndDeletedFlagFalse("NONEXIST")).thenReturn(false);

        boolean result = repository.existsByOutboundNoAndDeletedFlagFalse("NONEXIST");

        assertThat(result).isFalse();
    }

    @Test
    void existsByOutboundNoAndDeletedFlagFalse_shouldReturnFalseWhenDeleted() {
        when(repository.existsByOutboundNoAndDeletedFlagFalse("SOO002")).thenReturn(false);

        boolean result = repository.existsByOutboundNoAndDeletedFlagFalse("SOO002");

        assertThat(result).isFalse();
    }

    @Test
    void findByDeletedFlagFalse_shouldReturnNonDeletedOutbounds() {
        SalesOutbound ob1 = new SalesOutbound();
        ob1.setOutboundNo("SOO001");
        ob1.setDeletedFlag(false);

        SalesOutbound ob2 = new SalesOutbound();
        ob2.setOutboundNo("SOO002");
        ob2.setDeletedFlag(false);

        when(repository.findByDeletedFlagFalse()).thenReturn(List.of(ob1, ob2));

        List<SalesOutbound> result = repository.findByDeletedFlagFalse();

        assertThat(result).hasSize(2);
    }

    @Test
    void findAllByDeletedFlagFalse_shouldReturnNonDeletedWithItems() {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setOutboundNo("SOO001");
        outbound.setDeletedFlag(false);
        outbound.setItems(new ArrayList<>());

        SalesOutboundItem item = new SalesOutboundItem();
        item.setLineNo(1);
        item.setMaterialCode("M1");
        outbound.getItems().add(item);

        when(repository.findAllByDeletedFlagFalse()).thenReturn(List.of(outbound));

        List<SalesOutbound> result = repository.findAllByDeletedFlagFalse();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getItems()).hasSize(1);
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnWhenExists() {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(1L);
        outbound.setOutboundNo("SOO001");
        outbound.setDeletedFlag(false);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(outbound));

        Optional<SalesOutbound> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getOutboundNo()).isEqualTo("SOO001");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<SalesOutbound> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound_shouldReturnMatching() {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(1L);
        outbound.setOutboundNo("SOO001");
        outbound.setDeletedFlag(false);

        SalesOutboundItem item = new SalesOutboundItem();
        item.setSourceSalesOrderItemId(100L);
        item.setMaterialCode("M1");
        outbound.setItems(List.of(item));

        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(List.of(100L), null))
                .thenReturn(List.of(outbound));

        List<SalesOutbound> result = repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(
                List.of(100L), null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOutboundNo()).isEqualTo("SOO001");
    }

    @Test
    void findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound_shouldExcludeCurrent() {
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(List.of(100L), 1L))
                .thenReturn(List.of());

        List<SalesOutbound> result = repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(
                List.of(100L), 1L);

        assertThat(result).isEmpty();
    }

    @Test
    void findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound_shouldNotReturnDeleted() {
        when(repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(List.of(200L), null))
                .thenReturn(List.of());

        List<SalesOutbound> result = repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(
                List.of(200L), null);

        assertThat(result).isEmpty();
    }
}
