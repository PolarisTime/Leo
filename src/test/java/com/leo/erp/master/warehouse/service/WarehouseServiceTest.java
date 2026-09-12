package com.leo.erp.master.warehouse.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.service.ReferenceSnapshotSyncService;
import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import com.leo.erp.master.warehouse.mapper.WarehouseMapper;
import com.leo.erp.master.warehouse.repository.WarehouseRepository;
import com.leo.erp.master.warehouse.web.dto.WarehouseRequest;
import com.leo.erp.master.warehouse.web.dto.WarehouseResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Mock
    private WarehouseMapper warehouseMapper;
    @Mock
    private MasterDataReferenceGuard referenceGuard;
    @Mock
    private MasterDataCodeIssuanceService codeIssuanceService;
    @Mock
    private ReferenceSnapshotSyncService referenceSnapshotSyncService;

    private WarehouseService service() {
        return new WarehouseService(warehouseRepository, snowflakeIdGenerator, warehouseMapper,
                referenceGuard, codeIssuanceService, referenceSnapshotSyncService);
    }

    private WarehouseRequest request(String name, String status) {
        return new WarehouseRequest("111", name, "实体仓", "联系人", "13800000000", "地址", status, "备注");
    }

    @Test
    void create_assignsSnowflakeIdAndNormalizesStatus() {
        Warehouse[] savedHolder = new Warehouse[1];
        when(snowflakeIdGenerator.nextId()).thenReturn(88L);
        when(codeIssuanceService.resolve("warehouse", null, "111")).thenReturn("111");
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> {
            Warehouse entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });
        when(warehouseMapper.toResponse(any(Warehouse.class))).thenReturn(null);

        service().create(request("仓库A", " 正常 "));

        Warehouse entity = savedHolder[0];
        assertThat(entity.getId()).isEqualTo(88L);
        assertThat(entity.getWarehouseCode()).isEqualTo("111");
        assertThat(entity.getWarehouseName()).isEqualTo("仓库A");
        assertThat(entity.getStatus()).isEqualTo("正常");
        verify(codeIssuanceService).validate("warehouse", "111");
        verify(codeIssuanceService).consume("warehouse", "111");
    }

    @Test
    void create_trimsUserVisibleStrings() {
        Warehouse[] savedHolder = new Warehouse[1];
        when(snowflakeIdGenerator.nextId()).thenReturn(88L);
        when(codeIssuanceService.resolve(eq("warehouse"), any(), anyString())).thenReturn("111");
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> {
            Warehouse entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });
        when(warehouseMapper.toResponse(any(Warehouse.class))).thenReturn(null);

        service().create(new WarehouseRequest("  111  ", "  仓库A  ", "  实体仓  ", " 联系人 ", " 138 ",
                " 地址 ", "正常", " 备注 "));

        Warehouse entity = savedHolder[0];
        assertThat(entity.getWarehouseName()).isEqualTo("仓库A");
        assertThat(entity.getWarehouseType()).isEqualTo("实体仓");
        assertThat(entity.getContactName()).isEqualTo("联系人");
        assertThat(entity.getContactPhone()).isEqualTo("138");
        assertThat(entity.getAddress()).isEqualTo("地址");
        assertThat(entity.getRemark()).isEqualTo("备注");
    }

    @Test
    void create_invalidStatus_rejected() {
        assertThatThrownBy(() -> service().create(request("仓库A", "已审核")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("仓库状态不合法");
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void detail_notFound_throwsWithModuleMessage() {
        when(warehouseRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().detail(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND))
                .hasMessage("仓库不存在");
    }

    @Test
    void update_nameChanged_syncsReferenceSnapshot() {
        Warehouse entity = new Warehouse();
        entity.setId(5L);
        entity.setWarehouseName("旧仓库名");
        entity.setWarehouseCode("W001");
        when(warehouseRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(codeIssuanceService.resolve("warehouse", "W001", "111")).thenReturn("W001");
        when(warehouseRepository.save(entity)).thenReturn(entity);

        service().update(5L, request("新仓库名", "正常"));

        verify(referenceSnapshotSyncService).syncWarehouseName(5L, "新仓库名");
    }

    @Test
    void update_nameUnchanged_skipsReferenceSnapshotSync() {
        Warehouse entity = new Warehouse();
        entity.setId(5L);
        entity.setWarehouseName("仓库名");
        entity.setWarehouseCode("W001");
        when(warehouseRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(codeIssuanceService.resolve("warehouse", "W001", "111")).thenReturn("W001");
        when(warehouseRepository.save(entity)).thenReturn(entity);

        service().update(5L, request("仓库名", "正常"));

        verify(referenceSnapshotSyncService, never()).syncWarehouseName(any(), anyString());
    }

    @Test
    void updateStatus_blank_rejectedWithValidationMessage() {
        when(warehouseRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(new Warehouse()));

        assertThatThrownBy(() -> service().updateStatus(5L, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("状态不能为空");
    }

    @Test
    void updateStatus_anyStatus_rejectedAsUnsupported() {
        when(warehouseRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(new Warehouse()));

        assertThatThrownBy(() -> service().updateStatus(5L, "正常"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.BUSINESS_ERROR))
                .hasMessage("当前模块不支持状态变更");
    }

    @Test
    void delete_referenced_blockedBeforeSoftDelete() {
        Warehouse entity = new Warehouse();
        entity.setId(5L);
        when(warehouseRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "仓库被引用"))
                .when(referenceGuard).assertNoReferences(eq("该仓库"), any());

        assertThatThrownBy(() -> service().delete(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仓库被引用");
        assertThat(entity.isDeletedFlag()).isFalse();
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void delete_softDeletesEntity() {
        Warehouse entity = new Warehouse();
        entity.setId(5L);
        when(warehouseRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(warehouseRepository.save(entity)).thenReturn(entity);

        service().delete(5L);

        assertThat(entity.isDeletedFlag()).isTrue();
        verify(warehouseRepository).save(entity);
    }

    @Test
    void delete_withoutReferenceGuard_stillSoftDeletes() {
        WarehouseService serviceWithoutGuard = new WarehouseService(warehouseRepository, snowflakeIdGenerator,
                warehouseMapper, null, codeIssuanceService, referenceSnapshotSyncService);
        Warehouse entity = new Warehouse();
        entity.setId(5L);
        when(warehouseRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(warehouseRepository.save(entity)).thenReturn(entity);

        serviceWithoutGuard.delete(5L);

        assertThat(entity.isDeletedFlag()).isTrue();
    }

    @Test
    void page_appliesDeletedVisibilityAndDefaultIdSort() {
        when(warehouseRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class)))
                .thenReturn(Page.empty());

        Page<WarehouseResponse> page =
                service().page(new PageQuery(0, 10, null, null), "仓", null, null);

        assertThat(page.getTotalElements()).isZero();
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(warehouseRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class),
                pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        Sort.Order idOrder = pageable.getSort().getOrderFor("id");
        assertThat(idOrder).isNotNull();
        assertThat(idOrder.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getPageSize()).isEqualTo(10);
    }

    @Test
    void cacheAnnotations_coverOptionsReadAndAllWritePaths() throws Exception {
        Method create = WarehouseService.class.getMethod("create", WarehouseRequest.class);
        Method update = WarehouseService.class.getMethod("update", Long.class, WarehouseRequest.class);
        Method updateStatus = WarehouseService.class.getMethod("updateStatus", Long.class, String.class);
        Method delete = WarehouseService.class.getMethod("delete", Long.class);
        Method listActiveOptions = WarehouseService.class.getMethod("listActiveOptions");

        for (Method method : List.of(create, update, updateStatus, delete)) {
            CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);
            assertThat(cacheEvict).as(method.getName()).isNotNull();
            assertThat(cacheEvict.value()).containsExactly(CacheConfig.CACHE_OPTIONS);
            assertThat(cacheEvict.key()).isEqualTo("'leo:warehouse:all'");
        }
        Cacheable cacheable = listActiveOptions.getAnnotation(Cacheable.class);
        assertThat(cacheable).isNotNull();
        assertThat(cacheable.value()).containsExactly(CacheConfig.CACHE_OPTIONS);
        assertThat(cacheable.key()).isEqualTo("'leo:warehouse:all'");
        assertThat(cacheable.unless()).isEqualTo("#result == null || #result.isEmpty()");
    }
}
