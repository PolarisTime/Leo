package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.service.BusinessNumberAllocator;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void shouldNormalizeMaterialCode_whenLoadingMaterialMap() {
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(repository(List.of(batchManagedMaterial("MAT-001"))));

        Map<String, TradeMaterialSnapshot> materialMap = support.loadMaterialMap(List.of(" MAT-001 "));

        assertThat(materialMap).containsKey("MAT-001");
        assertThat(support.normalizeMaterialCode(" MAT-001 ", 1)).isEqualTo("MAT-001");
    }

    @Test
    void shouldReturnEmptyMapWhenMaterialCodesAreNullOrBlank() {
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(repository(List.of(batchManagedMaterial("MAT-001"))));

        assertThat(support.loadMaterialMap(null)).isEmpty();
        assertThat(support.loadMaterialMap(List.of(" ", "\t"))).isEmpty();
    }

    @Test
    void shouldRejectBlankMaterialCodeDuringNormalization() {
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(repository(List.of()));

        assertThatThrownBy(() -> support.normalizeMaterialCode(null, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第3行商品编码不能为空");
        assertThatThrownBy(() -> support.normalizeMaterialCode(" ", 4))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第4行商品编码不能为空");
    }

    @Test
    void shouldRequireBatchNoWhenMaterialBatchManaged() {
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(repository(List.of(batchManagedMaterial("MAT-001"))));

        assertThatThrownBy(() -> support.normalizeBatchNo(batchManagedMaterial("MAT-001"), " ", 1, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行当前商品需批号管理，批号不能为空");
    }

    @Test
    void shouldRejectBatchNoLongerThanSixtyFourCharacters() {
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(repository(List.of(batchManagedMaterial("MAT-001"))));

        assertThatThrownBy(() -> support.normalizeBatchNo(batchManagedMaterial("MAT-001"), "A".repeat(65), 2, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第2行批号长度不能超过64");
    }

    @Test
    void shouldNormalizeBatchNoEnabledFlag() {
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(repository(List.of()));

        assertThat(support.normalizeBatchNoEnabled(Boolean.TRUE)).isTrue();
        assertThat(support.normalizeBatchNoEnabled(Boolean.FALSE)).isFalse();
        assertThat(support.normalizeBatchNoEnabled(null)).isFalse();
    }

    @Test
    void shouldClearBatchNoWhenMaterialDoesNotManageBatch() {
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(repository(List.of(batchDisabledMaterial("MAT-001"))));

        String normalized = support.normalizeBatchNo(batchDisabledMaterial("MAT-001"), "BATCH-001", 1, true);

        assertThat(normalized).isNull();
    }

    @Test
    void shouldIgnoreCacheEvictionWhenRedisCacheIsUnavailable() {
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                repository(List.of(batchManagedMaterial("MAT-001"))),
                null,
                null,
                null
        );

        support.evictCache();
    }

    @Test
    void shouldEvictCacheAfterCommitWhenRedisCacheIsAvailable() {
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                repository(List.of(batchManagedMaterial("MAT-001"))),
                cache,
                null,
                null
        );

        support.evictCache();

        verify(cache).deleteAfterCommit("leo:material:all");
    }

    @Test
    void shouldRejectMaterialWhenCatalogIsUnavailable() {
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(null, null, null, null);

        assertThatThrownBy(() -> support.loadMaterialMap(List.of("MAT-001")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品不存在: MAT-001");
    }

    @Test
    void shouldRejectMaterialWhenCacheAndCatalogAreBothEmpty() {
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        when(cache.getOrLoad(anyString(), any(Duration.class), any(TypeReference.class), any(Supplier.class)))
                .thenReturn(List.of());
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                repository(List.of()),
                cache,
                null,
                null
        );

        assertThatThrownBy(() -> support.loadMaterialMap(List.of("MAT-001")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品不存在: MAT-001");
        verify(cache, never()).write(anyString(), any(), any(Duration.class));
    }

    @Test
    void shouldRefreshCache_whenCachedMaterialsAreEmptyButCatalogHasActiveMaterials() {
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        when(cache.getOrLoad(anyString(), any(Duration.class), any(TypeReference.class), any(Supplier.class)))
                .thenReturn(List.of());
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                repository(List.of(batchManagedMaterial("MAT-001"))),
                cache,
                null,
                null
        );

        Map<String, TradeMaterialSnapshot> materialMap = support.loadMaterialMap(List.of("MAT-001"));

        assertThat(materialMap).containsKey("MAT-001");
        verify(cache, never()).delete(anyString());
        verify(cache).write(eq("leo:material:all"), eq(List.of(batchManagedMaterial("MAT-001"))), any(Duration.class));
    }

    @Test
    void shouldUseCachedMaterialsAndIgnoreBlankCodes() {
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        when(cache.getOrLoad(anyString(), any(Duration.class), any(TypeReference.class), any(Supplier.class)))
                .thenReturn(List.of(new TradeMaterialSnapshot(" ", Boolean.TRUE), batchManagedMaterial("MAT-001")));
        MaterialCatalog catalog = mock(MaterialCatalog.class);
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                catalog,
                cache,
                null,
                null
        );

        Map<String, TradeMaterialSnapshot> materialMap = support.loadMaterialMap(List.of("MAT-001"));

        assertThat(materialMap).containsOnlyKeys("MAT-001");
        verify(catalog, never()).listActiveMaterials();
        verify(cache, never()).write(anyString(), any(), any(Duration.class));
    }

    @Test
    void shouldRefreshMaterialCacheDuringHealthCheck_whenCachedContentDiffers() {
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                repository(List.of(batchManagedMaterial("MAT-001"))),
                cache,
                null,
                null
        );

        var result = support.verifyAndRefreshCache();

        assertThat(result.cacheName()).isEqualTo("leo:material:all");
        assertThat(result.currentSize()).isZero();
        assertThat(result.refreshedSize()).isEqualTo(1);
        assertThat(result.refreshed()).isTrue();
        verify(cache).write(eq("leo:material:all"), eq(List.of(batchManagedMaterial("MAT-001"))), any(Duration.class));
    }

    @Test
    void shouldRefreshMaterialCacheDuringHealthCheck_whenCatalogContentChanged() {
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        when(cache.read(anyString(), any(TypeReference.class)))
                .thenReturn(Optional.of(List.of(batchDisabledMaterial("MAT-001"))));
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                repository(List.of(batchManagedMaterial("MAT-001"))),
                cache,
                null,
                null
        );

        var result = support.verifyAndRefreshCache();

        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.refreshedSize()).isEqualTo(1);
        assertThat(result.refreshed()).isTrue();
        verify(cache).write(eq("leo:material:all"), eq(List.of(batchManagedMaterial("MAT-001"))), any(Duration.class));
    }

    @Test
    void shouldDeleteStaleMaterialCacheDuringHealthCheck_whenCatalogHasNoActiveMaterials() {
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        when(cache.read(anyString(), any(TypeReference.class)))
                .thenReturn(Optional.of(List.of(batchManagedMaterial("MAT-001"))));
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                repository(List.of()),
                cache,
                null,
                null
        );

        var result = support.verifyAndRefreshCache();

        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.refreshedSize()).isZero();
        assertThat(result.refreshed()).isTrue();
        verify(cache).delete("leo:material:all");
        verify(cache, never()).write(anyString(), any(), any(Duration.class));
    }

    @Test
    void shouldReportHealthyCacheWhenRedisCacheIsUnavailable() {
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                repository(List.of(batchManagedMaterial("MAT-001"))),
                null,
                null,
                null
        );

        var result = support.verifyAndRefreshCache();

        assertThat(support.cacheName()).isEqualTo("leo:material:all");
        assertThat(result.cacheName()).isEqualTo("leo:material:all");
        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.refreshedSize()).isEqualTo(1);
        assertThat(result.refreshed()).isFalse();
    }

    @Test
    void shouldReportEmptyHealthyCacheWhenCatalogIsUnavailable() {
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        when(cache.read(anyString(), any(TypeReference.class))).thenReturn(Optional.empty());
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(null, cache, null, null);

        var result = support.verifyAndRefreshCache();

        assertThat(result.currentSize()).isZero();
        assertThat(result.refreshedSize()).isZero();
        assertThat(result.refreshed()).isFalse();
        verify(cache, never()).delete(anyString());
        verify(cache, never()).write(anyString(), any(), any(Duration.class));
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
    void shouldNotForceBatchManagementWhenSwitchDisabled() {
        TradeItemRuntimeSettings tradeItemRuntimeSettings = mock(TradeItemRuntimeSettings.class);
        when(tradeItemRuntimeSettings.shouldForceBatchManagement()).thenReturn(false);
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                repository(List.of(batchDisabledMaterial("MAT-001"))),
                null,
                tradeItemRuntimeSettings,
                null
        );

        String normalized = support.normalizeBatchNo(batchDisabledMaterial("MAT-001"), "LOT-001", 1, true);

        assertThat(normalized).isNull();
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

    @Test
    void shouldNotAutoGenerateOptionalMissingBatchNoWhenSwitchDisabled() {
        TradeItemRuntimeSettings tradeItemRuntimeSettings = mock(TradeItemRuntimeSettings.class);
        BusinessNumberAllocator businessNumberAllocator = mock(BusinessNumberAllocator.class);
        when(tradeItemRuntimeSettings.shouldAutoGenerateBatchNo()).thenReturn(false);
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                repository(List.of(batchManagedMaterial("MAT-001"))),
                null,
                tradeItemRuntimeSettings,
                businessNumberAllocator
        );

        String normalized = support.normalizeBatchNo(batchManagedMaterial("MAT-001"), null, 1, false);

        assertThat(normalized).isNull();
    }

    @Test
    void writeMaterialCacheDoesNothingWhenRedisCacheUnavailable() throws Exception {
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                repository(List.of(batchManagedMaterial("MAT-001"))),
                null,
                null,
                null
        );
        Method method = TradeItemMaterialSupport.class.getDeclaredMethod("writeMaterialCache", List.class);
        method.setAccessible(true);

        method.invoke(support, List.of(batchManagedMaterial("MAT-001")));
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
