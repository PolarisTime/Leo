package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.web.OptionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.time.Duration;
import java.util.List;
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
    void shouldResolveWarehouseByStableIdAndRejectNameConflict() {
        WarehouseCatalog catalog = mock(WarehouseCatalog.class);
        when(catalog.listActiveWarehouseNames()).thenReturn(List.of("一号库"));
        when(catalog.listActiveWarehouses()).thenReturn(List.of(
                new WarehouseSnapshot(201L, "WH-001", "一号库")
        ));
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(catalog);

        assertThat(support.resolveWarehouse(201L, "一号库", 1, true).warehouseId()).isEqualTo(201L);
        assertThatThrownBy(() -> support.resolveWarehouse(201L, "二号库", 1, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仓库ID与名称不一致");
    }

    @Test
    void shouldKeepSameNameWarehousesSeparateByStableId() {
        WarehouseCatalog catalog = mock(WarehouseCatalog.class);
        when(catalog.listActiveWarehouses()).thenReturn(List.of(
                new WarehouseSnapshot(201L, "WH-001", "同名库"),
                new WarehouseSnapshot(202L, "WH-002", "同名库")
        ));
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(catalog);

        assertThat(support.resolveWarehouse(201L, "同名库", 1, true).warehouseId()).isEqualTo(201L);
        assertThat(support.resolveWarehouse(202L, "同名库", 1, true).warehouseId()).isEqualTo(202L);
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
    void evictCacheShouldDeleteAfterCommitWhenRedisPresent() {
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of()), cache);

        support.evictCache();

        verify(cache).deleteAfterCommit("leo:warehouse:all");
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
    void shouldLoadCatalogNamesWhenCacheSupportIsProvided() {
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of("一号库")), cache);

        List<OptionResponse> options = support.listActiveOptions();

        assertThat(options).singleElement().satisfies(option -> {
            assertThat(option.label()).isEqualTo("一号库");
            assertThat(option.value()).isEqualTo("一号库");
        });
        verify(cache, never()).write(anyString(), any(), any(Duration.class));
    }

    @Test
    void listActiveOptionsShouldUseCatalogNamesWhenCacheSupportIsProvided() {
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        WarehouseCatalog catalog = mock(WarehouseCatalog.class);
        when(catalog.listActiveWarehouseNames()).thenReturn(Arrays.asList(" 一号库 ", "", null));
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(catalog, cache);

        List<OptionResponse> options = support.listActiveOptions();

        assertThat(options).singleElement().satisfies(option -> {
            assertThat(option.label()).isEqualTo("一号库");
            assertThat(option.value()).isEqualTo("一号库");
        });
        verify(catalog).listActiveWarehouseNames();
        verify(cache, never()).write(anyString(), any(), any(Duration.class));
    }

    @Test
    void listActiveOptionsShouldReturnEmptyWhenCatalogIsEmpty() {
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of()), cache);

        List<OptionResponse> options = support.listActiveOptions();

        assertThat(options).isEmpty();
        verify(cache, never()).write(anyString(), any(), any(Duration.class));
    }

    @Test
    void shouldRefreshWarehouseCacheDuringHealthCheck_whenCachedSizeDiffersFromCatalog() {
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of("一号库")), cache);

        var result = support.verifyAndRefreshCache();

        assertThat(result.cacheName()).isEqualTo("leo:warehouse:all");
        assertThat(result.currentSize()).isZero();
        assertThat(result.refreshedSize()).isEqualTo(1);
        assertThat(result.refreshed()).isTrue();
        verify(cache).write(eq("leo:warehouse:all"), eq(List.of("一号库")), any(Duration.class));
    }

    @Test
    void shouldRefreshWarehouseCacheDuringHealthCheck_whenCatalogContentChanged() {
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        when(cache.read(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Optional.of(List.of("旧仓库")));
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of("新仓库")), cache);

        var result = support.verifyAndRefreshCache();

        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.refreshedSize()).isEqualTo(1);
        assertThat(result.refreshed()).isTrue();
        verify(cache).write(eq("leo:warehouse:all"), eq(List.of("新仓库")), any(Duration.class));
    }

    @Test
    void shouldDeleteStaleWarehouseCacheDuringHealthCheck_whenCatalogHasNoActiveNames() {
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        when(cache.read(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Optional.of(List.of("旧仓库")));
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of()), cache);

        var result = support.verifyAndRefreshCache();

        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.refreshedSize()).isZero();
        assertThat(result.refreshed()).isTrue();
        verify(cache).delete("leo:warehouse:all");
        verify(cache, never()).write(anyString(), any(), any(Duration.class));
    }

    @Test
    void verifyAndRefreshCacheShouldReturnEmptyWhenCatalogIsNullAndNoRedis() {
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(null);

        var result = support.verifyAndRefreshCache();

        assertThat(result.cacheName()).isEqualTo("leo:warehouse:all");
        assertThat(result.currentSize()).isZero();
        assertThat(result.refreshedSize()).isZero();
        assertThat(result.refreshed()).isFalse();
    }

    @Test
    void verifyAndRefreshCacheShouldReturnEmptyWhenCatalogIsNullAndRedisHasNoValue() {
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        when(cache.read(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Optional.empty());
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(null, cache);

        var result = support.verifyAndRefreshCache();

        assertThat(result.cacheName()).isEqualTo("leo:warehouse:all");
        assertThat(result.currentSize()).isZero();
        assertThat(result.refreshedSize()).isZero();
        assertThat(result.refreshed()).isFalse();
        verify(cache, never()).delete(anyString());
        verify(cache, never()).write(anyString(), any(), any(Duration.class));
    }

    @Test
    void cacheNameShouldReturnWarehouseCacheKey() {
        WarehouseSelectionSupport support = new WarehouseSelectionSupport(repository(List.of()));

        assertThat(support.cacheName()).isEqualTo("leo:warehouse:all");
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
