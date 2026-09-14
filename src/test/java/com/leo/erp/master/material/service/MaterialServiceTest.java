package com.leo.erp.master.material.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
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

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
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

    private MaterialService service() {
        return new MaterialService(materialRepository, snowflakeIdGenerator, materialMapper,
                materialReferenceGuard, codeIssuanceService, identityService, materialHistoryRecorder);
    }

    private MaterialRequest physicalRequest(String brand) {
        return new MaterialRequest("111", brand, "棉布", "面料", "1米", "100",
                "米", "支", BigDecimal.ONE, 2, BigDecimal.TEN, "备注", null);
    }

    @Test
    void create_assignsSnowflakeIdAndConsumesIssuedCode() {
        Material saved = new Material();
        when(snowflakeIdGenerator.nextId()).thenReturn(123L);
        when(codeIssuanceService.resolve("material", null, "111")).thenReturn("111");
        when(materialRepository.save(any(Material.class))).thenReturn(saved);
        when(materialMapper.toResponse(saved)).thenReturn(new MaterialResponse(123L, "111", null, null,
                null, null, null, null, null, null, null, null, null, null));

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
                null, null, null, null, null, null, null, null, null, null, null));

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
                null, null, null, null, null, null, null, null, null, null, null));

        service().create(new MaterialRequest("  111  ", "  纯棉  ", "  棉布  ", "  面料  ", "  1米  ",
                "  100  ", "  米  ", "  支  ", new BigDecimal("1.0"), 2, new BigDecimal("10.00"),
                "  备注  ", null));

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
                null, null, null, null, null, null, null, null, null, null, null));
        MaterialRequest request = new MaterialRequest("111", null, "装卸费", null, null, null,
                "次", null, null, null, null, null, MaterialRequest.TYPE_EXPENSE);

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
                "米", "支", null, 2, BigDecimal.TEN, null, null);
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
                "次", null, null, null, null, null, MaterialRequest.TYPE_EXPENSE);

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
    void updateStatus_anyStatus_rejectedAsUnsupported() {
        when(materialRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(new Material()));

        assertThatThrownBy(() -> service().updateStatus(5L, "正常"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.BUSINESS_ERROR))
                .hasMessage("当前模块不支持状态变更");
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
