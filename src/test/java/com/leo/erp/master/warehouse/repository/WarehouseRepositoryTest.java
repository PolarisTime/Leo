package com.leo.erp.master.warehouse.repository;

import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseRepositoryTest {

    @Mock
    private WarehouseRepository repository;

    @Test
    void existsByWarehouseCodeAndDeletedFlagFalse_shouldReturnTrueWhenExists() {
        when(repository.existsByWarehouseCodeAndDeletedFlagFalse("WH001")).thenReturn(true);

        boolean result = repository.existsByWarehouseCodeAndDeletedFlagFalse("WH001");

        assertThat(result).isTrue();
    }

    @Test
    void existsByWarehouseCodeAndDeletedFlagFalse_shouldReturnFalseWhenNotExists() {
        when(repository.existsByWarehouseCodeAndDeletedFlagFalse("NONEXIST")).thenReturn(false);

        boolean result = repository.existsByWarehouseCodeAndDeletedFlagFalse("NONEXIST");

        assertThat(result).isFalse();
    }

    @Test
    void findByWarehouseNameInAndDeletedFlagFalse_shouldReturnMatchingWarehouses() {
        Warehouse warehouse1 = new Warehouse();
        warehouse1.setId(1L);
        warehouse1.setWarehouseCode("WH001");
        warehouse1.setWarehouseName("仓库A");
        warehouse1.setDeletedFlag(false);

        Warehouse warehouse2 = new Warehouse();
        warehouse2.setId(2L);
        warehouse2.setWarehouseCode("WH002");
        warehouse2.setWarehouseName("仓库B");
        warehouse2.setDeletedFlag(false);

        when(repository.findByWarehouseNameInAndDeletedFlagFalse(List.of("仓库A", "仓库B", "仓库C")))
                .thenReturn(List.of(warehouse1, warehouse2));

        List<Warehouse> result = repository.findByWarehouseNameInAndDeletedFlagFalse(List.of("仓库A", "仓库B", "仓库C"));

        assertThat(result).hasSize(2);
    }

    @Test
    void findByDeletedFlagFalseOrderByWarehouseNameAsc_shouldReturnNonDeletedWarehouses() {
        Warehouse warehouse1 = new Warehouse();
        warehouse1.setId(1L);
        warehouse1.setWarehouseCode("WH001");
        warehouse1.setWarehouseName("仓库A");
        warehouse1.setDeletedFlag(false);

        Warehouse warehouse2 = new Warehouse();
        warehouse2.setId(2L);
        warehouse2.setWarehouseCode("WH002");
        warehouse2.setWarehouseName("仓库B");
        warehouse2.setDeletedFlag(false);

        when(repository.findByDeletedFlagFalseOrderByWarehouseNameAsc()).thenReturn(List.of(warehouse1, warehouse2));

        List<Warehouse> result = repository.findByDeletedFlagFalseOrderByWarehouseNameAsc();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getWarehouseName()).isEqualTo("仓库A");
        assertThat(result.get(1).getWarehouseName()).isEqualTo("仓库B");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnWarehouseWhenExistsAndNotDeleted() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);
        warehouse.setWarehouseCode("WH001");
        warehouse.setWarehouseName("测试仓库");
        warehouse.setDeletedFlag(false);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(warehouse));

        Optional<Warehouse> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getWarehouseCode()).isEqualTo("WH001");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<Warehouse> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isEmpty();
    }
}
