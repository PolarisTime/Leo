package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.web.OptionResponse;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
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
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of("一号库")));

        String warehouseName = support.normalizeWarehouseName(" 一号库 ", 1, true);

        assertThat(warehouseName).isEqualTo("一号库");
    }

    @Test
    void shouldReturnNullWhenNotRequiredAndNull() {
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of()));

        String result = support.normalizeWarehouseName(null, 1, false);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenNotRequiredAndBlank() {
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of()));

        String result = support.normalizeWarehouseName("  ", 1, false);

        assertThat(result).isNull();
    }

    @Test
    void shouldThrowWhenRequiredAndNull() {
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of()));

        assertThatThrownBy(() -> support.normalizeWarehouseName(null, 5, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第5行")
                .hasMessageContaining("不能为空");
    }

    @Test
    void shouldThrowWhenNameTooLong() {
        String longName = "A".repeat(129);
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of(longName)));

        assertThatThrownBy(() -> support.normalizeWarehouseName(longName, 1, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("长度不能超过128");
    }

    @Test
    void shouldNotThrowWhenValidateWithNullCollection() {
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of()));

        support.validateWarehouseNames(null);
    }

    @Test
    void shouldNotThrowWhenValidateWithEmptyCollection() {
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of()));

        support.validateWarehouseNames(List.of());
    }

    @Test
    void shouldNotThrowWhenValidateWithBlankNames() {
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of()));

        support.validateWarehouseNames(Arrays.asList("", "  ", null));
    }

    @Test
    void evictCacheShouldNoOpWhenNoRedis() {
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of()));

        support.evictCache();
    }

    @Test
    void listActiveOptionsShouldReturnOptions() {
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of("一号库", "二号库")));

        List<OptionResponse> options = support.listActiveOptions();

        assertThat(options).hasSize(2);
        assertThat(options).extracting(OptionResponse::label).containsExactlyInAnyOrder("一号库", "二号库");
    }

    @Test
    void listActiveOptionsShouldReturnEmptyWhenNoRepo() {
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(null);

        List<OptionResponse> options = support.listActiveOptions();

        assertThat(options).isEmpty();
    }

    @Test
    void shouldFilterBlankWarehouseNamesFromRepo() {
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(Arrays.asList("  ", "good", null)));

        List<OptionResponse> options = support.listActiveOptions();

        assertThat(options).hasSize(1);
        assertThat(options.get(0).label()).isEqualTo("good");
    }

    @Test
    void shouldThrowWhenValidatingNonExistentWarehouse() {
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of("一号库")));

        assertThatThrownBy(() -> support.validateWarehouseNames(List.of("不存在")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("码头不存在: 不存在");
    }

    private WarehouseCatalog repository(List<String> warehouseNames) {
        return () -> warehouseNames;
    }
}
