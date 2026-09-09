package com.leo.erp.master.carrier.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.mapper.CarrierMapper;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.master.carrier.repository.VehicleRepository;
import com.leo.erp.master.carrier.web.dto.CarrierRequest;
import com.leo.erp.master.carrier.web.dto.VehicleItem;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.service.ReferenceSnapshotSyncService;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarrierServiceTest {

    @Mock
    private CarrierRepository carrierRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Mock
    private CarrierMapper carrierMapper;
    @Mock
    private MasterDataReferenceGuard referenceGuard;
    @Mock
    private CompanySettingService companySettingService;
    @Mock
    private MasterDataCodeIssuanceService codeIssuanceService;
    @Mock
    private ReferenceSnapshotSyncService referenceSnapshotSyncService;

    private CarrierService service() {
        return new CarrierService(carrierRepository, vehicleRepository, snowflakeIdGenerator, carrierMapper,
                referenceGuard, companySettingService, codeIssuanceService, referenceSnapshotSyncService);
    }

    private CarrierRequest request(String carrierName) {
        return new CarrierRequest("111", carrierName, "联系人", "13800000000", "厢式",
                List.of(new VehicleItem("沪a1234", "司机", "13900000000", null)),
                "满载", 9L, "正常", "备注");
    }

    @Test
    void create_assignsSnowflakeIdAndResolvesSettlementCompany() {
        Carrier[] savedHolder = new Carrier[1];
        CompanySetting company = new CompanySetting();
        company.setId(9L);
        company.setCompanyName("结算公司C");
        when(snowflakeIdGenerator.nextId()).thenReturn(77L, 78L);
        when(codeIssuanceService.resolve("carrier", null, "111")).thenReturn("111");
        when(companySettingService.requireActiveSettlementCompany(9L)).thenReturn(company);
        when(carrierRepository.saveAndFlush(any(Carrier.class))).thenAnswer(invocation -> {
            Carrier entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });

        service().create(request("承运A"));

        Carrier entity = savedHolder[0];
        assertThat(entity.getId()).isEqualTo(77L);
        assertThat(entity.getCarrierCode()).isEqualTo("111");
        assertThat(entity.getCarrierName()).isEqualTo("承运A");
        assertThat(entity.getDefaultSettlementCompanyId()).isEqualTo(9L);
        assertThat(entity.getDefaultSettlementCompanyName()).isEqualTo("结算公司C");
        assertThat(entity.getStatus()).isEqualTo("正常");
        assertThat(entity.getVehicles()).hasSize(1);
        assertThat(entity.getVehicles().getFirst().getPlate()).isEqualTo("沪A1234");
        assertThat(entity.getVehicles().getFirst().getId()).isEqualTo(78L);
        verify(codeIssuanceService).validate("carrier", "111");
        verify(codeIssuanceService).consume("carrier", "111");
    }

    @Test
    void create_duplicateName_rejectedBeforeSave() {
        when(carrierRepository.countActiveByCarrierName("承运A")).thenReturn(1L);

        assertThatThrownBy(() -> service().create(request("承运A")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.BUSINESS_ERROR))
                .hasMessage("物流商名称已存在：承运A");
        verify(carrierRepository, never()).saveAndFlush(any());
        verify(codeIssuanceService, never()).consume(anyString(), anyString());
    }

    @Test
    void create_blankName_rejected() {
        assertThatThrownBy(() -> service().create(request("   ")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("物流方名称不能为空");
        verify(carrierRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_nameLongerThan128_rejected() {
        assertThatThrownBy(() -> service().create(request("承".repeat(129))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("物流方名称不能超过128个字符");
        verify(carrierRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_duplicateVehiclePlate_rejected() {
        CarrierRequest request = new CarrierRequest("111", "承运A", null, null, null,
                List.of(new VehicleItem("沪A1234", null, null, null),
                        new VehicleItem("沪A1234", null, null, null)),
                null, 9L, "正常", null);

        assertThatThrownBy(() -> service().create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一物流商不能配置重复车牌");
        verify(carrierRepository, never()).saveAndFlush(any());
    }

    @Test
    void detail_notFound_throwsWithModuleMessage() {
        when(carrierRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().detail(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND))
                .hasMessage("物流方不存在");
    }

    @Test
    void update_duplicateName_rejectedBeforeSave() {
        Carrier entity = new Carrier();
        entity.setId(5L);
        entity.setCarrierName("旧名称");
        entity.setCarrierCode("C001");
        when(carrierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(carrierRepository.countOtherActiveByCarrierName("新名称", 5L)).thenReturn(1L);

        assertThatThrownBy(() -> service().update(5L, request("新名称")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("物流商名称已存在：新名称");
        verify(carrierRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_nameChanged_syncsReferenceSnapshot() {
        Carrier entity = new Carrier();
        entity.setId(5L);
        entity.setCarrierName("旧名称");
        entity.setCarrierCode("C001");
        when(carrierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(carrierRepository.saveAndFlush(entity)).thenReturn(entity);
        CompanySetting company = new CompanySetting();
        company.setId(9L);
        company.setCompanyName("结算公司C");
        when(companySettingService.requireActiveSettlementCompany(9L)).thenReturn(company);

        service().update(5L, request("新名称"));

        verify(referenceSnapshotSyncService).syncCarrierName(5L, "新名称");
    }

    @Test
    void updateStatus_blank_rejectedWithValidationMessage() {
        when(carrierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(new Carrier()));

        assertThatThrownBy(() -> service().updateStatus(5L, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("状态不能为空");
    }

    @Test
    void updateStatus_anyStatus_rejectedAsUnsupported() {
        when(carrierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(new Carrier()));

        assertThatThrownBy(() -> service().updateStatus(5L, "正常"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.BUSINESS_ERROR))
                .hasMessage("当前模块不支持状态变更");
    }

    @Test
    void delete_referenced_blockedBeforeSoftDelete() {
        Carrier entity = new Carrier();
        entity.setId(5L);
        when(carrierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "物流商被引用"))
                .when(referenceGuard).assertNoReferences(eq("该物流商"), any());

        assertThatThrownBy(() -> service().delete(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("物流商被引用");
        assertThat(entity.isDeletedFlag()).isFalse();
        verify(carrierRepository, never()).saveAndFlush(any());
    }

    @Test
    void delete_softDeletesEntityAndKeepsVehicles() {
        Carrier entity = new Carrier();
        entity.setId(5L);
        when(carrierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(carrierRepository.saveAndFlush(entity)).thenReturn(entity);

        service().delete(5L);

        assertThat(entity.isDeletedFlag()).isTrue();
        verify(carrierRepository).saveAndFlush(entity);
        verify(vehicleRepository, never()).saveAll(any());
    }

    @Test
    void cacheAnnotations_preservedOnPublicWriteAndReadMethods() throws Exception {
        Method create = CarrierService.class.getMethod("create", CarrierRequest.class);
        Method update = CarrierService.class.getMethod("update", Long.class, CarrierRequest.class);
        Method updateStatus = CarrierService.class.getMethod("updateStatus", Long.class, String.class);
        Method delete = CarrierService.class.getMethod("delete", Long.class);
        Method listActiveOptions = CarrierService.class.getMethod("listActiveOptions");

        for (Method method : List.of(create, update, updateStatus, delete)) {
            CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);
            assertThat(cacheEvict).as(method.getName()).isNotNull();
            assertThat(cacheEvict.value()).containsExactly(CacheConfig.CACHE_OPTIONS);
        }
        Cacheable cacheable = listActiveOptions.getAnnotation(Cacheable.class);
        assertThat(cacheable).isNotNull();
        assertThat(cacheable.value()).containsExactly(CacheConfig.CACHE_OPTIONS);
    }
}
