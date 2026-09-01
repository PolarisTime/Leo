package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WarehouseSelectionSupport 仓库快照解析回归测试。
 */
class WarehouseSelectionSupportTest {

    private final WarehouseCatalog catalog = mock(WarehouseCatalog.class);
    private final WarehouseSelectionSupport support = new WarehouseSelectionSupport(catalog);

    private void stubWarehouses(WarehouseSnapshot... warehouses) {
        when(catalog.listActiveWarehouses()).thenReturn(List.of(warehouses));
    }

    @Test
    void resolveWarehouse_shouldRejectBlankNameWhenRequired() {
        stubWarehouses();

        assertThatThrownBy(() -> support.resolveWarehouse(null, "  ", 1, true))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void resolveWarehouse_shouldReturnEmptySnapshotWhenBothMissingAndNotRequired() {
        stubWarehouses();

        WarehouseSnapshot snapshot = support.resolveWarehouse(null, null, 1, false);

        assertThat(snapshot.warehouseId()).isNull();
        assertThat(snapshot.warehouseName()).isNull();
    }

    @Test
    void resolveWarehouse_shouldResolveByIdAndKeepSnapshotName() {
        stubWarehouses(new WarehouseSnapshot(1L, "W001", "库房A"));

        WarehouseSnapshot snapshot = support.resolveWarehouse(1L, "库房A", 1, true);

        assertThat(snapshot.warehouseId()).isEqualTo(1L);
        assertThat(snapshot.warehouseName()).isEqualTo("库房A");
    }

    @Test
    void resolveWarehouse_shouldRejectIdNameMismatch() {
        stubWarehouses(new WarehouseSnapshot(1L, "W001", "库房A"));

        assertThatThrownBy(() -> support.resolveWarehouse(1L, "库房B", 1, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    void resolveWarehouse_shouldRejectUnknownId() {
        stubWarehouses(new WarehouseSnapshot(1L, "W001", "库房A"));

        assertThatThrownBy(() -> support.resolveWarehouse(99L, "库房X", 1, true))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    @Test
    void resolveWarehouse_shouldResolveUniqueNameAndTrim() {
        stubWarehouses(new WarehouseSnapshot(null, null, "库房A"));

        WarehouseSnapshot snapshot = support.resolveWarehouse(null, "  库房A  ", 1, true);

        assertThat(snapshot.warehouseName()).isEqualTo("库房A");
    }

    @Test
    void resolveWarehouse_shouldRejectMissingName() {
        stubWarehouses(new WarehouseSnapshot(null, null, "库房A"));

        assertThatThrownBy(() -> support.resolveWarehouse(null, "库房X", 1, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("码头不存在");
    }

    @Test
    void resolveWarehouse_shouldRejectAmbiguousName() {
        stubWarehouses(
                new WarehouseSnapshot(null, null, "库房A"),
                new WarehouseSnapshot(null, null, "库房A")
        );

        assertThatThrownBy(() -> support.resolveWarehouse(null, "库房A", 1, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("码头名称不唯一");
    }

    @Test
    void resolveWarehouse_shouldRejectNameLongerThan128() {
        stubWarehouses();

        assertThatThrownBy(() -> support.resolveWarehouse(null, "仓".repeat(129), 1, true))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void validateWarehouseNames_shouldAcceptAllConfigured() {
        when(catalog.listActiveWarehouseNames()).thenReturn(List.of("库房A", "库房B"));

        support.validateWarehouseNames(List.of("库房A", "库房B"));
    }

    @Test
    void validateWarehouseNames_shouldRejectMissingName() {
        when(catalog.listActiveWarehouseNames()).thenReturn(List.of("库房A"));

        assertThatThrownBy(() -> support.validateWarehouseNames(List.of("库房A", "库房X")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("码头不存在");
    }
}
