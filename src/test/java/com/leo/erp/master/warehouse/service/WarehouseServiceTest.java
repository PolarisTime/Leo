package com.leo.erp.master.warehouse.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.web.OptionResponse;
import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import com.leo.erp.master.warehouse.mapper.WarehouseMapper;
import com.leo.erp.master.warehouse.repository.WarehouseRepository;
import com.leo.erp.master.warehouse.web.dto.WarehouseRequest;
import com.leo.erp.master.warehouse.web.dto.WarehouseResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WarehouseServiceTest {

    @Test
    void shouldReturnPage_whenCallingPage() {
        var repository = (WarehouseRepository) Proxy.newProxyInstance(
                WarehouseRepository.class.getClassLoader(),
                new Class[]{WarehouseRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> org.springframework.data.domain.Page.empty();
                    case "toString" -> "WarehouseRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new WarehouseService(repository, new SnowflakeIdGenerator(1), null, null);

        var result = service.page(new PageQuery(0, 10, "id", "desc"), null, null, null);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnActiveOptions_whenCallingListActiveOptions() {
        var warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        when(warehouseSelectionSupport.listActiveOptions()).thenReturn(List.of(new OptionResponse("一号库", "一号库")));
        var service = new WarehouseService(null, null, null, warehouseSelectionSupport);

        var result = service.listActiveOptions();

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldThrowException_whenCreateWithDuplicateCode() {
        var repository = (WarehouseRepository) Proxy.newProxyInstance(
                WarehouseRepository.class.getClassLoader(),
                new Class[]{WarehouseRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByWarehouseCodeAndDeletedFlagFalse" -> true;
                    case "toString" -> "WarehouseRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new WarehouseService(repository, new SnowflakeIdGenerator(1), null, null);

        assertThatThrownBy(() -> service.create(new WarehouseRequest("WH001", null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仓库编码已存在");
    }

    @Test
    void shouldThrowException_whenUpdateWithChangedDuplicateCode() {
        var repository = (WarehouseRepository) Proxy.newProxyInstance(
                WarehouseRepository.class.getClassLoader(),
                new Class[]{WarehouseRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createWarehouse(1L, "WH001"));
                    case "existsByWarehouseCodeAndDeletedFlagFalse" -> true;
                    case "toString" -> "WarehouseRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new WarehouseService(repository, new SnowflakeIdGenerator(1), null, null);

        assertThatThrownBy(() -> service.update(1L, new WarehouseRequest("WH002", null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仓库编码已存在");
    }

    @Test
    void shouldReturnDetail_whenEntityExists() {
        var repository = (WarehouseRepository) Proxy.newProxyInstance(
                WarehouseRepository.class.getClassLoader(),
                new Class[]{WarehouseRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createWarehouse(1L, "WH001"));
                    case "findById" -> Optional.of(createWarehouse(1L, "WH001"));
                    case "toString" -> "WarehouseRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (WarehouseMapper) Proxy.newProxyInstance(
                WarehouseMapper.class.getClassLoader(),
                new Class[]{WarehouseMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new WarehouseResponse(1L, "WH001", "一号库", "室内", null, null, null, "正常", null);
                    case "toString" -> "WarehouseMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new WarehouseService(repository, new SnowflakeIdGenerator(1), mapper, null);

        var result = service.detail(1L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.warehouseCode()).isEqualTo("WH001");
    }

    @Test
    void shouldEvictCache_whenSaving() {
        var repository = (WarehouseRepository) Proxy.newProxyInstance(
                WarehouseRepository.class.getClassLoader(),
                new Class[]{WarehouseRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createWarehouse(1L, "WH001"));
                    case "save" -> args[0];
                    case "existsByWarehouseCodeAndDeletedFlagFalse" -> false;
                    case "toString" -> "WarehouseRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        var mapper = (WarehouseMapper) Proxy.newProxyInstance(
                WarehouseMapper.class.getClassLoader(),
                new Class[]{WarehouseMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new WarehouseResponse(1L, null, null, null, null, null, null, null, null);
                    case "toString" -> "WarehouseMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new WarehouseService(repository, new SnowflakeIdGenerator(1), mapper, warehouseSelectionSupport);

        var request = new WarehouseRequest("WH001", "一号库", "室内", null, null, null, "正常", null);
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
        verify(warehouseSelectionSupport).evictCache();
    }

    @Test
    void shouldCreate_success() {
        var repository = (WarehouseRepository) Proxy.newProxyInstance(
                WarehouseRepository.class.getClassLoader(),
                new Class[]{WarehouseRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByWarehouseCodeAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "WarehouseRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        var mapper = (WarehouseMapper) Proxy.newProxyInstance(
                WarehouseMapper.class.getClassLoader(),
                new Class[]{WarehouseMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Warehouse) args[0]);
                    case "toString" -> "WarehouseMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new WarehouseService(repository, new SnowflakeIdGenerator(1), mapper, warehouseSelectionSupport);

        var request = new WarehouseRequest("WH001", "一号库", "室内", "张三", "13800138000", "地址", "正常", "备注");
        var result = service.create(request);

        assertThat(result).isNotNull();
        assertThat(result.warehouseCode()).isEqualTo("WH001");
        verify(warehouseSelectionSupport).evictCache();
    }

    @Test
    void shouldUpdate_successWithSameCode() {
        var repository = (WarehouseRepository) Proxy.newProxyInstance(
                WarehouseRepository.class.getClassLoader(),
                new Class[]{WarehouseRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createWarehouse(1L, "WH001"));
                    case "save" -> args[0];
                    case "toString" -> "WarehouseRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        var mapper = (WarehouseMapper) Proxy.newProxyInstance(
                WarehouseMapper.class.getClassLoader(),
                new Class[]{WarehouseMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Warehouse) args[0]);
                    case "toString" -> "WarehouseMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new WarehouseService(repository, new SnowflakeIdGenerator(1), mapper, warehouseSelectionSupport);

        var request = new WarehouseRequest("WH001", "二号库", "室外", null, null, null, "正常", null);
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
        assertThat(result.warehouseName()).isEqualTo("二号库");
    }

    @Test
    void shouldThrowException_whenDetailNotFound() {
        var repository = (WarehouseRepository) Proxy.newProxyInstance(
                WarehouseRepository.class.getClassLoader(),
                new Class[]{WarehouseRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.empty();
                    case "findById" -> Optional.empty();
                    case "toString" -> "WarehouseRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new WarehouseService(repository, new SnowflakeIdGenerator(1), null, null);

        assertThatThrownBy(() -> service.detail(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仓库不存在");
    }

    @Test
    void shouldDelete_success() {
        var repository = (WarehouseRepository) Proxy.newProxyInstance(
                WarehouseRepository.class.getClassLoader(),
                new Class[]{WarehouseRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createWarehouse(1L, "WH001"));
                    case "findById" -> Optional.of(createWarehouse(1L, "WH001"));
                    case "save" -> args[0];
                    case "toString" -> "WarehouseRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        var referenceGuard = mock(MasterDataReferenceGuard.class);
        var service = new WarehouseService(repository, new SnowflakeIdGenerator(1), null, warehouseSelectionSupport, referenceGuard);

        service.delete(1L);

        verify(referenceGuard).assertNoReferences(eq("该仓库"), any(List.class));
        verify(warehouseSelectionSupport).evictCache();
    }

    @Test
    void shouldThrowException_whenDeleteWithReferences() {
        var repository = (WarehouseRepository) Proxy.newProxyInstance(
                WarehouseRepository.class.getClassLoader(),
                new Class[]{WarehouseRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createWarehouse(1L, "WH001"));
                    case "findById" -> Optional.of(createWarehouse(1L, "WH001"));
                    case "toString" -> "WarehouseRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var referenceGuard = mock(MasterDataReferenceGuard.class);
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "该仓库已被业务或主数据引用"))
                .when(referenceGuard).assertNoReferences(eq("该仓库"), any(List.class));
        var service = new WarehouseService(repository, new SnowflakeIdGenerator(1), null, null, referenceGuard);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该仓库已被业务或主数据引用");
    }

    private static Warehouse createWarehouse(Long id, String code) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setWarehouseCode(code);
        w.setWarehouseName("一号库");
        w.setWarehouseType("室内");
        w.setStatus("正常");
        return w;
    }

    private static WarehouseResponse toResponse(Warehouse w) {
        return new WarehouseResponse(
                w.getId(), w.getWarehouseCode(), w.getWarehouseName(),
                w.getWarehouseType(), w.getContactName(), w.getContactPhone(),
                w.getAddress(), w.getStatus(), w.getRemark()
        );
    }
}
