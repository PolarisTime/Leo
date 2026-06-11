package com.leo.erp.master.carrier.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.domain.entity.Vehicle;
import com.leo.erp.master.carrier.mapper.CarrierMapper;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.master.carrier.repository.VehicleRepository;
import com.leo.erp.master.carrier.web.dto.CarrierOptionResponse;
import com.leo.erp.master.carrier.web.dto.CarrierRequest;
import com.leo.erp.master.carrier.web.dto.CarrierResponse;
import com.leo.erp.master.carrier.web.dto.VehicleInfo;
import com.leo.erp.master.carrier.web.dto.VehicleItem;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
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

class CarrierServiceTest {

    @Test
    void shouldReturnCarrierOptionsWithVehiclePlates() {
        CarrierRepository repository = mock(CarrierRepository.class);
        VehicleRepository vehicleRepository = mock(VehicleRepository.class);
        CarrierMapper mapper = mock(CarrierMapper.class);
        Carrier carrier = new Carrier();
        carrier.setId(1L);
        carrier.setCarrierCode("WL-001");
        carrier.setCarrierName("物流甲");

        Vehicle v1 = new Vehicle();
        v1.setPlate("苏A12345");
        v1.setCarrier(carrier);
        Vehicle v2 = new Vehicle();
        v2.setPlate("苏A67890");
        v2.setCarrier(carrier);
        Vehicle v3 = new Vehicle();
        v3.setPlate("苏A99999");
        v3.setCarrier(carrier);
        carrier.setVehicles(List.of(v1, v2, v3));

        when(repository.findByDeletedFlagFalseAndStatusOrderByCarrierCodeAsc(StatusConstants.NORMAL)).thenReturn(List.of(carrier));

        CarrierService service = new CarrierService(repository, vehicleRepository, null, mapper);

        List<CarrierOptionResponse> options = service.listActiveOptions();

        assertThat(options).singleElement().satisfies(option -> {
            assertThat(option.id()).isEqualTo(1L);
            assertThat(option.label()).isEqualTo("物流甲");
            assertThat(option.value()).isEqualTo("物流甲");
            assertThat(option.vehiclePlates()).containsExactly("苏A12345", "苏A67890", "苏A99999");
        });
    }

    @Test
    void shouldReturnPage_whenCallingPage() {
        var repository = (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> org.springframework.data.domain.Page.empty();
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CarrierService(repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), null);

        var result = service.page(new PageQuery(0, 10, "id", "desc"), null, null);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldThrowException_whenCreateWithDuplicateCode() {
        var repository = (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByCarrierCodeAndDeletedFlagFalse" -> true;
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CarrierService(repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), null);

        assertThatThrownBy(() -> service.create(new CarrierRequest("CR001", "物流甲", null, null, null, null, null, "正常", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流方编码已存在");
    }

    @Test
    void shouldThrowException_whenUpdateWithChangedDuplicateCode() {
        var repository = (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCarrier(1L, "CR001"));
                    case "existsByCarrierCodeAndDeletedFlagFalse" -> true;
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CarrierService(repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), null);

        assertThatThrownBy(() -> service.update(1L, new CarrierRequest("CR002", "物流乙", null, null, null, null, null, "正常", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流方编码已存在");
    }

    @Test
    void shouldReturnDetail_whenEntityExists() {
        var repository = (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCarrier(1L, "CR001"));
                    case "findById" -> Optional.of(createCarrier(1L, "CR001"));
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (CarrierMapper) Proxy.newProxyInstance(
                CarrierMapper.class.getClassLoader(),
                new Class[]{CarrierMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new CarrierResponse(1L, "CR001", "物流甲", null, null, null, List.of(), null, "正常", null);
                    case "toString" -> "CarrierMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CarrierService(repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), mapper);

        var result = service.detail(1L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void shouldEvictCache_whenSavingWithRedis() {
        var repository = (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCarrier(1L, "CR001"));
                    case "save" -> args[0];
                    case "existsByCarrierCodeAndDeletedFlagFalse" -> false;
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var cache = mock(RedisJsonCacheSupport.class);
        var mapper = (CarrierMapper) Proxy.newProxyInstance(
                CarrierMapper.class.getClassLoader(),
                new Class[]{CarrierMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new CarrierResponse(1L, null, null, null, null, null, List.of(), null, null, null);
                    case "toString" -> "CarrierMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CarrierService(repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), mapper, cache);

        var request = new CarrierRequest("CR001", "物流甲", null, null, null, null, null, "正常", null);
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
        verify(cache).delete("leo:carrier:all");
    }

    @Test
    void shouldCreateWithVehicles() {
        var repository = (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByCarrierCodeAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (CarrierMapper) Proxy.newProxyInstance(
                CarrierMapper.class.getClassLoader(),
                new Class[]{CarrierMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Carrier) args[0]);
                    case "toString" -> "CarrierMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CarrierService(repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), mapper);

        var vehicles = List.of(
                new VehicleItem("苏A12345", "司机A", "13900000001", "车辆1"),
                new VehicleItem("苏A67890", "司机B", "13900000002", "车辆2")
        );
        var request = new CarrierRequest("CR001", "物流甲", "张三", "13800138000", "重型卡车", vehicles, "按吨", "正常", "备注");
        var result = service.create(request);

        assertThat(result).isNotNull();
        assertThat(result.carrierCode()).isEqualTo("CR001");
        assertThat(result.vehicles()).hasSize(2);
    }

    @Test
    void shouldUpdateWithVehicles() {
        Carrier existing = createCarrier(1L, "CR001");
        existing.setVehicles(new ArrayList<>());
        var repository = (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(existing);
                    case "save" -> args[0];
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (CarrierMapper) Proxy.newProxyInstance(
                CarrierMapper.class.getClassLoader(),
                new Class[]{CarrierMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Carrier) args[0]);
                    case "toString" -> "CarrierMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CarrierService(repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), mapper);

        var vehicles = List.of(
                new VehicleItem("苏A12345", null, null, null),
                new VehicleItem("  ", null, null, null),
                new VehicleItem(null, null, null, null)
        );
        var request = new CarrierRequest("CR001", "物流甲", "  ", "  ", "  ", vehicles, "按吨", "正常", "备注");
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
        assertThat(result.vehicles()).hasSize(1);
        assertThat(result.vehicles().get(0).plate()).isEqualTo("苏A12345");
    }

    @Test
    void shouldUpdateWithNullVehicles() {
        Carrier existing = createCarrier(1L, "CR001");
        existing.setVehicles(new ArrayList<>());
        var repository = (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(existing);
                    case "save" -> args[0];
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (CarrierMapper) Proxy.newProxyInstance(
                CarrierMapper.class.getClassLoader(),
                new Class[]{CarrierMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Carrier) args[0]);
                    case "toString" -> "CarrierMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CarrierService(repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), mapper);

        var request = new CarrierRequest("CR001", "物流甲", null, null, null, null, "按吨", "正常", null);
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldThrowException_whenDetailNotFound() {
        var repository = (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.empty();
                    case "findById" -> Optional.empty();
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CarrierService(repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), null);

        assertThatThrownBy(() -> service.detail(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流方不存在");
    }

    @Test
    void shouldDelete_success() {
        var repository = (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCarrier(1L, "CR001"));
                    case "findById" -> Optional.of(createCarrier(1L, "CR001"));
                    case "save" -> args[0];
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var referenceGuard = mock(MasterDataReferenceGuard.class);
        var service = new CarrierService(repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), null, null, referenceGuard);

        service.delete(1L);

        verify(referenceGuard).assertNoReferences(eq("该物流商"), any(List.class));
    }

    @Test
    void shouldThrowException_whenDeleteWithReferences() {
        var repository = (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCarrier(1L, "CR001"));
                    case "findById" -> Optional.of(createCarrier(1L, "CR001"));
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var referenceGuard = mock(MasterDataReferenceGuard.class);
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "该物流商已被业务或主数据引用"))
                .when(referenceGuard).assertNoReferences(eq("该物流商"), any(List.class));
        var service = new CarrierService(repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), null, null, referenceGuard);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该物流商已被业务或主数据引用");
    }

    @Test
    void shouldCreate_success() {
        var repository = (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByCarrierCodeAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (CarrierMapper) Proxy.newProxyInstance(
                CarrierMapper.class.getClassLoader(),
                new Class[]{CarrierMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new CarrierResponse(1L, "CR001", "物流甲", null, null, null, List.of(), null, "正常", null);
                    case "toString" -> "CarrierMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CarrierService(repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), mapper);

        var request = new CarrierRequest("CR001", "物流甲", "张三", "13800138000", "重型卡车", null, "按吨", "正常", "备注");
        var result = service.create(request);

        assertThat(result).isNotNull();
        assertThat(result.carrierCode()).isEqualTo("CR001");
    }

    @Test
    void shouldUpdate_successWithSameCode() {
        var repository = (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCarrier(1L, "CR001"));
                    case "save" -> args[0];
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (CarrierMapper) Proxy.newProxyInstance(
                CarrierMapper.class.getClassLoader(),
                new Class[]{CarrierMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new CarrierResponse(1L, "CR001", "物流甲", null, null, null, List.of(), null, "正常", null);
                    case "toString" -> "CarrierMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CarrierService(repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), mapper);

        var request = new CarrierRequest("CR001", "物流乙", null, null, null, null, null, "正常", null);
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldTestPrivateUtilityMethods_viaReflection() throws Exception {
        CarrierService service = new CarrierService(null, null, null, null);

        Method emptyToNull = CarrierService.class.getDeclaredMethod("emptyToNull", String.class);
        emptyToNull.setAccessible(true);

        assertThat(emptyToNull.invoke(service, (Object) null)).isNull();
        assertThat(emptyToNull.invoke(service, "")).isNull();
        assertThat(emptyToNull.invoke(service, "  ")).isNull();
        assertThat(emptyToNull.invoke(service, "value")).isEqualTo("value");
        assertThat(emptyToNull.invoke(service, " value ")).isEqualTo("value");
    }

    @Test
    void shouldTestLoadCachedResponses_withoutRedis() throws Exception {
        Carrier carrier = createCarrier(1L, "CR001");
        var repository = (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByDeletedFlagFalseOrderByCarrierCodeAsc" -> List.of(carrier);
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (CarrierMapper) Proxy.newProxyInstance(
                CarrierMapper.class.getClassLoader(),
                new Class[]{CarrierMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new CarrierResponse(1L, "CR001", "物流甲", null, null, null, List.of(), null, "正常", null);
                    case "toString" -> "CarrierMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CarrierService(repository, null, null, mapper);

        Method loadCachedResponses = CarrierService.class.getDeclaredMethod("loadCachedResponses");
        loadCachedResponses.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<CarrierResponse> result = (List<CarrierResponse>) loadCachedResponses.invoke(service);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).carrierCode()).isEqualTo("CR001");
    }

    @Test
    void shouldTestLoadCachedResponses_withRedis() throws Exception {
        Carrier carrier = createCarrier(1L, "CR001");
        var repository = (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByDeletedFlagFalseOrderByCarrierCodeAsc" -> List.of(carrier);
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (CarrierMapper) Proxy.newProxyInstance(
                CarrierMapper.class.getClassLoader(),
                new Class[]{CarrierMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new CarrierResponse(1L, "CR001", "物流甲", null, null, null, List.of(), null, "正常", null);
                    case "toString" -> "CarrierMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var cache = mock(RedisJsonCacheSupport.class);
        when(cache.<List<CarrierResponse>>getOrLoad(anyString(), any(java.time.Duration.class), any(com.fasterxml.jackson.core.type.TypeReference.class), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            var loader = (java.util.function.Supplier<List<CarrierResponse>>) invocation.getArgument(3);
            return loader.get();
        });
        var service = new CarrierService(repository, null, null, mapper, cache);

        Method loadCachedResponses = CarrierService.class.getDeclaredMethod("loadCachedResponses");
        loadCachedResponses.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<CarrierResponse> result = (List<CarrierResponse>) loadCachedResponses.invoke(service);

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldTestLoadCachedResponses_withCacheDirect() throws Exception {
        var mapper = (CarrierMapper) Proxy.newProxyInstance(
                CarrierMapper.class.getClassLoader(),
                new Class[]{CarrierMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new CarrierResponse(1L, "CR001", "物流甲", null, null, null, List.of(), null, "正常", null);
                    case "toString" -> "CarrierMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var cache = mock(RedisJsonCacheSupport.class);
        var cachedResponse = new CarrierResponse(1L, "CR001", "物流甲", null, null, null, List.of(), null, "正常", null);
        when(cache.<List<CarrierResponse>>getOrLoad(anyString(), any(java.time.Duration.class), any(com.fasterxml.jackson.core.type.TypeReference.class), any())).thenReturn(List.of(cachedResponse));
        var service = new CarrierService(null, null, null, mapper, cache);

        Method loadCachedResponses = CarrierService.class.getDeclaredMethod("loadCachedResponses");
        loadCachedResponses.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<CarrierResponse> result = (List<CarrierResponse>) loadCachedResponses.invoke(service);

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldTestMatches_viaReflection() throws Exception {
        var service = new CarrierService(null, null, null, null);
        Method matches = CarrierService.class.getDeclaredMethod("matches", CarrierResponse.class, String.class, String.class);
        matches.setAccessible(true);

        var response = new CarrierResponse(1L, "CR001", "物流甲", "张三", null, null, List.of(), null, "正常", null);

        assertThat((boolean) matches.invoke(service, response, null, null)).isTrue();
        assertThat((boolean) matches.invoke(service, response, "", null)).isTrue();
        assertThat((boolean) matches.invoke(service, response, "CR001", null)).isTrue();
        assertThat((boolean) matches.invoke(service, response, "物流", null)).isTrue();
        assertThat((boolean) matches.invoke(service, response, "张三", null)).isTrue();
        assertThat((boolean) matches.invoke(service, response, "不存在", null)).isFalse();
        assertThat((boolean) matches.invoke(service, response, null, "正常")).isTrue();
        assertThat((boolean) matches.invoke(service, response, null, "停用")).isFalse();
    }

    @Test
    void shouldTestContains_viaReflection() throws Exception {
        var service = new CarrierService(null, null, null, null);
        Method contains = CarrierService.class.getDeclaredMethod("contains", String.class, String.class);
        contains.setAccessible(true);

        assertThat((boolean) contains.invoke(service, "Hello World", "hello")).isTrue();
        assertThat((boolean) contains.invoke(service, "Hello World", "xyz")).isFalse();
        assertThat((boolean) contains.invoke(service, null, "test")).isFalse();
    }

    @Test
    void shouldTestEvictCache_withoutRedis() throws Exception {
        var service = new CarrierService(null, null, null, null);
        Method evictCache = CarrierService.class.getDeclaredMethod("evictCache");
        evictCache.setAccessible(true);

        evictCache.invoke(service);
    }

    @Test
    void shouldTestEvictCache_withRedis() throws Exception {
        var cache = mock(RedisJsonCacheSupport.class);
        var service = new CarrierService(null, null, null, null, cache);
        Method evictCache = CarrierService.class.getDeclaredMethod("evictCache");
        evictCache.setAccessible(true);

        evictCache.invoke(service);

        verify(cache).delete("leo:carrier:all");
    }

    @Test
    void shouldTestBuildComparator_viaReflection() throws Exception {
        var service = new CarrierService(null, null, null, null);
        Method buildComparator = CarrierService.class.getDeclaredMethod("buildComparator", PageQuery.class);
        buildComparator.setAccessible(true);

        var r1 = new CarrierResponse(1L, "CR001", "A物流", null, null, null, List.of(), null, "正常", null);
        var r2 = new CarrierResponse(2L, "CR002", "B物流", null, null, null, List.of(), null, "正常", null);

        var comparatorById = (java.util.Comparator<CarrierResponse>) buildComparator.invoke(service, new PageQuery(0, 10, null, "asc"));
        assertThat(comparatorById.compare(r1, r2)).isLessThan(0);

        var comparatorByCode = (java.util.Comparator<CarrierResponse>) buildComparator.invoke(service, new PageQuery(0, 10, "carrierCode", "desc"));
        assertThat(comparatorByCode.compare(r1, r2)).isGreaterThan(0);

        var comparatorByName = (java.util.Comparator<CarrierResponse>) buildComparator.invoke(service, new PageQuery(0, 10, "carrierName", "asc"));
        assertThat(comparatorByName.compare(r1, r2)).isLessThan(0);

        var comparatorByContact = (java.util.Comparator<CarrierResponse>) buildComparator.invoke(service, new PageQuery(0, 10, "contactName", "asc"));
        assertThat(comparatorByContact).isNotNull();

        var comparatorByVehicleType = (java.util.Comparator<CarrierResponse>) buildComparator.invoke(service, new PageQuery(0, 10, "vehicleType", "asc"));
        assertThat(comparatorByVehicleType).isNotNull();

        var comparatorByPriceMode = (java.util.Comparator<CarrierResponse>) buildComparator.invoke(service, new PageQuery(0, 10, "priceMode", "asc"));
        assertThat(comparatorByPriceMode).isNotNull();

        var comparatorByStatus = (java.util.Comparator<CarrierResponse>) buildComparator.invoke(service, new PageQuery(0, 10, "status", "asc"));
        assertThat(comparatorByStatus).isNotNull();
    }

    @Test
    void shouldTestToPage_viaReflection() throws Exception {
        var service = new CarrierService(null, null, null, null);
        Method toPage = CarrierService.class.getDeclaredMethod("toPage", List.class, PageQuery.class);
        toPage.setAccessible(true);

        var items = List.of(
                new CarrierResponse(1L, "CR001", "物流甲", null, null, null, List.of(), null, "正常", null),
                new CarrierResponse(2L, "CR002", "物流乙", null, null, null, List.of(), null, "正常", null),
                new CarrierResponse(3L, "CR003", "物流丙", null, null, null, List.of(), null, "正常", null)
        );

        @SuppressWarnings("unchecked")
        var page0 = (org.springframework.data.domain.Page<CarrierResponse>) toPage.invoke(service, items, new PageQuery(0, 2, null, null));
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(3);

        @SuppressWarnings("unchecked")
        var page1 = (org.springframework.data.domain.Page<CarrierResponse>) toPage.invoke(service, items, new PageQuery(1, 2, null, null));
        assertThat(page1.getContent()).hasSize(1);
    }

    private static Carrier createCarrier(Long id, String code) {
        Carrier c = new Carrier();
        c.setId(id);
        c.setCarrierCode(code);
        c.setCarrierName("物流甲");
        c.setStatus(StatusConstants.NORMAL);
        return c;
    }

    private static CarrierResponse toResponse(Carrier c) {
        List<VehicleInfo> vehicles = c.getVehicles() == null ? List.of() :
                c.getVehicles().stream()
                        .map(v -> new VehicleInfo(v.getId(), v.getPlate(), v.getContact(), v.getPhone(), v.getRemark()))
                        .toList();
        return new CarrierResponse(
                c.getId(), c.getCarrierCode(), c.getCarrierName(),
                c.getContactName(), c.getContactPhone(), c.getVehicleType(),
                vehicles, c.getPriceMode(), c.getStatus(), c.getRemark()
        );
    }
}
