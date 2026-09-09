package com.leo.erp.master.supplier.service;

import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.service.ReferenceSnapshotSyncService;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.mapper.SupplierMapper;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.master.supplier.web.dto.SupplierRequest;
import com.leo.erp.master.supplier.web.dto.SupplierResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private MasterDataReferenceGuard referenceGuard;
    @Mock
    private MasterDataCodeIssuanceService codeIssuanceService;
    @Mock
    private ReferenceSnapshotSyncService referenceSnapshotSyncService;

    private SupplierService service() {
        return new SupplierService(supplierRepository, snowflakeIdGenerator, supplierMapper,
                referenceGuard, codeIssuanceService, referenceSnapshotSyncService);
    }

    private SupplierRequest request(String supplierName) {
        return new SupplierRequest("GYS001", supplierName, "联系人", "13800000000",
                "上海", "正常", "备注");
    }

    @Test
    void create_assignsSnowflakeIdAppliesRequestAndConsumesCode() {
        Supplier[] savedHolder = new Supplier[1];
        when(snowflakeIdGenerator.nextId()).thenReturn(77L);
        when(codeIssuanceService.resolve("supplier", null, "GYS001")).thenReturn("GYS001");
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });
        SupplierResponse response = new SupplierResponse(77L, "GYS001", "供应商A",
                "联系人", "13800000000", "上海", "正常", "备注");
        when(supplierMapper.toResponse(any(Supplier.class))).thenReturn(response);

        SupplierResponse result = service().create(request("供应商A"));

        Supplier entity = savedHolder[0];
        assertThat(entity.getId()).isEqualTo(77L);
        assertThat(entity.getSupplierCode()).isEqualTo("GYS001");
        assertThat(entity.getSupplierName()).isEqualTo("供应商A");
        assertThat(entity.getStatus()).isEqualTo("正常");
        assertThat(result.id()).isEqualTo(77L);
        assertThat(result.supplierName()).isEqualTo("供应商A");
        verify(codeIssuanceService).validate("supplier", "GYS001");
        verify(codeIssuanceService).consume("supplier", "GYS001");
    }

    @Test
    void create_codeValidationFailure_rejectedBeforeSave() {
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "编码已占用"))
                .when(codeIssuanceService).validate("supplier", "GYS001");

        assertThatThrownBy(() -> service().create(request("供应商A")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("编码已占用");
        verify(supplierRepository, never()).save(any());
        verify(codeIssuanceService, never()).consume(anyString(), anyString());
    }

    @Test
    void detail_notFound_throwsWithModuleMessage() {
        when(supplierRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().detail(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND))
                .hasMessage("供应商不存在");
    }

    @Test
    void update_nameChanged_syncsReferenceSnapshot() {
        Supplier entity = new Supplier();
        entity.setId(5L);
        entity.setSupplierName("旧名称");
        entity.setSupplierCode("GYS001");
        when(supplierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(supplierRepository.save(entity)).thenReturn(entity);
        SupplierResponse response = new SupplierResponse(5L, "GYS001", "新名称",
                null, null, null, "正常", null);
        when(supplierMapper.toResponse(entity)).thenReturn(response);

        service().update(5L, request("新名称"));

        verify(referenceSnapshotSyncService).syncSupplierName(5L, "新名称");
    }

    @Test
    void update_nameUnchanged_doesNotSyncReferenceSnapshot() {
        Supplier entity = new Supplier();
        entity.setId(5L);
        entity.setSupplierName("供应商A");
        entity.setSupplierCode("GYS001");
        when(supplierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(supplierRepository.save(entity)).thenReturn(entity);
        SupplierResponse response = new SupplierResponse(5L, "GYS001", "供应商A",
                null, null, null, "正常", null);
        when(supplierMapper.toResponse(entity)).thenReturn(response);

        service().update(5L, request("供应商A"));

        verify(referenceSnapshotSyncService, never()).syncSupplierName(any(), anyString());
    }

    @Test
    void updateStatus_blank_rejectedWithValidationMessage() {
        when(supplierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(new Supplier()));

        assertThatThrownBy(() -> service().updateStatus(5L, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("状态不能为空");
    }

    @Test
    void updateStatus_anyStatus_rejectedAsUnsupported() {
        when(supplierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(new Supplier()));

        assertThatThrownBy(() -> service().updateStatus(5L, "正常"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.BUSINESS_ERROR))
                .hasMessage("当前模块不支持状态变更");
    }

    @Test
    void delete_referenced_blockedBeforeSoftDelete() {
        Supplier entity = new Supplier();
        entity.setId(5L);
        when(supplierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "该供应商被引用，无法删除"))
                .when(referenceGuard).assertNoReferences(eq("该供应商"), any());

        assertThatThrownBy(() -> service().delete(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该供应商被引用，无法删除");
        assertThat(entity.isDeletedFlag()).isFalse();
        verify(supplierRepository, never()).save(any());
    }

    @Test
    void delete_softDeletesEntity() {
        Supplier entity = new Supplier();
        entity.setId(5L);
        when(supplierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(supplierRepository.save(entity)).thenReturn(entity);

        service().delete(5L);

        assertThat(entity.isDeletedFlag()).isTrue();
        verify(supplierRepository).save(entity);
    }

    @Test
    void delete_nullReferenceGuard_toleratedAsBefore() {
        SupplierService nullGuardService = new SupplierService(supplierRepository, snowflakeIdGenerator,
                supplierMapper, null, codeIssuanceService, referenceSnapshotSyncService);
        Supplier entity = new Supplier();
        entity.setId(5L);
        when(supplierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(supplierRepository.save(entity)).thenReturn(entity);

        nullGuardService.delete(5L);

        assertThat(entity.isDeletedFlag()).isTrue();
        verify(referenceGuard, never()).assertNoReferences(anyString(), any());
    }

    @Test
    void cacheAnnotations_preservedOnPublicWriteAndReadMethods() throws Exception {
        Method create = SupplierService.class.getMethod("create", SupplierRequest.class);
        Method update = SupplierService.class.getMethod("update", Long.class, SupplierRequest.class);
        Method updateStatus = SupplierService.class.getMethod("updateStatus", Long.class, String.class);
        Method delete = SupplierService.class.getMethod("delete", Long.class);
        Method listActiveOptions = SupplierService.class.getMethod("listActiveOptions");

        for (Method method : List.of(create, update, updateStatus, delete)) {
            CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);
            assertThat(cacheEvict).as(method.getName()).isNotNull();
            assertThat(cacheEvict.value()).containsExactly(CacheConfig.CACHE_OPTIONS);
            assertThat(cacheEvict.key())
                    .isEqualTo("'leo:supplier:all'");
        }
        Cacheable cacheable = listActiveOptions.getAnnotation(Cacheable.class);
        assertThat(cacheable).isNotNull();
        assertThat(cacheable.value()).containsExactly(CacheConfig.CACHE_OPTIONS);
        assertThat(cacheable.key()).isEqualTo("'leo:supplier:all'");
        assertThat(cacheable.unless()).isEqualTo("#result == null || #result.isEmpty()");
    }
}
