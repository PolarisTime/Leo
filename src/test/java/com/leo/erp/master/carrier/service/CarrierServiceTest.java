package com.leo.erp.master.carrier.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
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
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        carrier.setDefaultSettlementCompanyId(9L);
        carrier.setDefaultSettlementCompanyName("上海结算主体");

        Vehicle v1 = new Vehicle();
        v1.setId(101L);
        v1.setPlate("苏A12345");
        v1.setCarrier(carrier);
        Vehicle v2 = new Vehicle();
        v2.setId(102L);
        v2.setPlate("苏A67890");
        v2.setCarrier(carrier);
        Vehicle v3 = new Vehicle();
        v3.setId(103L);
        v3.setPlate("苏A99999");
        v3.setCarrier(carrier);
        carrier.setVehicles(List.of(v1, v2, v3));

        when(repository.findByDeletedFlagFalseAndStatusOrderByCarrierCodeAsc(StatusConstants.NORMAL)).thenReturn(List.of(carrier));

        CarrierService service = new CarrierService(repository, vehicleRepository, new SnowflakeIdGenerator(1), mapper);

        List<CarrierOptionResponse> options = service.listActiveOptions();

        assertThat(options).singleElement().satisfies(option -> {
            assertThat(option.id()).isEqualTo(1L);
            assertThat(option.carrierCode()).isEqualTo("WL-001");
            assertThat(option.label()).isEqualTo("WL-001 / 物流甲");
            assertThat(option.value()).isEqualTo(1L);
            assertThat(option.carrierName()).isEqualTo("物流甲");
            assertThat(option.vehicles())
                    .extracting("id", "value", "label", "plate")
                    .containsExactly(
                            tuple(101L, 101L, "苏A12345", "苏A12345"),
                            tuple(102L, 102L, "苏A67890", "苏A67890"),
                            tuple(103L, 103L, "苏A99999", "苏A99999")
                    );
            assertThat(option.vehiclePlates()).containsExactly("苏A12345", "苏A67890", "苏A99999");
            assertThat(option.defaultSettlementCompanyId()).isEqualTo(9L);
            assertThat(option.defaultSettlementCompanyName()).isEqualTo("上海结算主体");
        });
    }

    @Test
    void shouldKeepSameNameCarriersAsDistinctStableOptions() {
        Carrier first = createCarrier(101L, "CR001");
        Carrier second = createCarrier(102L, "CR002");
        first.setCarrierName("同名物流商");
        second.setCarrierName("同名物流商");
        CarrierRepository repository = mock(CarrierRepository.class);
        when(repository.findByDeletedFlagFalseAndStatusOrderByCarrierCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(first, second));
        CarrierService service = new CarrierService(
                repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), mock(CarrierMapper.class));

        List<CarrierOptionResponse> options = service.listActiveOptions();

        assertThat(options).extracting(CarrierOptionResponse::id).containsExactly(101L, 102L);
        assertThat(options).extracting(CarrierOptionResponse::value).containsExactly(101L, 102L);
    }

    @Test
    void shouldFailClosedWhenVehicleOptionHasNoStableId() {
        Carrier carrier = createCarrier(1L, "CR001");
        carrier.setVehicles(List.of(createVehicle(null, carrier, "苏A12345")));
        CarrierRepository repository = mock(CarrierRepository.class);
        when(repository.findByDeletedFlagFalseAndStatusOrderByCarrierCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(carrier));
        CarrierService service = new CarrierService(
                repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), mock(CarrierMapper.class));

        assertThatThrownBy(service::listActiveOptions)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("车辆选项")
                .hasMessageContaining("稳定ID");
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

        assertThatThrownBy(() -> service.create(new CarrierRequest("CR001", "物流甲", null, null, null, null, null, 1L, "正常", null)))
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

        assertThatThrownBy(() -> service.update(1L, new CarrierRequest("CR002", "物流乙", null, null, null, null, null, 1L, "正常", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流方编码已存在");
    }

    @Test
    void shouldUpdateSuccessfully_whenCarrierCodeChangedAndNewCodeUnique() {
        Carrier existing = createCarrier(1L, "CR001");
        CarrierRepository repository = mock(CarrierRepository.class);
        CarrierMapper mapper = mock(CarrierMapper.class);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByCarrierCodeAndDeletedFlagFalse("CR002")).thenReturn(false);
        when(repository.save(any(Carrier.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Carrier.class))).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
        var service = new CarrierService(repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), mapper);

        var request = new CarrierRequest("CR002", "物流乙", null, null, null, null, "按吨", 1L, "正常", null);
        var result = service.update(1L, request);

        assertThat(result.carrierCode()).isEqualTo("CR002");
        assertThat(result.carrierName()).isEqualTo("物流乙");
        verify(repository).existsByCarrierCodeAndDeletedFlagFalse("CR002");
        verify(repository).save(existing);
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

        var request = new CarrierRequest("CR001", "物流甲", null, null, null, null, null, 1L, "正常", null);
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
        verify(cache, never()).deleteAfterCommit("leo:carrier:all");
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
        var request = new CarrierRequest("CR001", "物流甲", "张三", "13800138000", "重型卡车", vehicles, "按吨", 1L, "正常", "备注");
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
        var request = new CarrierRequest("CR001", "物流甲", "  ", "  ", "  ", vehicles, "按吨", 1L, "正常", "备注");
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
        assertThat(result.vehicles()).hasSize(1);
        assertThat(result.vehicles().get(0).plate()).isEqualTo("苏A12345");
    }

    @Test
    void shouldReuseCurrentCarrierVehicleIdForLegacyRequestWithSameNormalizedPlate() {
        Carrier existing = createCarrier(1L, "CR001");
        Vehicle vehicle = createVehicle(101L, existing, "苏A12345");
        existing.setVehicles(new ArrayList<>(List.of(vehicle)));
        CarrierRepository repository = mock(CarrierRepository.class);
        CarrierMapper mapper = mock(CarrierMapper.class);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Carrier.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Carrier.class)))
                .thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
        var service = new CarrierService(
                repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), mapper);
        CarrierRequest request = carrierRequest(List.of(
                new VehicleItem(" 苏a12345 ", "司机A", "13900000001", "未换车")
        ));

        CarrierResponse result = service.update(1L, request);

        assertThat(result.vehicles()).singleElement().satisfies(updated -> {
            assertThat(updated.id()).isEqualTo(101L);
            assertThat(updated.plate()).isEqualTo("苏A12345");
        });
        assertThat(existing.getVehicles()).containsExactly(vehicle);
    }

    @Test
    void shouldUpdateVehicleAttributesInPlaceWhenVehicleIdIsProvided() {
        Carrier existing = createCarrier(1L, "CR001");
        Vehicle vehicle = createVehicle(101L, existing, "苏A12345");
        vehicle.setContact("旧司机");
        existing.setVehicles(new ArrayList<>(List.of(vehicle)));
        CarrierRepository repository = mock(CarrierRepository.class);
        CarrierMapper mapper = mock(CarrierMapper.class);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Carrier.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Carrier.class)))
                .thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
        var service = new CarrierService(
                repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), mapper);
        CarrierRequest request = carrierRequest(List.of(
                new VehicleItem(101L, "苏A12345", "新司机", "13900000002", "属性更新")
        ));

        CarrierResponse result = service.update(1L, request);

        assertThat(result.vehicles()).singleElement().satisfies(updated -> {
            assertThat(updated.id()).isEqualTo(101L);
            assertThat(updated.vehicleId()).isEqualTo(101L);
            assertThat(updated.contact()).isEqualTo("新司机");
            assertThat(updated.phone()).isEqualTo("13900000002");
            assertThat(updated.remark()).isEqualTo("属性更新");
        });
        assertThat(existing.getVehicles()).containsExactly(vehicle);
    }

    @Test
    void shouldRejectRemovingVehicleReferencedByFreightBill() {
        Carrier existing = createCarrier(1L, "CR001");
        Vehicle vehicle = createVehicle(101L, existing, "苏A12345");
        existing.setVehicles(new ArrayList<>(List.of(vehicle)));
        CarrierRepository repository = mock(CarrierRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        MasterDataReferenceGuard referenceGuard = mock(MasterDataReferenceGuard.class);
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "该车辆已被物流单引用"))
                .when(referenceGuard).assertNoReferences(eq("该车辆"), any(List.class));
        var service = new CarrierService(
                repository,
                mock(VehicleRepository.class),
                new SnowflakeIdGenerator(1),
                mock(CarrierMapper.class),
                null,
                referenceGuard
        );

        assertThatThrownBy(() -> service.update(1L, carrierRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该车辆已被物流单引用");

        ArgumentCaptor<List<ReferenceCheck>> captor = ArgumentCaptor.forClass(List.class);
        verify(referenceGuard).assertNoReferences(eq("该车辆"), captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(check -> {
            assertThat(check.tableName()).isEqualTo("lg_freight_bill");
            assertThat(check.columnName()).isEqualTo("vehicle_id");
            assertThat(check.value()).isEqualTo(101L);
            assertThat(check.activeOnly()).isFalse();
        });
        assertThat(existing.getVehicles()).containsExactly(vehicle);
    }

    @Test
    void shouldRejectVehicleIdOwnedByAnotherCarrier() {
        Carrier existing = createCarrier(1L, "CR001");
        Vehicle vehicle = createVehicle(101L, existing, "苏A12345");
        existing.setVehicles(new ArrayList<>(List.of(vehicle)));
        CarrierRepository repository = mock(CarrierRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        var service = new CarrierService(
                repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), mock(CarrierMapper.class));
        CarrierRequest request = carrierRequest(List.of(
                new VehicleItem(202L, "苏B12345", null, null, null)
        ));

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("车辆ID不属于当前物流商");
        assertThat(existing.getVehicles()).containsExactly(vehicle);
    }

    @Test
    void shouldNotReuseSamePlateVehicleFromAnotherCarrier() {
        Carrier current = createCarrier(1L, "CR001");
        current.setVehicles(new ArrayList<>());
        Carrier other = createCarrier(2L, "CR002");
        Vehicle otherVehicle = createVehicle(202L, other, "苏A12345");
        CarrierRepository repository = mock(CarrierRepository.class);
        CarrierMapper mapper = mock(CarrierMapper.class);
        VehicleRepository vehicleRepository = mock(VehicleRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(current));
        when(repository.save(any(Carrier.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Carrier.class)))
                .thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
        when(vehicleRepository.findByCarrierIdOrderBySortOrderAsc(2L)).thenReturn(List.of(otherVehicle));
        var service = new CarrierService(
                repository, vehicleRepository, new SnowflakeIdGenerator(1), mapper);

        CarrierResponse result = service.update(1L, carrierRequest(List.of(
                new VehicleItem("苏A12345", null, null, null)
        )));

        assertThat(result.vehicles()).singleElement().satisfies(created -> {
            assertThat(created.id()).isNotEqualTo(202L);
            assertThat(created.plate()).isEqualTo("苏A12345");
        });
        assertThat(current.getVehicles()).singleElement()
                .satisfies(created -> assertThat(created.getCarrier()).isSameAs(current));
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

        var request = new CarrierRequest("CR001", "物流甲", null, null, null, null, "按吨", 1L, "正常", null);
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
    void shouldCheckStableCarrierIdentityBeforeDelete() {
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

        ArgumentCaptor<List<ReferenceCheck>> captor = ArgumentCaptor.forClass(List.class);
        verify(referenceGuard).assertNoReferences(eq("该物流商"), captor.capture());
        assertThat(captor.getValue())
                .extracting(ReferenceCheck::tableName, ReferenceCheck::columnName, ReferenceCheck::value)
                .containsExactly(
                        tuple("lg_freight_bill", "carrier_id", 1L),
                        tuple("st_freight_statement", "carrier_id", 1L),
                        tuple("fm_payment", "counterparty_id", 1L),
                        tuple("fm_ledger_adjustment", "counterparty_id", 1L)
                );
        assertThat(captor.getValue().subList(2, 4)).allSatisfy(check -> {
            assertThat(check.extraCondition()).isEqualTo("counterparty_type = ?");
            assertThat(check.extraArguments()).containsExactly("物流商");
        });
    }

    @Test
    void shouldDeleteSuccessfully_whenReferenceGuardIsNull() {
        Carrier existing = createCarrier(1L, "CR001");
        CarrierRepository repository = mock(CarrierRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Carrier.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new CarrierService(repository, mock(VehicleRepository.class), new SnowflakeIdGenerator(1), null);

        service.delete(1L);

        assertThat(existing.isDeletedFlag()).isTrue();
        verify(repository).save(existing);
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

        var request = new CarrierRequest("CR001", "物流甲", "张三", "13800138000", "重型卡车", null, "按吨", 1L, "正常", "备注");
        var result = service.create(request);

        assertThat(result).isNotNull();
        assertThat(result.carrierCode()).isEqualTo("CR001");
    }

    @Test
    void shouldResolveDefaultSettlementCompanyName_whenCompanySettingServiceProvided() {
        CarrierRepository repository = mock(CarrierRepository.class);
        CarrierMapper mapper = mock(CarrierMapper.class);
        CompanySettingService companySettingService = mock(CompanySettingService.class);
        CompanySetting company = new CompanySetting();
        company.setId(9L);
        company.setCompanyName("上海结算主体");
        when(repository.existsByCarrierCodeAndDeletedFlagFalse("CR001")).thenReturn(false);
        when(repository.save(any(Carrier.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Carrier.class))).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
        when(companySettingService.requireActiveSettlementCompany(9L)).thenReturn(company);
        var service = new CarrierService(
                repository,
                mock(VehicleRepository.class),
                new SnowflakeIdGenerator(1),
                mapper,
                null,
                null,
                companySettingService
        );

        var request = new CarrierRequest("CR001", "物流甲", null, null, null, null, "按吨", 9L, "正常", null);
        var result = service.create(request);

        assertThat(result.defaultSettlementCompanyId()).isEqualTo(9L);
        assertThat(result.defaultSettlementCompanyName()).isEqualTo("上海结算主体");
        verify(companySettingService).requireActiveSettlementCompany(9L);
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

        var request = new CarrierRequest("CR001", "物流乙", null, null, null, null, null, 1L, "正常", null);
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldTestPrivateUtilityMethods_viaReflection() throws Exception {
        CarrierService service = new CarrierService(null, null, new SnowflakeIdGenerator(1), null);

        Method emptyToNull = CarrierService.class.getDeclaredMethod("emptyToNull", String.class);
        emptyToNull.setAccessible(true);

        assertThat(emptyToNull.invoke(service, (Object) null)).isNull();
        assertThat(emptyToNull.invoke(service, "")).isNull();
        assertThat(emptyToNull.invoke(service, "  ")).isNull();
        assertThat(emptyToNull.invoke(service, "value")).isEqualTo("value");
        assertThat(emptyToNull.invoke(service, " value ")).isEqualTo("value");
    }

    @Test
    void shouldDeclareSpringCacheAnnotationsForOptions() throws Exception {
        Method readMethod = CarrierService.class.getDeclaredMethod("listActiveOptions");
        Cacheable cacheable = readMethod.getAnnotation(Cacheable.class);
        assertThat(cacheable.value()).containsExactly(CacheConfig.CACHE_OPTIONS);
        assertThat(cacheable.key()).isEqualTo("'leo:carrier:all'");

        Method createMethod = CarrierService.class.getDeclaredMethod("create", CarrierRequest.class);
        Method updateMethod = CarrierService.class.getDeclaredMethod("update", Long.class, CarrierRequest.class);
        Method updateStatusMethod = CarrierService.class.getDeclaredMethod("updateStatus", Long.class, String.class);
        Method deleteMethod = CarrierService.class.getDeclaredMethod("delete", Long.class);

        assertThat(createMethod.getAnnotation(CacheEvict.class).value()).containsExactly(CacheConfig.CACHE_OPTIONS);
        assertThat(createMethod.getAnnotation(CacheEvict.class).key()).isEqualTo("'leo:carrier:all'");
        assertThat(updateMethod.getAnnotation(CacheEvict.class).key()).isEqualTo("'leo:carrier:all'");
        assertThat(updateStatusMethod.getAnnotation(CacheEvict.class).key()).isEqualTo("'leo:carrier:all'");
        assertThat(deleteMethod.getAnnotation(CacheEvict.class).key()).isEqualTo("'leo:carrier:all'");
    }

    @Test
    void shouldRefreshActualSpringCarrierCacheDuringHealthCheck() {
        CarrierRepository repository = mock(CarrierRepository.class);
        Carrier carrier = createCarrier(1L, "CR001");
        when(repository.findByDeletedFlagFalseAndStatusOrderByCarrierCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(carrier));
        RedisJsonCacheSupport legacyCache = mock(RedisJsonCacheSupport.class);
        CarrierService service = new CarrierService(
                repository,
                mock(VehicleRepository.class),
                new SnowflakeIdGenerator(1),
                mock(CarrierMapper.class),
                legacyCache
        );
        var cacheManager = new ConcurrentMapCacheManager(CacheConfig.CACHE_OPTIONS);
        cacheManager.getCache(CacheConfig.CACHE_OPTIONS).put("leo:carrier:all", List.of("stale"));
        service.setCacheManager(cacheManager);

        var result = service.verifyAndRefreshCache();

        assertThat(result.cacheName()).isEqualTo("options::leo:carrier:all");
        assertThat(result.refreshed()).isTrue();
        assertThat(cacheManager.getCache(CacheConfig.CACHE_OPTIONS).get("leo:carrier:all", List.class))
                .singleElement()
                .isInstanceOf(CarrierOptionResponse.class);
        verify(legacyCache, never()).write(anyString(), any(), any());
    }

    private static Carrier createCarrier(Long id, String code) {
        Carrier c = new Carrier();
        c.setId(id);
        c.setCarrierCode(code);
        c.setCarrierName("物流甲");
        c.setStatus(StatusConstants.NORMAL);
        return c;
    }

    private static Vehicle createVehicle(Long id, Carrier carrier, String plate) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setCarrier(carrier);
        vehicle.setPlate(plate);
        vehicle.setSortOrder(0);
        return vehicle;
    }

    private static CarrierRequest carrierRequest(List<VehicleItem> vehicles) {
        return new CarrierRequest(
                "CR001", "物流甲", null, null, null, vehicles,
                "按吨", 1L, StatusConstants.NORMAL, null
        );
    }

    private static CarrierResponse toResponse(Carrier c) {
        List<VehicleInfo> vehicles = c.getVehicles() == null ? List.of() :
                c.getVehicles().stream()
                        .map(v -> new VehicleInfo(v.getId(), v.getPlate(), v.getContact(), v.getPhone(), v.getRemark()))
                        .toList();
        return new CarrierResponse(
                c.getId(), c.getCarrierCode(), c.getCarrierName(),
                c.getContactName(), c.getContactPhone(), c.getVehicleType(),
                vehicles, c.getPriceMode(), c.getDefaultSettlementCompanyId(),
                c.getDefaultSettlementCompanyName(), c.getStatus(), c.getRemark()
        );
    }
}
