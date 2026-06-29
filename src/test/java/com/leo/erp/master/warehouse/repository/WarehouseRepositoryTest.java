package com.leo.erp.master.warehouse.repository;

import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
    void findByDeletedFlagFalseAndStatusOrderByWarehouseNameAsc_shouldReturnActiveWarehouses() {
        Warehouse warehouse1 = new Warehouse();
        warehouse1.setId(1L);
        warehouse1.setWarehouseCode("WH001");
        warehouse1.setWarehouseName("仓库A");
        warehouse1.setDeletedFlag(false);
        warehouse1.setStatus("正常");

        Warehouse warehouse2 = new Warehouse();
        warehouse2.setId(2L);
        warehouse2.setWarehouseCode("WH002");
        warehouse2.setWarehouseName("仓库B");
        warehouse2.setDeletedFlag(false);
        warehouse2.setStatus("正常");

        when(repository.findByDeletedFlagFalseAndStatusOrderByWarehouseNameAsc("正常")).thenReturn(List.of(warehouse1, warehouse2));

        List<Warehouse> result = repository.findByDeletedFlagFalseAndStatusOrderByWarehouseNameAsc("正常");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getWarehouseName()).isEqualTo("仓库A");
        assertThat(result.get(1).getWarehouseName()).isEqualTo("仓库B");
    }

    @Test
    void listActiveWarehouseNames_shouldQueryOnlyNormalWarehouses() {
        Warehouse activeWarehouse = new Warehouse();
        activeWarehouse.setWarehouseName(" 仓库A ");
        activeWarehouse.setStatus("正常");

        WarehouseRepository repo = warehouseRepositoryReturning(List.of(activeWarehouse));

        List<String> result = repo.listActiveWarehouseNames();

        assertThat(result).containsExactly("仓库A");
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

    private WarehouseRepository warehouseRepositoryReturning(List<Warehouse> activeWarehouses) {
        return (WarehouseRepository) Proxy.newProxyInstance(
                WarehouseRepository.class.getClassLoader(),
                new Class[]{WarehouseRepository.class},
                (proxy, method, args) -> {
                    if ("findByDeletedFlagFalseAndStatusOrderByWarehouseNameAsc".equals(method.getName())) {
                        assertThat(args).containsExactly("正常");
                        return activeWarehouses;
                    }
                    if (method.isDefault()) {
                        return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    if ("toString".equals(method.getName())) {
                        return "WarehouseRepositoryDefaultMethodStub";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
