package com.leo.erp.master.material.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.master.material.web.dto.MaterialImportDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MaterialSpreadsheetImportService 行级失败收集与行级轨迹测试（包级可见，须同包）：
 * 格式错误行失败不阻塞后续行、身份守卫失败行级上报、全部失败不触发缓存失效、
 * 空行列表、行级 trace 记录 outcome 与失败原因、缓存只失效一次。
 */
@ExtendWith(MockitoExtension.class)
class MaterialSpreadsheetImportServiceTest {

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private TradeItemMaterialSupport tradeItemMaterialSupport;

    private MaterialSpreadsheetImportService service;

    @BeforeEach
    void setUp() {
        MaterialIdentityService identityService = new MaterialIdentityService(materialRepository);
        MaterialImportProcessor processor = new MaterialImportProcessor(
                materialRepository, new SnowflakeIdGenerator(1), identityService);
        service = new MaterialSpreadsheetImportService(processor, tradeItemMaterialSupport);
    }

    @Test
    void invalidNumberRowShouldFailWithoutBlockingFollowingRows() {
        when(materialRepository.findActiveIdentityCandidates(anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(List.of());

        MaterialSpreadsheetImportService.SpreadsheetImportResult result = service.importRows(List.of(
                dto(null, "中天", "HRB400E", "直条", "12", "9米", "吨", "件", "abc", "250", "0", null),
                dto(null, "中天", "HRB400E", "直条", "14", "9米", "吨", "件", "2.0", "188", "0", null)
        ));

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.failCount()).isEqualTo(1);
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);

        assertThat(result.rows()).hasSize(2);
        MaterialSpreadsheetImportService.ImportRowTrace failed = result.rows().getFirst();
        assertThat(failed.rowNumber()).isEqualTo(2);
        assertThat(failed.outcome()).isNull();
        assertThat(failed.failReason()).contains("件重");
        MaterialSpreadsheetImportService.ImportRowTrace created = result.rows().get(1);
        assertThat(created.rowNumber()).isEqualTo(3);
        assertThat(created.outcome()).isEqualTo(MaterialImportProcessor.ImportOutcome.CREATED);
        assertThat(created.material()).isNotNull();
        assertThat(created.failReason()).isNull();
    }

    @Test
    void identityGuardFailureShouldBeReportedByRowAndContinue() {
        Material source = dbMaterial(100L, "A", "万泰", "HRB500E", "20", "9米", 1);
        Material occupier = dbMaterial(200L, "B", "中天", "HRB400E", "12", "9米", 1);
        when(materialRepository.findByMaterialCode("A")).thenReturn(Optional.of(source));
        when(materialRepository.findActiveIdentityCandidates(anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(List.of(source, occupier));

        MaterialSpreadsheetImportService.SpreadsheetImportResult result = service.importRows(List.of(
                dto("A", "中天", "HRB400E", "直条", "12", "9米", "吨", "件", "2.0", "250", "0", null),
                dto(null, "永钢", "HRB400", "盘螺", "6", "-", "吨", "件", "2.3", "120", "0", null)
        ));

        assertThat(result.failCount()).isEqualTo(1);
        assertThat(result.createdCount()).isEqualTo(1);
        MaterialSpreadsheetImportService.ImportRowTrace failed = result.rows().getFirst();
        assertThat(failed.rowNumber()).isEqualTo(2);
        assertThat(failed.failReason()).contains("重复").contains("B");
        assertThat(result.rows().get(1).outcome()).isEqualTo(MaterialImportProcessor.ImportOutcome.CREATED);
    }

    @Test
    void allRowsInvalidShouldFailAllWithoutCacheEviction() {
        when(materialRepository.findActiveIdentityCandidates(anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(List.of());

        MaterialSpreadsheetImportService.SpreadsheetImportResult result = service.importRows(List.of(
                dto(null, "中天", "HRB400E", "直条", "12", "9米", "吨", "件", "abc", "250", "0", null),
                dto(null, "中天", "HRB400E", "直条", "14", "9米", "吨", "件", "xyz", "188", "0", null)
        ));

        assertThat(result.successCount()).isZero();
        assertThat(result.failCount()).isEqualTo(2);
        assertThat(result.rows()).allSatisfy(trace -> assertThat(trace.outcome()).isNull());
        verify(tradeItemMaterialSupport, never()).evictCache();
    }

    @Test
    void successfulRowsShouldEvictCacheOnlyOnce() {
        when(materialRepository.findActiveIdentityCandidates(anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(List.of());

        service.importRows(List.of(
                dto(null, "中天", "HRB400E", "直条", "12", "9米", "吨", "件", "2.0", "250", "0", null),
                dto(null, "中天", "HRB400E", "直条", "14", "9米", "吨", "件", "2.0", "188", "0", null)
        ));

        verify(tradeItemMaterialSupport, times(1)).evictCache();
    }

    @Test
    void emptyRowsShouldReturnZeroCounters() {
        MaterialSpreadsheetImportService.SpreadsheetImportResult result = service.importRows(List.of());

        assertThat(result.totalRows()).isZero();
        assertThat(result.successCount()).isZero();
        assertThat(result.createdCount()).isZero();
        assertThat(result.updatedCount()).isZero();
        assertThat(result.skippedCount()).isZero();
        assertThat(result.failCount()).isZero();
        assertThat(result.rows()).isEmpty();
    }

    private MaterialImportDTO dto(String materialCode, String brand, String material, String category, String spec,
                                  String length, String unit, String quantityUnit, String pieceWeightTon,
                                  String piecesPerBundle, String unitPrice, String remark) {
        return new MaterialImportDTO(
                materialCode, brand, material, category, spec, length, unit, quantityUnit,
                pieceWeightTon, piecesPerBundle, unitPrice, remark
        );
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
}
