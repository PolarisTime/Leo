package com.leo.erp.master.supplier.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.api.PageQuery;
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
    void shouldReturnCachedOptions_whenRedisAvailable() {
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
        when(cache.getOrLoad(anyString(), any(), any(TypeReference.class), any())).thenReturn(List.of(new SupplierOptionResponse(1L, "供应商甲", "供应商甲")));
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
        verify(cache).delete("leo:supplier:all");
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

        verify(cache).delete("leo:supplier:all");
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
