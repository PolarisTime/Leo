package com.leo.erp.master.material.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.repository.MaterialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MaterialIdentityService 极端情况测试（纯逻辑，包级可见，须同包）：
 * Identity 规范化、冲突检测、activeIndex 短路与去重、导入校验、精确匹配归一化、异常链索引名识别。
 */
@ExtendWith(MockitoExtension.class)
class MaterialIdentityServiceTest {

    @Mock
    private MaterialRepository materialRepository;

    @InjectMocks
    private MaterialIdentityService service;

    // ---------- identity 构造 ----------

    @Test
    void identity_stringFields_shouldNormalizeNullAndTrim() {
        MaterialIdentityService.Identity identity = service.identity(" 钢 ", null, " 5mm ", "");

        assertThat(identity.brand()).isEqualTo("钢");
        assertThat(identity.material()).isEmpty();
        assertThat(identity.spec()).isEqualTo("5mm");
        assertThat(identity.length()).isEmpty();
    }

    @Test
    void identity_materialImportData_shouldMapFields() {
        MaterialImportData data = new MaterialImportData(
                "M001", " 钢 ", "Q235", "型钢", "5mm", "6m",
                "吨", "件", new BigDecimal("1.5"), 10, new BigDecimal("4000"), "备注",
                MaterialImportData.TYPE_PHYSICAL
        );

        MaterialIdentityService.Identity identity = service.identity(data);

        assertThat(identity.brand()).isEqualTo("钢");
        assertThat(identity.material()).isEqualTo("Q235");
        assertThat(identity.spec()).isEqualTo("5mm");
        assertThat(identity.length()).isEqualTo("6m");
    }

    @Test
    void identity_material_shouldMapFields() {
        Material material = material(1L);

        MaterialIdentityService.Identity identity = service.identity(material);

        assertThat(identity.brand()).isEqualTo("钢");
        assertThat(identity.material()).isEqualTo("Q235");
        assertThat(identity.spec()).isEqualTo("5mm");
        assertThat(identity.length()).isEqualTo("6m");
    }

    // ---------- ensureUnique ----------

    @Test
    void ensureUnique_shouldNotThrowWhenConflictsNull() {
        when(materialRepository.findActiveIdentityConflicts("钢", "Q235", "5mm", "", null))
                .thenReturn(null);

        assertThatCode(() -> service.ensureUnique(null, identity("钢", "Q235", "5mm", null)))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureUnique_shouldNotThrowWhenConflictsEmpty() {
        when(materialRepository.findActiveIdentityConflicts("钢", "Q235", "5mm", "", null))
                .thenReturn(List.of());

        assertThatCode(() -> service.ensureUnique(null, identity("钢", "Q235", "5mm", null)))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureUnique_shouldThrowDuplicateWhenConflictsFound() {
        Material conflict = material(2L);
        conflict.setMaterialCode("M002");
        when(materialRepository.findActiveIdentityConflicts("钢", "Q235", "5mm", "", null))
                .thenReturn(List.of(conflict));

        assertThatThrownBy(() -> service.ensureUnique(null, identity("钢", "Q235", "5mm", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品资料重复")
                .hasMessageContaining("M002");
    }

    @Test
    void ensureUnique_shouldDelegateWithExcludedId() {
        service.ensureUnique(99L, identity("钢", "Q235", "5mm", null));

        verify(materialRepository).findActiveIdentityConflicts("钢", "Q235", "5mm", "", 99L);
    }

    // ---------- activeIndex ----------

    @Test
    void activeIndex_shouldReturnEmptyWhenNull() {
        assertThat(service.activeIndex(null)).isEmpty();
        verifyNoInteractions(materialRepository);
    }

    @Test
    void activeIndex_shouldReturnEmptyWhenEmpty() {
        assertThat(service.activeIndex(List.of())).isEmpty();
        verifyNoInteractions(materialRepository);
    }

    @Test
    void activeIndex_shouldReturnEmptyWhenAllValuesBlank() {
        assertThat(service.activeIndex(List.of(identity("  ", "", " ", null)))).isEmpty();
        verifyNoInteractions(materialRepository);
    }

    @Test
    void activeIndex_shouldReturnEmptyWhenCandidatesNull() {
        when(materialRepository.findActiveIdentityCandidates(
                List.of("钢"), List.of("Q235"), List.of("5mm")
        )).thenReturn(null);

        assertThat(service.activeIndex(List.of(identity("钢", "Q235", "5mm", null)))).isEmpty();
    }

    @Test
    void activeIndex_shouldDedupeByIdentityPutIfAbsent() {
        Material first = material(1L);
        Material second = material(2L);
        when(materialRepository.findActiveIdentityCandidates(any(), any(), any()))
                .thenReturn(List.of(first, second));

        Map<MaterialIdentityService.Identity, Material> index =
                service.activeIndex(List.of(identity("钢", "Q235", "5mm", null)));

        assertThat(index).hasSize(1);
        assertThat(index.values()).containsExactly(first);
    }

    @Test
    void activeIndex_shouldSkipBlankValuesInRepositoryQuery() {
        when(materialRepository.findActiveIdentityCandidates(
                List.of("钢", "铁"), List.of("Q235"), List.of("5mm", "3mm")
        )).thenReturn(List.of());

        // 第 2 条 material 空白、第 3 条 brand 空白 → 收集时被过滤；specs 去重。
        service.activeIndex(Arrays.asList(
                identity("钢", "Q235", "5mm", null),
                identity("铁", "", "3mm", "6m"),
                identity(null, "Q235", "3mm", "6m")
        ));

        verify(materialRepository).findActiveIdentityCandidates(
                List.of("钢", "铁"), List.of("Q235"), List.of("5mm", "3mm")
        );
    }

    // ---------- validateImport ----------

    @Test
    void validateImport_shouldRejectDuplicateDifferentMaterial() {
        Material importing = material(3L);
        Material duplicate = material(1L);
        Map<MaterialIdentityService.Identity, Material> index = new HashMap<>();
        index.put(identity("钢", "Q235", "5mm", null), duplicate);

        assertThatThrownBy(() ->
                service.validateImport(importing, identity("钢", "Q235", "5mm", null), index, 5))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第5行商品资料重复");
    }

    @Test
    void validateImport_shouldAcceptSameMaterial() {
        Material importing = material(1L);
        Map<MaterialIdentityService.Identity, Material> index = new HashMap<>();
        index.put(identity("钢", "Q235", "5mm", null), importing);

        assertThatCode(() ->
                service.validateImport(importing, identity("钢", "Q235", "5mm", null), index, 5))
                .doesNotThrowAnyException();
    }

    @Test
    void validateImport_shouldAcceptWhenIdentityAbsent() {
        assertThatCode(() ->
                service.validateImport(material(3L), identity("钢", "Q235", "5mm", null), new HashMap<>(), 5))
                .doesNotThrowAnyException();
    }

    // ---------- registerImport ----------

    @Test
    void registerImport_shouldSwapPreviousIdentityKey() {
        Material material = material(1L);
        Map<MaterialIdentityService.Identity, Material> index = new HashMap<>();
        MaterialIdentityService.Identity previous = identity("钢", "Q235", "5mm", null);
        MaterialIdentityService.Identity current = identity("铁", "Q235", "5mm", null);
        index.put(previous, material);

        service.registerImport(index, previous, current, material);

        assertThat(index).containsOnlyKeys(current);
        assertThat(index.get(current)).isSameAs(material);
    }

    @Test
    void registerImport_shouldNotRemoveWhenMaterialDiffers() {
        Material previousMaterial = material(1L);
        Map<MaterialIdentityService.Identity, Material> index = new HashMap<>();
        MaterialIdentityService.Identity previous = identity("钢", "Q235", "5mm", null);
        MaterialIdentityService.Identity current = identity("铁", "Q235", "5mm", null);
        index.put(previous, previousMaterial);

        service.registerImport(index, previous, current, material(2L));

        assertThat(index).containsKeys(previous, current);
    }

    @Test
    void registerImport_shouldPutWhenSameIdentity() {
        Material material = material(1L);
        Map<MaterialIdentityService.Identity, Material> index = new HashMap<>();
        MaterialIdentityService.Identity previous = identity("钢", "Q235", "5mm", null);
        index.put(previous, material);

        service.registerImport(index, previous, previous, material);

        assertThat(index).containsOnlyKeys(previous);
        assertThat(index.get(previous)).isSameAs(material);
    }

    // ---------- isExactImportMatch ----------

    @Test
    void isExactImportMatch_shouldReturnFalseWhenNull() {
        assertThat(service.isExactImportMatch(null, matchingData(), true)).isFalse();
    }

    @Test
    void isExactImportMatch_shouldReturnFalseWhenDeleted() {
        Material deleted = matchingExisting();
        deleted.setDeletedFlag(true);

        assertThat(service.isExactImportMatch(deleted, matchingData(), true)).isFalse();
    }

    @Test
    void isExactImportMatch_shouldReturnFalseWhenCodeMismatch() {
        Material existing = matchingExisting();
        existing.setMaterialCode("M999");

        assertThat(service.isExactImportMatch(existing, matchingData(), true)).isFalse();
        // compareCode=false 时忽略物料编码比较。
        assertThat(service.isExactImportMatch(existing, matchingData(), false)).isTrue();
    }

    @Test
    void isExactImportMatch_shouldReturnTrueWhenAllMatch() {
        assertThat(service.isExactImportMatch(matchingExisting(), matchingData(), true)).isTrue();
        assertThat(service.isExactImportMatch(matchingExisting(), matchingData(), false)).isTrue();
    }

    @Test
    void isExactImportMatch_shouldNormalizeQuantityUnitToDefaultPiece() {
        Material existing = matchingExisting();
        existing.setQuantityUnit("件");
        // data.quantityUnit 为 null/空白 → normalizeQuantityUnit 默认"件"。
        MaterialImportData data = new MaterialImportData(
                "M001", "钢", "Q235", "型钢", "5mm", "6m", "吨", null,
                new BigDecimal("1.5"), 10, new BigDecimal("4000"), "备注",
                MaterialImportData.TYPE_PHYSICAL
        );

        assertThat(service.isExactImportMatch(existing, data, true)).isTrue();
    }

    @Test
    void isExactImportMatch_shouldTreatNullNumericAsZero() {
        Material existing = matchingExisting();
        existing.setPieceWeightTon(new BigDecimal("0.000"));
        existing.setPiecesPerBundle(0);
        existing.setUnitPrice(new BigDecimal("0"));
        // data 的数值字段为 null → 与 0 视为相等（BigDecimal compareTo / int 相等）。
        MaterialImportData data = new MaterialImportData(
                "M001", "钢", "Q235", "型钢", "5mm", "6m", "吨", "件",
                null, null, null, "备注",
                MaterialImportData.TYPE_PHYSICAL
        );

        assertThat(service.isExactImportMatch(existing, data, true)).isTrue();
    }

    // ---------- mapViolation ----------

    @Test
    void mapViolation_shouldMapIdentityViolation() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "insert or update on table md_material violates unique constraint \"uk_md_material_identity_active\""
        );

        BusinessException mapped = service.mapViolation(exception, ErrorCode.VALIDATION_ERROR, 5);

        assertThat(mapped.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(mapped.getMessage()).contains("第5行商品资料重复");
    }

    @Test
    void mapViolation_shouldThrowOriginalWhenNotIdentityViolation() {
        DataIntegrityViolationException original =
                new DataIntegrityViolationException("violates foreign key constraint foo_fk");

        assertThatThrownBy(() -> service.mapViolation(original, ErrorCode.VALIDATION_ERROR, null))
                .isSameAs(original);
    }

    @Test
    void mapViolation_shouldDetectViolationInNestedCause() {
        DataIntegrityViolationException root =
                new DataIntegrityViolationException("uk_md_material_identity_active");
        DataIntegrityViolationException outer = new DataIntegrityViolationException("outer wrap", root);

        BusinessException mapped = service.mapViolation(outer, ErrorCode.BUSINESS_ERROR, null);

        assertThat(mapped.getMessage()).contains("商品资料重复");
    }

    // ---------- fixtures ----------

    private MaterialIdentityService.Identity identity(String brand, String material, String spec, String length) {
        return service.identity(brand, material, spec, length);
    }

    private Material material(Long id) {
        Material material = new Material();
        material.setId(id);
        material.setMaterialCode("M001");
        material.setBrand("钢");
        material.setMaterial("Q235");
        material.setCategory("型钢");
        material.setSpec("5mm");
        material.setLength("6m");
        material.setUnit("吨");
        material.setQuantityUnit("件");
        material.setPieceWeightTon(new BigDecimal("1.5"));
        material.setPiecesPerBundle(10);
        material.setUnitPrice(new BigDecimal("4000"));
        material.setRemark("备注");
        return material;
    }

    private Material matchingExisting() {
        return material(1L);
    }

    private MaterialImportData matchingData() {
        return new MaterialImportData(
                "M001", "钢", "Q235", "型钢", "5mm", "6m",
                "吨", "件", new BigDecimal("1.5"), 10, new BigDecimal("4000"), "备注",
                MaterialImportData.TYPE_PHYSICAL
        );
    }
}
