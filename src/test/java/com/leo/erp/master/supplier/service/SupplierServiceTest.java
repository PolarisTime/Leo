package com.leo.erp.master.supplier.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.mapper.SupplierMapper;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.master.supplier.web.dto.SupplierOptionResponse;
import com.leo.erp.master.supplier.web.dto.SupplierRequest;
import com.leo.erp.master.supplier.web.dto.SupplierResponse;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierServiceTest {

    @Test
    void shouldReturnPage_whenCallingPage() {
        var repository = (SupplierRepository) Proxy.newProxyInstance(
                SupplierRepository.class.getClassLoader(),
                new Class[]{SupplierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> org.springframework.data.domain.Page.empty();
                    case "toString" -> "SupplierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new SupplierService(repository, new SnowflakeIdGenerator(1), null);

        var result = service.page(new PageQuery(0, 10, "id", "desc"), null, null);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldLoadOptionsThroughSpringCachePath_whenLegacyRedisCachePresent() {
        var repository = (SupplierRepository) Proxy.newProxyInstance(
                SupplierRepository.class.getClassLoader(),
                new Class[]{SupplierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByDeletedFlagFalseAndStatusOrderBySupplierCodeAsc" -> List.of(createSupplier(1L, "S001"));
                    case "toString" -> "SupplierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var cache = mock(RedisJsonCacheSupport.class);
        var service = new SupplierService(repository, new SnowflakeIdGenerator(1), null, cache);

        var result = service.listActiveOptions();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    void shouldLoadOptionsDirectly_whenRedisUnavailable() {
        var repository = (SupplierRepository) Proxy.newProxyInstance(
                SupplierRepository.class.getClassLoader(),
                new Class[]{SupplierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByDeletedFlagFalseAndStatusOrderBySupplierCodeAsc" -> List.of(createSupplier(1L, "S001"));
                    case "toString" -> "SupplierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new SupplierService(repository, new SnowflakeIdGenerator(1), null);

        var result = service.listActiveOptions();

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldRefreshCache_whenCachedOptionsAreEmptyButActiveSuppliersExist() {
        SupplierRepository repository = mock(SupplierRepository.class);
        Supplier supplier = createSupplier(1L, "S001");
        supplier.setSupplierName("供应商甲");
        when(repository.findByDeletedFlagFalseAndStatusOrderBySupplierCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(supplier));
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        SupplierService service = new SupplierService(repository, new SnowflakeIdGenerator(1), null, cache);

        List<SupplierOptionResponse> result = service.listActiveOptions();

        assertThat(result).singleElement().satisfies(option -> {
            assertThat(option.id()).isEqualTo(1L);
            assertThat(option.label()).isEqualTo("供应商甲");
        });
        verify(cache, never()).delete(anyString());
        verify(cache, never()).write(eq("leo:supplier:all"), eq(result), any());
    }

    @Test
    void shouldReturnCachedEmptyOptionsWhenDatabaseAlsoEmpty() {
        SupplierRepository repository = mock(SupplierRepository.class);
        when(repository.findByDeletedFlagFalseAndStatusOrderBySupplierCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of());
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        SupplierService service = new SupplierService(repository, new SnowflakeIdGenerator(1), null, cache);

        List<SupplierOptionResponse> result = service.listActiveOptions();

        assertThat(result).isEmpty();
        verify(cache, never()).write(anyString(), any(), any());
    }

    @Test
    void shouldExposeCacheName() {
        SupplierService service = new SupplierService(null, new SnowflakeIdGenerator(1), null);

        assertThat(service.cacheName()).isEqualTo("leo:supplier:all");
    }

    @Test
    void shouldDeclareSpringCacheAnnotationsForOptions() throws Exception {
        Method readMethod = SupplierService.class.getMethod("listActiveOptions");
        Cacheable cacheable = readMethod.getAnnotation(Cacheable.class);
        Method createMethod = SupplierService.class.getMethod("create", SupplierRequest.class);
        Method updateMethod = SupplierService.class.getMethod("update", Long.class, SupplierRequest.class);
        Method updateStatusMethod = SupplierService.class.getMethod("updateStatus", Long.class, String.class);
        Method deleteMethod = SupplierService.class.getMethod("delete", Long.class);

        assertThat(cacheable.value()).containsExactly(CacheConfig.CACHE_OPTIONS);
        assertThat(cacheable.key()).isEqualTo("'leo:supplier:all'");
        assertThat(cacheable.unless()).isEqualTo("#result == null || #result.isEmpty()");
        assertThat(createMethod.getAnnotation(CacheEvict.class).value()).containsExactly(CacheConfig.CACHE_OPTIONS);
        assertThat(updateMethod.getAnnotation(CacheEvict.class).key()).isEqualTo("'leo:supplier:all'");
        assertThat(updateStatusMethod.getAnnotation(CacheEvict.class).key()).isEqualTo("'leo:supplier:all'");
        assertThat(deleteMethod.getAnnotation(CacheEvict.class).key()).isEqualTo("'leo:supplier:all'");
    }

    @Test
    void shouldKeepUpdateStatusOnSpringCacheEvictionPath() {
        Supplier supplier = createSupplier(1L, "S001");
        SupplierRepository repository = mock(SupplierRepository.class);
        SupplierMapper mapper = mock(SupplierMapper.class);
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(supplier));
        when(mapper.toResponse(supplier)).thenReturn(toResponse(supplier));
        SupplierService service = new SupplierService(repository, new SnowflakeIdGenerator(1), mapper, cache);

        SupplierResponse response = service.updateStatus(1L, StatusConstants.NORMAL);

        assertThat(response.id()).isEqualTo(1L);
        verify(repository, never()).save(any());
        verify(cache, never()).deleteAfterCommit("leo:supplier:all");
    }

    @Test
    void shouldRefreshCacheDuringHealthCheck_whenCachedSizeDiffersFromDatabase() {
        SupplierRepository repository = mock(SupplierRepository.class);
        Supplier supplier = createSupplier(1L, "S001");
        supplier.setSupplierName("供应商甲");
        when(repository.findByDeletedFlagFalseAndStatusOrderBySupplierCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(supplier));
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        SupplierService service = new SupplierService(repository, new SnowflakeIdGenerator(1), null, cache);

        var result = service.verifyAndRefreshCache();

        assertThat(result.cacheName()).isEqualTo("leo:supplier:all");
        assertThat(result.currentSize()).isZero();
        assertThat(result.refreshedSize()).isEqualTo(1);
        assertThat(result.refreshed()).isTrue();
        verify(cache).write(eq("leo:supplier:all"), any(), any());
    }

    @Test
    void shouldRefreshActualSpringCacheDuringHealthCheck() {
        SupplierRepository repository = mock(SupplierRepository.class);
        Supplier supplier = createSupplier(1L, "S001");
        supplier.setSupplierName("供应商新名称");
        when(repository.findByDeletedFlagFalseAndStatusOrderBySupplierCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(supplier));
        RedisJsonCacheSupport legacyCache = mock(RedisJsonCacheSupport.class);
        SupplierService service = new SupplierService(repository, new SnowflakeIdGenerator(1), null, legacyCache);
        var cacheManager = new ConcurrentMapCacheManager(CacheConfig.CACHE_OPTIONS);
        cacheManager.getCache(CacheConfig.CACHE_OPTIONS).put(
                "leo:supplier:all",
                List.of(new SupplierOptionResponse(1L, "供应商旧名称", "供应商旧名称"))
        );
        service.setCacheManager(cacheManager);

        var result = service.verifyAndRefreshCache();

        assertThat(cacheManager.getCache(CacheConfig.CACHE_OPTIONS).get("leo:supplier:all", List.class))
                .containsExactly(new SupplierOptionResponse(1L, "供应商新名称", "供应商新名称"));
        assertThat(result.cacheName()).isEqualTo("options::leo:supplier:all");
        assertThat(result.refreshed()).isTrue();
        verify(legacyCache, never()).write(anyString(), any(), any());
    }

    @Test
    void shouldRefreshCacheDuringHealthCheck_whenDatabaseContentChanged() {
        SupplierRepository repository = mock(SupplierRepository.class);
        Supplier supplier = createSupplier(1L, "S001");
        supplier.setSupplierName("供应商新名称");
        when(repository.findByDeletedFlagFalseAndStatusOrderBySupplierCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(supplier));
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        when(cache.read(anyString(), any(TypeReference.class)))
                .thenReturn(Optional.of(List.of(new SupplierOptionResponse(1L, "供应商旧名称", "供应商旧名称"))));
        SupplierService service = new SupplierService(repository, new SnowflakeIdGenerator(1), null, cache);

        var result = service.verifyAndRefreshCache();

        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.refreshedSize()).isEqualTo(1);
        assertThat(result.refreshed()).isTrue();
        verify(cache).write(
                eq("leo:supplier:all"),
                eq(List.of(new SupplierOptionResponse(1L, "供应商新名称", "供应商新名称"))),
                any()
        );
    }

    @Test
    void shouldNotRefreshCacheDuringHealthCheck_whenDatabaseHasNoActiveSuppliers() {
        SupplierRepository repository = mock(SupplierRepository.class);
        when(repository.findByDeletedFlagFalseAndStatusOrderBySupplierCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of());
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        SupplierService service = new SupplierService(repository, new SnowflakeIdGenerator(1), null, cache);

        var result = service.verifyAndRefreshCache();

        assertThat(result.refreshed()).isFalse();
        verify(cache, never()).write(anyString(), any(), any());
    }

    @Test
    void shouldDeleteEmptyCacheDuringHealthCheck_whenDatabaseHasNoActiveSuppliers() {
        SupplierRepository repository = mock(SupplierRepository.class);
        when(repository.findByDeletedFlagFalseAndStatusOrderBySupplierCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of());
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        when(cache.read(anyString(), any(TypeReference.class))).thenReturn(Optional.of(List.of()));
        SupplierService service = new SupplierService(repository, new SnowflakeIdGenerator(1), null, cache);

        var result = service.verifyAndRefreshCache();

        assertThat(result.currentSize()).isZero();
        assertThat(result.refreshedSize()).isZero();
        assertThat(result.refreshed()).isTrue();
        verify(cache).delete("leo:supplier:all");
        verify(cache, never()).write(anyString(), any(), any());
    }

    @Test
    void shouldDeleteStaleCacheDuringHealthCheck_whenDatabaseHasNoActiveSuppliers() {
        SupplierRepository repository = mock(SupplierRepository.class);
        when(repository.findByDeletedFlagFalseAndStatusOrderBySupplierCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of());
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        when(cache.read(anyString(), any(TypeReference.class)))
                .thenReturn(Optional.of(List.of(new SupplierOptionResponse(1L, "旧供应商", "旧供应商"))));
        SupplierService service = new SupplierService(repository, new SnowflakeIdGenerator(1), null, cache);

        var result = service.verifyAndRefreshCache();

        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.refreshedSize()).isZero();
        assertThat(result.refreshed()).isTrue();
        verify(cache).delete("leo:supplier:all");
        verify(cache, never()).write(anyString(), any(), any());
    }

    @Test
    void shouldThrowException_whenCreateWithDuplicateCode() {
        var repository = (SupplierRepository) Proxy.newProxyInstance(
                SupplierRepository.class.getClassLoader(),
                new Class[]{SupplierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsBySupplierCodeAndDeletedFlagFalse" -> true;
                    case "toString" -> "SupplierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new SupplierService(repository, new SnowflakeIdGenerator(1), null);

        assertThatThrownBy(() -> service.create(new SupplierRequest("S001", null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商编码已存在");
    }

    @Test
    void shouldThrowException_whenUpdateWithChangedDuplicateCode() {
        var repository = (SupplierRepository) Proxy.newProxyInstance(
                SupplierRepository.class.getClassLoader(),
                new Class[]{SupplierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createSupplier(1L, "S001"));
                    case "existsBySupplierCodeAndDeletedFlagFalse" -> true;
                    case "toString" -> "SupplierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new SupplierService(repository, new SnowflakeIdGenerator(1), null);

        assertThatThrownBy(() -> service.update(1L, new SupplierRequest("S002", null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商编码已存在");
    }

    @Test
    void shouldEvictCache_whenSaving() {
        var repository = (SupplierRepository) Proxy.newProxyInstance(
                SupplierRepository.class.getClassLoader(),
                new Class[]{SupplierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createSupplier(1L, "S001"));
                    case "save" -> args[0];
                    case "existsBySupplierCodeAndDeletedFlagFalse" -> false;
                    case "toString" -> "SupplierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var cache = mock(RedisJsonCacheSupport.class);
        var mapper = (SupplierMapper) Proxy.newProxyInstance(
                SupplierMapper.class.getClassLoader(),
                new Class[]{SupplierMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new SupplierResponse(1L, null, null, null, null, null, null, null);
                    case "toString" -> "SupplierMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new SupplierService(repository, new SnowflakeIdGenerator(1), mapper, cache);

        var request = new SupplierRequest("S001", "供应商甲", "张三", "13800138000", "北京", "正常", null);
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
        verify(cache, never()).deleteAfterCommit("leo:supplier:all");
    }

    @Test
    void shouldCreate_success() {
        var repository = (SupplierRepository) Proxy.newProxyInstance(
                SupplierRepository.class.getClassLoader(),
                new Class[]{SupplierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsBySupplierCodeAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "SupplierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (SupplierMapper) Proxy.newProxyInstance(
                SupplierMapper.class.getClassLoader(),
                new Class[]{SupplierMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Supplier) args[0]);
                    case "toString" -> "SupplierMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new SupplierService(repository, new SnowflakeIdGenerator(1), mapper);

        var request = new SupplierRequest("S001", "供应商甲", "张三", "13800138000", "北京", "正常", "备注");
        var result = service.create(request);

        assertThat(result).isNotNull();
        assertThat(result.supplierCode()).isEqualTo("S001");
    }

    @Test
    void shouldUpdate_successWithSameCode() {
        var repository = (SupplierRepository) Proxy.newProxyInstance(
                SupplierRepository.class.getClassLoader(),
                new Class[]{SupplierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createSupplier(1L, "S001"));
                    case "save" -> args[0];
                    case "toString" -> "SupplierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (SupplierMapper) Proxy.newProxyInstance(
                SupplierMapper.class.getClassLoader(),
                new Class[]{SupplierMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Supplier) args[0]);
                    case "toString" -> "SupplierMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new SupplierService(repository, new SnowflakeIdGenerator(1), mapper);

        var request = new SupplierRequest("S001", "供应商乙", "李四", "13900139000", "上海", "正常", null);
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
        assertThat(result.supplierName()).isEqualTo("供应商乙");
    }

    @Test
    void shouldUpdate_successWithDifferentCode() {
        var repository = (SupplierRepository) Proxy.newProxyInstance(
                SupplierRepository.class.getClassLoader(),
                new Class[]{SupplierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createSupplier(1L, "S001"));
                    case "existsBySupplierCodeAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "SupplierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (SupplierMapper) Proxy.newProxyInstance(
                SupplierMapper.class.getClassLoader(),
                new Class[]{SupplierMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Supplier) args[0]);
                    case "toString" -> "SupplierMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new SupplierService(repository, new SnowflakeIdGenerator(1), mapper);

        var request = new SupplierRequest("S002", "供应商乙", null, null, null, "正常", null);
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldThrowException_whenDetailNotFound() {
        var repository = (SupplierRepository) Proxy.newProxyInstance(
                SupplierRepository.class.getClassLoader(),
                new Class[]{SupplierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.empty();
                    case "findById" -> Optional.empty();
                    case "toString" -> "SupplierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new SupplierService(repository, new SnowflakeIdGenerator(1), null);

        assertThatThrownBy(() -> service.detail(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商不存在");
    }

    @Test
    void shouldDelete_success() {
        var repository = (SupplierRepository) Proxy.newProxyInstance(
                SupplierRepository.class.getClassLoader(),
                new Class[]{SupplierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createSupplier(1L, "S001"));
                    case "findById" -> Optional.of(createSupplier(1L, "S001"));
                    case "save" -> args[0];
                    case "toString" -> "SupplierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var referenceGuard = mock(MasterDataReferenceGuard.class);
        var service = new SupplierService(repository, new SnowflakeIdGenerator(1), null, null, referenceGuard);

        service.delete(1L);

        verify(referenceGuard).assertNoReferences(eq("该供应商"), any(List.class));
    }

    @Test
    void shouldDeleteWithoutReferenceCheckWhenReferenceGuardMissing() {
        var repository = (SupplierRepository) Proxy.newProxyInstance(
                SupplierRepository.class.getClassLoader(),
                new Class[]{SupplierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createSupplier(1L, "S001"));
                    case "findById" -> Optional.of(createSupplier(1L, "S001"));
                    case "save" -> args[0];
                    case "toString" -> "SupplierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new SupplierService(repository, new SnowflakeIdGenerator(1), null);

        service.delete(1L);
    }

    @Test
    void shouldThrowException_whenDeleteWithReferences() {
        var repository = (SupplierRepository) Proxy.newProxyInstance(
                SupplierRepository.class.getClassLoader(),
                new Class[]{SupplierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createSupplier(1L, "S001"));
                    case "findById" -> Optional.of(createSupplier(1L, "S001"));
                    case "toString" -> "SupplierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var referenceGuard = mock(MasterDataReferenceGuard.class);
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "该供应商已被业务或主数据引用"))
                .when(referenceGuard).assertNoReferences(eq("该供应商"), any(List.class));
        var service = new SupplierService(repository, new SnowflakeIdGenerator(1), null, null, referenceGuard);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该供应商已被业务或主数据引用");
    }

    @Test
    void shouldEvictCache_whenCreating() {
        var repository = (SupplierRepository) Proxy.newProxyInstance(
                SupplierRepository.class.getClassLoader(),
                new Class[]{SupplierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsBySupplierCodeAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "SupplierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var cache = mock(RedisJsonCacheSupport.class);
        var mapper = (SupplierMapper) Proxy.newProxyInstance(
                SupplierMapper.class.getClassLoader(),
                new Class[]{SupplierMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new SupplierResponse(1L, null, null, null, null, null, null, null);
                    case "toString" -> "SupplierMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new SupplierService(repository, new SnowflakeIdGenerator(1), mapper, cache);

        var request = new SupplierRequest("S001", "供应商甲", null, null, null, "正常", null);
        service.create(request);

        verify(cache, never()).deleteAfterCommit("leo:supplier:all");
    }

    private static Supplier createSupplier(Long id, String code) {
        Supplier s = new Supplier();
        s.setId(id);
        s.setSupplierCode(code);
        s.setSupplierName("供应商甲");
        s.setContactName("张三");
        s.setStatus(StatusConstants.NORMAL);
        return s;
    }

    private static SupplierResponse toResponse(Supplier s) {
        return new SupplierResponse(
                s.getId(), s.getSupplierCode(), s.getSupplierName(),
                s.getContactName(), s.getContactPhone(), s.getCity(),
                s.getStatus(), s.getRemark()
        );
    }
}
