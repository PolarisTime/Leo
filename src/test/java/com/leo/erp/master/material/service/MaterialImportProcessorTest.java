package com.leo.erp.master.material.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.repository.MaterialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MaterialImportProcessor 导入行判定测试（包级可见，须同包）：
 * 身份命中更新（UPSERT）、精确匹配跳过、编码命中更新、身份变更守卫、
 * 新建编码生成、文件内同身份合并、身份 trim 归一化。
 */
@ExtendWith(MockitoExtension.class)
class MaterialImportProcessorTest {

    @Mock
    private MaterialRepository materialRepository;

    private MaterialImportProcessor processor;

    @BeforeEach
    void setUp() {
        MaterialIdentityService identityService = new MaterialIdentityService(materialRepository);
        processor = new MaterialImportProcessor(
                materialRepository, new SnowflakeIdGenerator(1), identityService);
    }

    @Test
    void identityHitWithDifferentFieldsShouldUpdateExisting() {
        Material existing = dbMaterial(100L, "100", "中天", "HRB400E", "12", "9米", 1);
        when(materialRepository.findActiveIdentityCandidates(anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(List.of(existing));

        MaterialImportProcessor.ImportSession session = startSession("中天", "HRB400E", "12", "9米");
        MaterialImportProcessor.ImportRowResult result = processor.importRow(
                session, importData(null, "中天", "HRB400E", "12", "9米", 250), 2);

        assertThat(result.outcome()).isEqualTo(MaterialImportProcessor.ImportOutcome.UPDATED);
        assertThat(result.material().getId()).isEqualTo(100L);
        assertThat(result.material().getPiecesPerBundle()).isEqualTo(250);
        verify(materialRepository).save(any(Material.class));
    }

    @Test
    void identityHitShouldIgnoreSurroundingWhitespace() {
        Material existing = dbMaterial(100L, "100", "中天", "HRB400E", "12", "9米", 1);
        when(materialRepository.findActiveIdentityCandidates(anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(List.of(existing));

        MaterialImportProcessor.ImportSession session = startSession("中天", "HRB400E", "12", "9米");
        MaterialImportProcessor.ImportRowResult result = processor.importRow(
                session, importData(null, " 中天 ", "HRB400E", " 12 ", "9米", 250), 2);

        assertThat(result.outcome()).isEqualTo(MaterialImportProcessor.ImportOutcome.UPDATED);
        assertThat(result.material().getId()).isEqualTo(100L);
    }

    @Test
    void exactMatchShouldSkipWithoutSave() {
        Material existing = dbMaterial(100L, "100", "中天", "HRB400E", "12", "9米", 1);
        when(materialRepository.findActiveIdentityCandidates(anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(List.of(existing));

        MaterialImportProcessor.ImportSession session = startSession("中天", "HRB400E", "12", "9米");
        MaterialImportProcessor.ImportRowResult result = processor.importRow(
                session, importData(null, "中天", "HRB400E", "12", "9米", 1), 2);

        assertThat(result.outcome()).isEqualTo(MaterialImportProcessor.ImportOutcome.SKIPPED);
        verify(materialRepository, never()).save(any(Material.class));
    }

    @Test
    void materialCodeHitShouldUpdateExisting() {
        Material existing = dbMaterial(100L, "CODE-1", "中天", "HRB400E", "12", "9米", 1);
        when(materialRepository.findByMaterialCode("CODE-1")).thenReturn(Optional.of(existing));

        MaterialImportProcessor.ImportSession session = processor.start(List.of());
        MaterialImportProcessor.ImportRowResult result = processor.importRow(
                session, importData("CODE-1", "中天", "HRB400E", "12", "9米", 250), 2);

        assertThat(result.outcome()).isEqualTo(MaterialImportProcessor.ImportOutcome.UPDATED);
        assertThat(result.material().getId()).isEqualTo(100L);
        assertThat(result.material().getMaterialCode()).isEqualTo("CODE-1");
    }

    @Test
    void identityChangeToOccupiedIdentityShouldFailByRow() {
        Material source = dbMaterial(100L, "A", "万泰", "HRB500E", "20", "9米", 1);
        Material occupier = dbMaterial(200L, "B", "中天", "HRB400E", "12", "9米", 1);
        when(materialRepository.findByMaterialCode("A")).thenReturn(Optional.of(source));
        when(materialRepository.findActiveIdentityCandidates(anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(List.of(source, occupier));

        MaterialImportProcessor.ImportSession session = processor.start(List.of(
                new MaterialIdentityService.Identity("万泰", "HRB500E", "20", "9米"),
                new MaterialIdentityService.Identity("中天", "HRB400E", "12", "9米")));
        MaterialImportData data = importData("A", "中天", "HRB400E", "12", "9米", 250);

        assertThatThrownBy(() -> processor.importRow(session, data, 5))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第5行")
                .hasMessageContaining("B");
        verify(materialRepository, never()).save(any(Material.class));
    }

    @Test
    void newIdentityShouldCreateWithGeneratedCode() {
        when(materialRepository.findActiveIdentityCandidates(anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(List.of());

        MaterialImportProcessor.ImportSession session = startSession("新品牌", "HRB400", "8", "9米");
        MaterialImportProcessor.ImportRowResult result = processor.importRow(
                session, importData(null, "新品牌", "HRB400", "8", "9米", 100), 2);

        assertThat(result.outcome()).isEqualTo(MaterialImportProcessor.ImportOutcome.CREATED);
        assertThat(result.material().getId()).isNotNull().isPositive();
        assertThat(result.material().getMaterialCode()).isEqualTo(String.valueOf(result.material().getId()));
    }

    @Test
    void duplicateIdentityRowsInFileShouldMergeIntoOneMaterial() {
        when(materialRepository.findActiveIdentityCandidates(anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(List.of());

        MaterialImportProcessor.ImportSession session = startSession("新品牌", "HRB400", "8", "9米");
        MaterialImportProcessor.ImportRowResult first = processor.importRow(
                session, importData(null, "新品牌", "HRB400", "8", "9米", 100), 2);
        MaterialImportProcessor.ImportRowResult second = processor.importRow(
                session, importData(null, "新品牌", "HRB400", "8", "9米", 120), 3);

        assertThat(first.outcome()).isEqualTo(MaterialImportProcessor.ImportOutcome.CREATED);
        assertThat(second.outcome()).isEqualTo(MaterialImportProcessor.ImportOutcome.UPDATED);
        assertThat(second.material().getId()).isEqualTo(first.material().getId());
        assertThat(second.material().getPiecesPerBundle()).isEqualTo(120);
        verify(materialRepository, times(2)).save(any(Material.class));
    }

    private MaterialImportProcessor.ImportSession startSession(String brand, String material, String spec, String length) {
        return processor.start(List.of(new MaterialIdentityService.Identity(brand, material, spec, length)));
    }

    private Material dbMaterial(Long id, String code, String brand, String material, String spec, String length,
                                int piecesPerBundle) {
        Material material1 = new Material();
        material1.setId(id);
        material1.setMaterialCode(code);
        material1.setBrand(brand);
        material1.setMaterial(material);
        material1.setCategory("直条");
        material1.setSpec(spec);
        material1.setLength(length);
        material1.setUnit("吨");
        material1.setQuantityUnit("件");
        material1.setPieceWeightTon(new BigDecimal("1.998"));
        material1.setPiecesPerBundle(piecesPerBundle);
        material1.setUnitPrice(BigDecimal.ZERO);
        return material1;
    }

    private MaterialImportData importData(String code, String brand, String material, String spec, String length,
                                          Integer piecesPerBundle) {
        return new MaterialImportData(
                code, brand, material, "直条", spec, length, "吨", "件",
                new BigDecimal("1.998"), piecesPerBundle, BigDecimal.ZERO, null
        );
    }
}
