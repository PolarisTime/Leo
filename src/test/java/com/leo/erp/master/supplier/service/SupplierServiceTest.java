package com.leo.erp.master.supplier.service;

import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.service.ReferenceSnapshotSyncService;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.domain.entity.SupplierBrand;
import com.leo.erp.master.supplier.mapper.SupplierMapper;
import com.leo.erp.master.supplier.repository.SupplierBrandRepository;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.master.supplier.web.dto.SupplierOptionResponse;
import com.leo.erp.master.supplier.web.dto.SupplierRequest;
import com.leo.erp.master.supplier.web.dto.SupplierResponse;
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
import static org.mockito.Mockito.times;
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
    @Mock
    private SupplierBrandRepository supplierBrandRepository;

    private SupplierService service() {
        return new SupplierService(supplierRepository, snowflakeIdGenerator, supplierMapper,
                referenceGuard, codeIssuanceService, referenceSnapshotSyncService, supplierBrandRepository);
    }

    private SupplierRequest request(String supplierName) {
        return new SupplierRequest("GYS001", supplierName, "联系人", "13800000000",
                "上海", "正常", "备注");
    }

    private SupplierRequest requestWithBrands(String supplierName, List<String> brands) {
        return new SupplierRequest("GYS001", supplierName, null, "联系人", "13800000000",
                "上海", "正常", "备注", brands);
    }

    private SupplierBrand brand(long id, long supplierId, String name) {
        SupplierBrand brand = new SupplierBrand();
        brand.setId(id);
        brand.setSupplierId(supplierId);
        brand.setBrandName(name);
        return brand;
    }

    private Supplier supplier(long id, String code, String name, String shortName) {
        Supplier supplier = new Supplier();
        supplier.setId(id);
        supplier.setSupplierCode(code);
        supplier.setSupplierName(name);
        supplier.setShortName(shortName);
        return supplier;
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
        assertThat(result.brands()).isEmpty();
        verify(codeIssuanceService).validate("supplier", "GYS001");
        verify(codeIssuanceService).consume("supplier", "GYS001");
    }

    @Test
    void create_trimsUserVisibleStringsAndNormalizesBlankOptionalFields() {
        Supplier[] savedHolder = new Supplier[1];
        when(snowflakeIdGenerator.nextId()).thenReturn(77L);
        when(codeIssuanceService.resolve(eq("supplier"), any(), anyString())).thenReturn("S001");
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });
        when(supplierMapper.toResponse(any(Supplier.class))).thenReturn(null);

        service().create(new SupplierRequest("  S001  ", "  供应商A  ", "  ", null, " 上海 ", "正常", "  "));

        Supplier entity = savedHolder[0];
        assertThat(entity.getSupplierName()).isEqualTo("供应商A");
        assertThat(entity.getContactName()).isNull();
        assertThat(entity.getContactPhone()).isNull();
        assertThat(entity.getCity()).isEqualTo("上海");
        assertThat(entity.getRemark()).isNull();
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
    void create_brands_dedupesSortsAndPersistsOnlyNewNames() {
        when(snowflakeIdGenerator.nextId()).thenReturn(77L, 201L, 202L);
        when(codeIssuanceService.resolve(eq("supplier"), any(), anyString())).thenReturn("S001");
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(supplierBrandRepository.findBySupplierIdOrderByBrandNameAsc(77L)).thenReturn(List.of());
        when(supplierMapper.toResponse(any(Supplier.class))).thenReturn(null);

        SupplierResponse result = service().create(
                requestWithBrands("供应商A", List.of(" 永钢 ", "沙钢", "沙钢", "  永钢  ")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<SupplierBrand> captor = ArgumentCaptor.forClass(SupplierBrand.class);
        verify(supplierBrandRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(SupplierBrand::getBrandName)
                .containsExactly("永钢", "沙钢");
        assertThat(captor.getAllValues()).extracting(SupplierBrand::getSupplierId)
                .containsOnly(77L);
        assertThat(captor.getAllValues()).extracting(SupplierBrand::getId)
                .containsExactly(201L, 202L);
        assertThat(result).isNull();
    }

    @Test
    void create_blankBrand_rejectedAs422BeforeSave() {
        assertThatThrownBy(() -> service().create(requestWithBrands("供应商A", List.of(" "))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("品牌名称不能为空");
        verify(supplierRepository, never()).save(any());
        verify(supplierBrandRepository, never()).save(any());
    }

    @Test
    void create_overlongBrand_rejectedAs422BeforeSave() {
        String overlong = "钢".repeat(65);

        assertThatThrownBy(() -> service().create(requestWithBrands("供应商A", List.of(overlong))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("品牌名称长度不能超过64个字符");
        verify(supplierRepository, never()).save(any());
    }

    @Test
    void update_brandSetChanged_deletesUnreferencedAddsMissingAndReusesSameName() {
        Supplier entity = supplier(5L, "GYS001", "供应商A", null);
        when(supplierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(supplierRepository.save(entity)).thenReturn(entity);
        when(supplierMapper.toResponse(entity)).thenReturn(new SupplierResponse(
                5L, "GYS001", "供应商A", null, null, null, "正常", null));
        SupplierBrand keep = brand(11L, 5L, "沙钢");
        SupplierBrand remove = brand(12L, 5L, "旧品牌");
        when(supplierBrandRepository.findBySupplierIdOrderByBrandNameAsc(5L))
                .thenReturn(List.of(keep, remove));
        when(snowflakeIdGenerator.nextId()).thenReturn(99L);

        service().update(5L, requestWithBrands("供应商A", List.of("沙钢", "永钢")));

        // 未再被引用的品牌改为软删, 不再物理删除
        verify(supplierBrandRepository, never()).delete(any());
        assertThat(remove.isDeletedFlag()).isTrue();
        assertThat(keep.isDeletedFlag()).isFalse();
        ArgumentCaptor<SupplierBrand> captor = ArgumentCaptor.forClass(SupplierBrand.class);
        verify(supplierBrandRepository, times(2)).save(captor.capture());
        assertThat(captor.getValue().getBrandName()).isEqualTo("永钢");
        assertThat(captor.getValue().getSupplierId()).isEqualTo(5L);
        assertThat(captor.getValue().getId()).isEqualTo(99L);
    }

    @Test
    void update_brandSetUnchanged_noWritesAtAll() {
        Supplier entity = supplier(5L, "GYS001", "供应商A", null);
        when(supplierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(supplierRepository.save(entity)).thenReturn(entity);
        when(supplierMapper.toResponse(entity)).thenReturn(new SupplierResponse(
                5L, "GYS001", "供应商A", null, null, null, "正常", null));
        when(supplierBrandRepository.findBySupplierIdOrderByBrandNameAsc(5L))
                .thenReturn(List.of(brand(11L, 5L, "沙钢"), brand(12L, 5L, "永钢")));

        service().update(5L, requestWithBrands("供应商A", List.of("永钢", "沙钢")));

        verify(supplierBrandRepository, never()).delete(any());
        verify(supplierBrandRepository, never()).save(any());
    }

    @Test
    void update_emptyBrandList_clearsAllExistingBrands() {
        Supplier entity = supplier(5L, "GYS001", "供应商A", null);
        when(supplierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(supplierRepository.save(entity)).thenReturn(entity);
        when(supplierMapper.toResponse(entity)).thenReturn(new SupplierResponse(
                5L, "GYS001", "供应商A", null, null, null, "正常", null));
        SupplierBrand first = brand(11L, 5L, "沙钢");
        SupplierBrand second = brand(12L, 5L, "永钢");
        when(supplierBrandRepository.findBySupplierIdOrderByBrandNameAsc(5L))
                .thenReturn(List.of(first, second));

        service().update(5L, requestWithBrands("供应商A", List.of()));

        // 清空品牌改为全部软删
        verify(supplierBrandRepository, never()).delete(any());
        assertThat(first.isDeletedFlag()).isTrue();
        assertThat(second.isDeletedFlag()).isTrue();
        verify(supplierBrandRepository, times(2)).save(any());
    }

    @Test
    void update_nullBrands_leavesExistingBrandsUntouchedAndReturnsThem() {
        Supplier entity = supplier(5L, "GYS001", "供应商A", null);
        when(supplierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(supplierRepository.save(entity)).thenReturn(entity);
        when(supplierMapper.toResponse(entity)).thenReturn(new SupplierResponse(
                5L, "GYS001", "供应商A", null, null, null, "正常", null));
        when(supplierBrandRepository.findBySupplierIdOrderByBrandNameAsc(5L))
                .thenReturn(List.of(brand(11L, 5L, "永钢"), brand(12L, 5L, "沙钢")));

        SupplierResponse response = service().update(5L, request("供应商A"));

        assertThat(response.brands()).containsExactly("永钢", "沙钢");
        verify(supplierBrandRepository, never()).delete(any());
        verify(supplierBrandRepository, never()).save(any());
    }

    @Test
    void detail_returnsBrands() {
        Supplier entity = supplier(5L, "GYS001", "供应商A", null);
        when(supplierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(supplierBrandRepository.findBySupplierIdAndDeletedFlagFalseOrderByBrandNameAsc(5L))
                .thenReturn(List.of(brand(11L, 5L, "沙钢")));
        when(supplierMapper.toResponse(entity)).thenReturn(new SupplierResponse(
                5L, "GYS001", "供应商A", null, null, null, "正常", null));

        SupplierResponse response = service().detail(5L);

        assertThat(response.brands()).containsExactly("沙钢");
    }

    @Test
    void options_returnsBrandsPerSupplierInRepositoryOrder() {
        when(supplierRepository.findByDeletedFlagFalseAndStatusOrderBySupplierCodeAsc("正常"))
                .thenReturn(List.of(
                        supplier(101L, "S001", "供应商A", "甲"),
                        supplier(102L, "S002", "供应商B", null),
                        supplier(103L, "S003", "无品牌供应商", null)));
        when(supplierBrandRepository.findBySupplierIdInAndDeletedFlagFalseOrderBySupplierIdAscBrandNameAsc(any()))
                .thenReturn(List.of(
                        brand(1L, 101L, "沙钢"),
                        brand(2L, 101L, "永钢"),
                        brand(3L, 102L, "中天")));

        List<SupplierOptionResponse> options = service().listActiveOptions();

        assertThat(options).hasSize(3);
        assertThat(options.get(0).brands()).containsExactly("沙钢", "永钢");
        assertThat(options.get(1).brands()).containsExactly("中天");
        assertThat(options.get(2).brands()).isEmpty();
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
        when(supplierBrandRepository.findBySupplierIdAndDeletedFlagFalseOrderByBrandNameAsc(5L))
                .thenReturn(List.of());

        service().delete(5L);

        assertThat(entity.isDeletedFlag()).isTrue();
        verify(supplierRepository).save(entity);
    }

    @Test
    void delete_nullReferenceGuard_toleratedAsBefore() {
        SupplierService nullGuardService = new SupplierService(supplierRepository, snowflakeIdGenerator,
                supplierMapper, null, codeIssuanceService, referenceSnapshotSyncService, supplierBrandRepository);
        Supplier entity = new Supplier();
        entity.setId(5L);
        when(supplierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(supplierRepository.save(entity)).thenReturn(entity);
        when(supplierBrandRepository.findBySupplierIdAndDeletedFlagFalseOrderByBrandNameAsc(5L))
                .thenReturn(List.of());

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
