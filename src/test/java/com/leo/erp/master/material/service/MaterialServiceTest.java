package com.leo.erp.master.material.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.material.domain.MaterialSnapshot;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.mapper.MaterialMapper;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.master.material.web.dto.MaterialRequest;
import com.leo.erp.master.material.web.dto.MaterialResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Mock
    private MaterialMapper materialMapper;
    @Mock
    private MaterialReferenceGuard materialReferenceGuard;
    @Mock
    private MasterDataCodeIssuanceService codeIssuanceService;
    @Mock
    private MaterialIdentityService identityService;
    @Mock
    private MaterialHistoryRecorder materialHistoryRecorder;

    @Mock
    private MaterialDictionaryCache materialDictionaryCache;

    @Mock
    private MaterialCategoryGuard materialCategoryGuard;

    private MaterialService service() {
        return service(materialMapper);
    }

    private MaterialService service(MaterialMapper mapper) {
        // 物理商品的类别需存在于分类主数据: 本测试只关注商品自身逻辑,
        // 故让 guard 原样放行传入的类别名。
        // 与真实 MaterialCategoryGuard 行为一致: 按名称匹配后返回规范化(trim)后的分类名。
        lenient().when(materialCategoryGuard.requireActive(anyString()))
                .thenAnswer(invocation -> ((String) invocation.getArgument(0)).trim());
        return new MaterialService(materialRepository, snowflakeIdGenerator, mapper,
                materialReferenceGuard, codeIssuanceService, identityService, materialHistoryRecorder,
                materialDictionaryCache, materialCategoryGuard);
    }

    /** 真实字段映射的 mapper 替身：断言 status 字段真的被映射进响应，而不只是「调用过 mapper」。 */
    private static MaterialMapper statusMapper() {
        return entity -> new MaterialResponse(
                entity.getId(),
                entity.getMaterialCode(),
                entity.getBrand(),
                entity.getMaterial(),
                entity.getCategory(),
                entity.getSpec(),
                entity.getLength(),
                entity.getUnit(),
                entity.getQuantityUnit(),
                entity.getPieceWeightTon(),
                entity.getPiecesPerBundle(),
                entity.getUnitPrice(),
                entity.getRemark(),
                entity.getMaterialType(),
                entity.getStatus());
    }

    private void stubCreatedMaterial(Material[] holder) {
        when(snowflakeIdGenerator.nextId()).thenReturn(123L);
        when(codeIssuanceService.resolve(eq("material"), isNull(), anyString())).thenReturn("111");
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> {
            Material entity = invocation.getArgument(0);
            holder[0] = entity;
            return entity;
        });
    }

    private static Material existingPhysical(Long id, String status) {
        Material entity = new Material();
        entity.setId(id);
        entity.setMaterialCode("M001");
        entity.setBrand("旧品牌");
        entity.setStatus(status);
        return entity;
    }

    private static MaterialRequest requestWithStatus(String status) {
        return new MaterialRequest("111", "新品牌", "棉布", "面料", "1米", "100",
                "米", "支", BigDecimal.ONE, 2, BigDecimal.TEN, "备注", null, status);
    }

    private MaterialRequest physicalRequest(String brand) {
        return new MaterialRequest("111", brand, "棉布", "面料", "1米", "100",
                "米", "支", BigDecimal.ONE, 2, BigDecimal.TEN, "备注", null, null);
    }

    @Test
    void create_assignsSnowflakeIdAndConsumesIssuedCode() {
        Material saved = new Material();
        when(snowflakeIdGenerator.nextId()).thenReturn(123L);
        when(codeIssuanceService.resolve("material", null, "111")).thenReturn("111");
        when(materialRepository.save(any(Material.class))).thenReturn(saved);
        when(materialMapper.toResponse(saved)).thenReturn(new MaterialResponse(123L, "111", null, null,
                null, null, null, null, null, null, null, null, null, null, null));

        MaterialResponse response = service().create(physicalRequest("   纯棉 "));

        assertThat(response.id()).isEqualTo(123L);
        verify(codeIssuanceService).validate("material", "111");
        verify(identityService).ensureUnique(isNull(), any());
        verify(codeIssuanceService).consume("material", saved.getMaterialCode());
    }

    @Test
    void create_physical_fillsRequiredPhysicalFields() {
        Material[] savedHolder = new Material[1];
        when(snowflakeIdGenerator.nextId()).thenReturn(123L);
        when(codeIssuanceService.resolve(eq("material"), isNull(), eq("111"))).thenReturn("111");
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> {
            Material entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });
        when(materialMapper.toResponse(any(Material.class))).thenReturn(new MaterialResponse(123L, "111", null,
                null, null, null, null, null, null, null, null, null, null, null, null));

        service().create(physicalRequest("纯棉"));

        Material entity = savedHolder[0];
        assertThat(entity.getId()).isEqualTo(123L);
        assertThat(entity.getMaterialCode()).isEqualTo("111");
        assertThat(entity.getBrand()).isEqualTo("纯棉");
        assertThat(entity.getMaterialType()).isEqualTo(MaterialRequest.TYPE_PHYSICAL);
        assertThat(entity.getPieceWeightTon()).isEqualTo(BigDecimal.ONE);
        assertThat(entity.getPiecesPerBundle()).isEqualTo(2);
    }

    @Test
    void create_trimsUserVisibleStringsForPhysicalMaterial() {
        Material[] savedHolder = new Material[1];
        when(snowflakeIdGenerator.nextId()).thenReturn(123L);
        when(codeIssuanceService.resolve(eq("material"), any(), anyString())).thenReturn("111");
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> {
            Material entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });
        when(materialMapper.toResponse(any(Material.class))).thenReturn(new MaterialResponse(123L, "111", null,
                null, null, null, null, null, null, null, null, null, null, null, null));

        service().create(new MaterialRequest("  111  ", "  纯棉  ", "  棉布  ", "  面料  ", "  1米  ",
                "  100  ", "  米  ", "  支  ", new BigDecimal("1.0"), 2, new BigDecimal("10.00"),
                "  备注  ", null, null));

        Material entity = savedHolder[0];
        assertThat(entity.getBrand()).isEqualTo("纯棉");
        assertThat(entity.getMaterial()).isEqualTo("棉布");
        assertThat(entity.getCategory()).isEqualTo("面料");
        assertThat(entity.getSpec()).isEqualTo("1米");
        assertThat(entity.getLength()).isEqualTo("100");
        assertThat(entity.getUnit()).isEqualTo("米");
        assertThat(entity.getQuantityUnit()).isEqualTo("支");
        assertThat(entity.getRemark()).isEqualTo("备注");
    }

    @Test
    void create_expense_withoutBrandSucceeds() {
        Material[] savedHolder = new Material[1];
        when(snowflakeIdGenerator.nextId()).thenReturn(222L);
        when(codeIssuanceService.resolve(eq("material"), isNull(), eq("111"))).thenReturn("111");
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> {
            Material entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });
        when(materialMapper.toResponse(any(Material.class))).thenReturn(new MaterialResponse(222L, "111", null,
                null, null, null, null, null, null, null, null, null, null, null, null));
        MaterialRequest request = new MaterialRequest("111", null, "装卸费", null, null, null,
                "次", null, null, null, null, null, MaterialRequest.TYPE_EXPENSE, null);

        service().create(request);

        Material entity = savedHolder[0];
        assertThat(entity.getMaterialType()).isEqualTo(MaterialRequest.TYPE_EXPENSE);
        assertThat(entity.getBrand()).isEmpty();
        assertThat(entity.getCategory()).isEqualTo("附加费用");
        assertThat(entity.getPieceWeightTon()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void create_blankBrand_rejectedWithoutSaving() {
        assertThatThrownBy(() -> service().create(physicalRequest("  ")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("品牌不能为空");
        verify(materialRepository, never()).save(any());
        verify(codeIssuanceService, never()).consume(anyString(), anyString());
    }

    @Test
    void create_missingPieceWeight_rejected() {
        MaterialRequest request = new MaterialRequest("111", "纯棉", "棉布", "面料", "1米", "100",
                "米", "支", null, 2, BigDecimal.TEN, null, null, null);
        assertThatThrownBy(() -> service().create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("件重不能为空");
        verify(materialRepository, never()).save(any());
    }

    @Test
    void detail_notFound_throwsWithModuleMessage() {
        when(materialRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().detail(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND))
                .hasMessage("商品不存在");
    }

    @Test
    void update_brandChanged_recordsHistoryAndFreezesSnapshots() {
        Material entity = new Material();
        entity.setId(5L);
        entity.setBrand("旧品牌");
        entity.setMaterialCode("M001");
        when(materialRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(codeIssuanceService.resolve("material", "M001", "111")).thenReturn("M001");
        when(materialRepository.save(entity)).thenReturn(entity);

        service().update(5L, physicalRequest("新品牌"));

        verify(materialHistoryRecorder).record(eq(5L), eq(MaterialHistoryRecorder.SOURCE_MANUAL),
                eq(MaterialHistoryRecorder.TYPE_UPDATED), any(MaterialSnapshot.class), any(MaterialSnapshot.class),
                isNull(), isNull());
        verify(codeIssuanceService, never()).consume(anyString(), anyString());
    }

    @Test
    void update_expenseType_recordsHistory() {
        Material entity = new Material();
        entity.setId(5L);
        entity.setBrand("旧品牌");
        entity.setMaterialCode("M001");
        when(materialRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(codeIssuanceService.resolve("material", "M001", "111")).thenReturn("M001");
        when(materialRepository.save(entity)).thenReturn(entity);
        MaterialRequest request = new MaterialRequest("111", null, "装卸费", null, null, null,
                "次", null, null, null, null, null, MaterialRequest.TYPE_EXPENSE, null);

        service().update(5L, request);

        verify(materialHistoryRecorder).record(eq(5L), eq(MaterialHistoryRecorder.SOURCE_MANUAL),
                eq(MaterialHistoryRecorder.TYPE_UPDATED), any(MaterialSnapshot.class), any(MaterialSnapshot.class),
                isNull(), isNull());
        assertThat(entity.getBrand()).isEmpty();
        assertThat(entity.getMaterialType()).isEqualTo(MaterialRequest.TYPE_EXPENSE);
    }

    @Test
    void updateStatus_blank_rejectedWithValidationMessage() {
        when(materialRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(new Material()));

        assertThatThrownBy(() -> service().updateStatus(5L, "  "))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("状态不能为空");
    }

    @Test
    void updateStatus_normalToDisabledAndBack_roundTrip() {
        Material entity = existingPhysical(5L, StatusConstants.NORMAL);
        when(materialRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(materialRepository.save(entity)).thenReturn(entity);
        MaterialService service = service(statusMapper());

        // 正常 → 禁用
        assertThat(service.updateStatus(5L, StatusConstants.DISABLED).status())
                .isEqualTo(StatusConstants.DISABLED);
        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DISABLED);

        // 禁用 → 正常：商品允许回退，误禁用必须能立即恢复
        assertThat(service.updateStatus(5L, StatusConstants.NORMAL).status())
                .isEqualTo(StatusConstants.NORMAL);
        assertThat(entity.getStatus()).isEqualTo(StatusConstants.NORMAL);
        // 状态影响商品选项缓存，每次生效的启停都要失效字典缓存
        verify(materialDictionaryCache, times(2)).evictAll();
    }

    @Test
    void updateStatus_sameStatus_skipsWrite() {
        Material entity = existingPhysical(5L, StatusConstants.NORMAL);
        when(materialRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));

        MaterialResponse response = service(statusMapper()).updateStatus(5L, StatusConstants.NORMAL);

        assertThat(response.status()).isEqualTo(StatusConstants.NORMAL);
        verify(materialRepository, never()).save(any());
        verify(materialDictionaryCache, never()).evictAll();
    }

    @Test
    void updateStatus_invalidValue_rejectedWithValidationError() {
        Material entity = existingPhysical(5L, StatusConstants.NORMAL);
        when(materialRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service().updateStatus(5L, "作废"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("商品状态不合法");
        verify(materialRepository, never()).save(any());
    }

    @Test
    void updateStatus_legacyNullStatus_treatedAsNormalNoop() {
        // 迁移前的历史行 status 为空时按「正常」处理：再次启用是幂等空操作，不应写库
        Material entity = existingPhysical(5L, null);
        when(materialRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));

        assertThat(service(statusMapper()).updateStatus(5L, StatusConstants.NORMAL)).isNotNull();
        verify(materialRepository, never()).save(any());
    }

    @Test
    void updateStatus_disabledMaterialStillEditableAndReEnabled() {
        // 禁用只影响「选商品」查询，不阻断 detail / 编辑 / 启用
        Material entity = existingPhysical(5L, StatusConstants.DISABLED);
        when(materialRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(codeIssuanceService.resolve("material", "M001", "111")).thenReturn("M001");
        when(materialRepository.save(entity)).thenReturn(entity);
        MaterialService service = service(statusMapper());

        assertThat(service.detail(5L).status()).isEqualTo(StatusConstants.DISABLED);

        service.update(5L, physicalRequest("新品牌"));
        assertThat(entity.getBrand()).isEqualTo("新品牌");
        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DISABLED);

        assertThat(service.updateStatus(5L, StatusConstants.NORMAL).status())
                .isEqualTo(StatusConstants.NORMAL);
    }

    @Test
    void create_defaultsStatusToNormal() {
        Material[] holder = new Material[1];
        stubCreatedMaterial(holder);

        MaterialResponse response = service(statusMapper()).create(physicalRequest("纯棉"));

        assertThat(holder[0].getStatus()).isEqualTo(StatusConstants.NORMAL);
        assertThat(response.status()).isEqualTo(StatusConstants.NORMAL);
    }

    @Test
    void create_acceptsExplicitNormalStatus() {
        Material[] holder = new Material[1];
        stubCreatedMaterial(holder);

        service().create(new MaterialRequest("111", "纯棉", "棉布", "面料", "1米", "100",
                "米", "支", BigDecimal.ONE, 2, BigDecimal.TEN, null, null, StatusConstants.NORMAL));

        assertThat(holder[0].getStatus()).isEqualTo(StatusConstants.NORMAL);
    }

    @Test
    void create_rejectsDisabledStatus() {
        assertThatThrownBy(() -> service().create(new MaterialRequest("111", "纯棉", "棉布", "面料", "1米", "100",
                "米", "支", BigDecimal.ONE, 2, BigDecimal.TEN, null, null, StatusConstants.DISABLED)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("只能是「正常」");
        verify(materialRepository, never()).save(any());
        verify(codeIssuanceService, never()).consume(anyString(), anyString());
    }

    @Test
    void update_switchesStatusToDisabled_andDetailReturnsDisabled() {
        Material entity = existingPhysical(5L, StatusConstants.NORMAL);
        when(materialRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(codeIssuanceService.resolve("material", "M001", "111")).thenReturn("M001");
        when(materialRepository.save(entity)).thenReturn(entity);
        MaterialService service = service(statusMapper());

        MaterialResponse updated = service.update(5L, requestWithStatus(StatusConstants.DISABLED));

        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DISABLED);
        assertThat(updated.status()).isEqualTo(StatusConstants.DISABLED);
        assertThat(service.detail(5L).status()).isEqualTo(StatusConstants.DISABLED);
    }

    @Test
    void update_statusNull_keepsExistingStatus() {
        Material entity = existingPhysical(5L, StatusConstants.DISABLED);
        when(materialRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(codeIssuanceService.resolve("material", "M001", "111")).thenReturn("M001");
        when(materialRepository.save(entity)).thenReturn(entity);

        service().update(5L, physicalRequest("新品牌"));

        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DISABLED);
    }

    @Test
    void update_invalidStatus_rejectedWithoutSaving() {
        Material entity = existingPhysical(5L, StatusConstants.NORMAL);
        when(materialRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(codeIssuanceService.resolve("material", "M001", "111")).thenReturn("M001");

        assertThatThrownBy(() -> service().update(5L, requestWithStatus("作废")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("商品状态不合法");
        verify(materialRepository, never()).save(any());
    }

    @Test
    void page_invalidStatus_rejectedBeforeQuery() {
        assertThatThrownBy(() -> service().page(new PageQuery(0, 20, null, null), null, null, null, null, "作废"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("商品状态不合法");
        verify(materialRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void page_validStatus_queriesWithFilter() {
        when(materialRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        service().page(new PageQuery(0, 20, null, null), null, null, null, null, " 禁用 ");

        verify(materialRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void delete_referenced_blockedBeforeSoftDelete() {
        Material entity = new Material();
        entity.setId(5L);
        when(materialRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "商品被引用"))
                .when(materialReferenceGuard).assertNoReferences(entity);

        assertThatThrownBy(() -> service().delete(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("商品被引用");
        assertThat(entity.isDeletedFlag()).isFalse();
        verify(materialRepository, never()).save(any());
    }

    @Test
    void delete_softDeletesEntity() {
        Material entity = new Material();
        entity.setId(5L);
        when(materialRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(materialRepository.save(entity)).thenReturn(entity);

        service().delete(5L);

        assertThat(entity.isDeletedFlag()).isTrue();
        verify(materialRepository).save(entity);
        verify(materialHistoryRecorder).record(eq(5L), eq(MaterialHistoryRecorder.SOURCE_MANUAL),
                eq(MaterialHistoryRecorder.TYPE_DELETED), any(MaterialSnapshot.class), isNull(), isNull(), isNull());
    }

    @Test
    void delete_notFound_throwsWithoutSaving() {
        when(materialRepository.findByIdAndDeletedFlagFalse(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().delete(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("商品不存在");
        verify(materialRepository, never()).save(any());
    }
}
