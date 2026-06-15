package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.service.BusinessNumberAllocator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeItemMaterialSupportTest {

    @Test
    void shouldRejectMissingMaterialCode() {
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(repository(List.of()));

        assertThatThrownBy(() -> support.loadMaterialMap(List.of("MISSING")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品不存在");
    }

    @Test
    void shouldRequireBatchNoWhenMaterialBatchManaged() {
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(repository(List.of(batchManagedMaterial("MAT-001"))));

        assertThatThrownBy(() -> support.normalizeBatchNo(batchManagedMaterial("MAT-001"), " ", 1, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行当前商品需批号管理，批号不能为空");
    }

    @Test
    void shouldClearBatchNoWhenMaterialDoesNotManageBatch() {
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(repository(List.of(batchDisabledMaterial("MAT-001"))));

        String normalized = support.normalizeBatchNo(batchDisabledMaterial("MAT-001"), "BATCH-001", 1, true);

        assertThat(normalized).isNull();
    }

    @Test
    void shouldTreatMaterialAsBatchManagedWhenForceSwitchEnabled() {
        TradeItemRuntimeSettings tradeItemRuntimeSettings = mock(TradeItemRuntimeSettings.class);
        when(tradeItemRuntimeSettings.shouldForceBatchManagement()).thenReturn(true);
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                repository(List.of(batchDisabledMaterial("MAT-001"))),
                null,
                tradeItemRuntimeSettings,
                null
        );

        String normalized = support.normalizeBatchNo(batchDisabledMaterial("MAT-001"), " FORCE-001 ", 1, true);

        assertThat(normalized).isEqualTo("FORCE-001");
    }

    @Test
    void shouldRequireBatchNoWhenForceSwitchEnabled() {
        TradeItemRuntimeSettings tradeItemRuntimeSettings = mock(TradeItemRuntimeSettings.class);
        when(tradeItemRuntimeSettings.shouldForceBatchManagement()).thenReturn(true);
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                repository(List.of(batchDisabledMaterial("MAT-001"))),
                null,
                tradeItemRuntimeSettings,
                null
        );

        assertThatThrownBy(() -> support.normalizeBatchNo(batchDisabledMaterial("MAT-001"), " ", 1, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行当前商品需批号管理，批号不能为空");
    }

    @Test
    void shouldAutoGenerateBatchNoWhenSwitchEnabled() throws Exception {
        java.lang.reflect.Field instanceField = SnowflakeIdGenerator.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, new SnowflakeIdGenerator(1L));
        TradeItemRuntimeSettings tradeItemRuntimeSettings = mock(TradeItemRuntimeSettings.class);
        BusinessNumberAllocator businessNumberAllocator = mock(BusinessNumberAllocator.class);
        when(tradeItemRuntimeSettings.shouldAutoGenerateBatchNo()).thenReturn(true);
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                repository(List.of(batchManagedMaterial("MAT-001"))),
                null,
                tradeItemRuntimeSettings,
                businessNumberAllocator
        );

        String normalized = support.normalizeBatchNo(batchManagedMaterial("MAT-001"), " ", 1, true);

        assertThat(normalized).isNotNull();
    }

    @Test
    void shouldKeepManualBatchNoWhenSwitchEnabled() {
        TradeItemRuntimeSettings tradeItemRuntimeSettings = mock(TradeItemRuntimeSettings.class);
        BusinessNumberAllocator businessNumberAllocator = mock(BusinessNumberAllocator.class);
        when(tradeItemRuntimeSettings.shouldAutoGenerateBatchNo()).thenReturn(true);
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                repository(List.of(batchManagedMaterial("MAT-001"))),
                null,
                tradeItemRuntimeSettings,
                businessNumberAllocator
        );

        String normalized = support.normalizeBatchNo(batchManagedMaterial("MAT-001"), " LOT-001 ", 1, true);

        assertThat(normalized).isEqualTo("LOT-001");
    }

    private TradeMaterialSnapshot batchManagedMaterial(String materialCode) {
        return new TradeMaterialSnapshot(materialCode, Boolean.TRUE);
    }

    private TradeMaterialSnapshot batchDisabledMaterial(String materialCode) {
        return new TradeMaterialSnapshot(materialCode, Boolean.FALSE);
    }

    private MaterialCatalog repository(List<TradeMaterialSnapshot> materials) {
        return () -> materials;
    }
}
