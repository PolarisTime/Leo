package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import com.leo.erp.master.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WarehouseSelectionSupportTest {

    @Test
    void shouldRejectMissingWarehouseSelection() {
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of()));

        assertThatThrownBy(() -> support.normalizeWarehouseName("三码头", 1, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("码头不存在");
    }

    @Test
    void shouldAcceptConfiguredWarehouseSelection() {
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of(warehouse("一号库"))));

        String warehouseName = support.normalizeWarehouseName(" 一号库 ", 1, true);

        assertThat(warehouseName).isEqualTo("一号库");
    }

    @SuppressWarnings("unchecked")
    private WarehouseRepository repository(List<Warehouse> warehouses) {
        return (WarehouseRepository) Proxy.newProxyInstance(
                WarehouseRepository.class.getClassLoader(),
                new Class[]{WarehouseRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByDeletedFlagFalseOrderByWarehouseNameAsc" -> warehouses;
                    case "toString" -> "WarehouseRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private Warehouse warehouse(String warehouseName) {
        Warehouse warehouse = new Warehouse();
        warehouse.setWarehouseName(warehouseName);
        return warehouse;
    }
}
