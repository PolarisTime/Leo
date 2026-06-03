package com.leo.erp.master.material.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.excel.dto.ImportResult;
import com.leo.erp.common.excel.service.ExcelExportService;
import com.leo.erp.common.excel.service.ExcelImportService;
import com.leo.erp.common.excel.service.ExcelTemplateService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.mapper.MaterialMapper;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.master.material.web.dto.MaterialImportDTO;
import com.leo.erp.master.material.web.dto.MaterialImportResultResponse;
import com.leo.erp.master.material.web.dto.MaterialRequest;
import com.leo.erp.master.material.web.dto.MaterialResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaterialServiceTest {

    @Test
    void shouldImportCsvWithQuotedValuesAndBatchHeader() throws Exception {
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        TradeItemMaterialSupport tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        when(materialRepository.findByMaterialCode("MAT-001")).thenReturn(Optional.empty());
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeItemMaterialSupport.normalizeBatchNoEnabled(Boolean.TRUE)).thenReturn(true);

        MaterialService service = new MaterialService(
                materialRepository, new SnowflakeIdGenerator(1L), materialMapper,
                tradeItemMaterialSupport, null, null, null, null
        );

        String csv = "\uFEFF商品编码,品牌,材质,类别,规格,长度,单位,数量单位,件重(吨),每件支数,单价,批号管理,备注\r\n"
                + "MAT-001,宝钢,Q235B,板材,\"10,20\",6m,吨,支,1.234,12,500.50,是,\"含,逗号\"\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "materials.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        MaterialImportResultResponse result = service.importCsv(file);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isZero();
        assertThat(result.failedCount()).isZero();

        ArgumentCaptor<Material> materialCaptor = ArgumentCaptor.forClass(Material.class);
        verify(materialRepository).save(materialCaptor.capture());
        Material saved = materialCaptor.getValue();
        assertThat(saved.getMaterialCode()).isEqualTo("MAT-001");
        assertThat(saved.getSpec()).isEqualTo("10,20");
        assertThat(saved.getRemark()).isEqualTo("含,逗号");
        assertThat(saved.getBatchNoEnabled()).isTrue();
    }

    @Test
    void shouldThrowException_whenCreateWithDuplicateCode() {
        var repository = (MaterialRepository) Proxy.newProxyInstance(
                MaterialRepository.class.getClassLoader(),
                new Class[]{MaterialRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByMaterialCodeAndDeletedFlagFalse" -> true;
                    case "toString" -> "MaterialRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        var service = new MaterialService(repository, new SnowflakeIdGenerator(1), null, tradeItemMaterialSupport, null, null, null, null);

        var request = new MaterialRequest("MAT-001", "宝钢", "Q235B", "板材", "10mm", null, "吨", null, BigDecimal.ONE, 1, BigDecimal.TEN, false, null);
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品编码已存在");
    }

    @Test
    void shouldThrowException_whenUpdateWithChangedDuplicateCode() {
        var repository = (MaterialRepository) Proxy.newProxyInstance(
                MaterialRepository.class.getClassLoader(),
                new Class[]{MaterialRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createMaterial(1L, "MAT-001"));
                    case "existsByMaterialCodeAndDeletedFlagFalse" -> true;
                    case "toString" -> "MaterialRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        var service = new MaterialService(repository, new SnowflakeIdGenerator(1), null, tradeItemMaterialSupport, null, null, null, null);

        var request = new MaterialRequest("MAT-002", "宝钢", "Q235B", "板材", "10mm", null, "吨", null, BigDecimal.ONE, 1, BigDecimal.TEN, false, null);
        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品编码已存在");
    }

    @Test
    void shouldReturnDetail_whenEntityExists() {
        var repository = (MaterialRepository) Proxy.newProxyInstance(
                MaterialRepository.class.getClassLoader(),
                new Class[]{MaterialRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createMaterial(1L, "MAT-001"));
                    case "findById" -> Optional.of(createMaterial(1L, "MAT-001"));
                    case "toString" -> "MaterialRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (MaterialMapper) Proxy.newProxyInstance(
                MaterialMapper.class.getClassLoader(),
                new Class[]{MaterialMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new MaterialResponse(1L, "MAT-001", "宝钢", "Q235B", "板材", "10mm", null, "吨", null, BigDecimal.ONE, 1, BigDecimal.TEN, false, null);
                    case "toString" -> "MaterialMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        var service = new MaterialService(repository, new SnowflakeIdGenerator(1), mapper, tradeItemMaterialSupport, null, null, null, null);

        var result = service.detail(1L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.materialCode()).isEqualTo("MAT-001");
    }

    @Test
    void shouldImportCsvAndUpdateExistingMaterial() throws Exception {
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        TradeItemMaterialSupport tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        Material existing = createMaterial(1L, "MAT-001");
        when(materialRepository.findByMaterialCode("MAT-001")).thenReturn(Optional.of(existing));
        when(materialRepository.existsById(1L)).thenReturn(true);
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeItemMaterialSupport.normalizeBatchNoEnabled(Boolean.FALSE)).thenReturn(false);

        MaterialService service = new MaterialService(
                materialRepository, new SnowflakeIdGenerator(1L), materialMapper,
                tradeItemMaterialSupport, null, null, null, null
        );

        String csv = "\uFEFF商品编码,品牌,材质,类别,规格,长度,单位,数量单位,件重(吨),每件支数,单价,批号管理,备注\r\n"
                + "MAT-001,新品牌,Q235B,板材,20mm,12m,吨,支,2.000,10,600.00,否,\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "materials.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        MaterialImportResultResponse result = service.importCsv(file);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.createdCount()).isZero();
    }

    @Test
    void shouldThrowException_whenImportEmptyFile() {
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        MaterialService service = new MaterialService(
                mock(MaterialRepository.class), new SnowflakeIdGenerator(1L), null,
                tradeItemMaterialSupport, null, null, null, null
        );

        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> service.importCsv(emptyFile))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传文件不能为空");
    }

    @Test
    void shouldImportCsvWithGbkEncoding() throws Exception {
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        TradeItemMaterialSupport tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        when(materialRepository.findByMaterialCode("MAT-GBK")).thenReturn(Optional.empty());
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeItemMaterialSupport.normalizeBatchNoEnabled(false)).thenReturn(false);

        MaterialService service = new MaterialService(
                materialRepository, new SnowflakeIdGenerator(1L), null,
                tradeItemMaterialSupport, null, null, null, null
        );

        String csv = "商品编码,品牌,材质,类别,规格,长度,单位,数量单位,件重(吨),每件支数,单价,批号管理,备注\r\n"
                + "MAT-GBK,宝钢,Q235B,板材,10mm,6m,吨,支,1.000,10,500.00,否,\r\n";
        byte[] gbkBytes = csv.getBytes(java.nio.charset.Charset.forName("GBK"));
        MockMultipartFile file = new MockMultipartFile("file", "materials.csv", "text/csv", gbkBytes);

        MaterialImportResultResponse result = service.importCsv(file);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
    }

    @Test
    void shouldImportCsvWithBlankRowsSkipped() throws Exception {
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        TradeItemMaterialSupport tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        when(materialRepository.findByMaterialCode("MAT-001")).thenReturn(Optional.empty());
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeItemMaterialSupport.normalizeBatchNoEnabled(false)).thenReturn(false);

        MaterialService service = new MaterialService(
                materialRepository, new SnowflakeIdGenerator(1L), null,
                tradeItemMaterialSupport, null, null, null, null
        );

        String csv = "商品编码,品牌,材质,类别,规格,长度,单位,数量单位,件重(吨),每件支数,单价,批号管理,备注\r\n"
                + "MAT-001,宝钢,Q235B,板材,10mm,6m,吨,支,1.000,10,500.00,否,\r\n"
                + ",,,,,,,,,,,,\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "materials.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        MaterialImportResultResponse result = service.importCsv(file);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
    }

    @Test
    void shouldImportCsvWithMissingRequiredHeader() {
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        MaterialService service = new MaterialService(
                mock(MaterialRepository.class), new SnowflakeIdGenerator(1L), null,
                tradeItemMaterialSupport, null, null, null, null
        );

        String csv = "品牌,材质,类别,规格,长度,单位,数量单位,件重(吨),每件支数,单价,批号管理,备注\r\n"
                + "宝钢,Q235B,板材,10mm,6m,吨,支,1.000,10,500.00,否,\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "materials.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.importCsv(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入模板缺少列");
    }

    @Test
    void shouldImportCsvWithNegativePieceWeight() throws Exception {
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        MaterialService service = new MaterialService(
                mock(MaterialRepository.class), new SnowflakeIdGenerator(1L), null,
                tradeItemMaterialSupport, null, null, null, null
        );

        String csv = "商品编码,品牌,材质,类别,规格,长度,单位,数量单位,件重(吨),每件支数,单价,批号管理,备注\r\n"
                + "MAT-001,宝钢,Q235B,板材,10mm,6m,吨,支,-1.000,10,500.00,否,\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "materials.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        MaterialImportResultResponse result = service.importCsv(file);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failures().get(0).reason()).contains("件重(吨)");
    }

    @Test
    void shouldImportCsvWithNegativePiecesPerBundle() throws Exception {
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        MaterialService service = new MaterialService(
                mock(MaterialRepository.class), new SnowflakeIdGenerator(1L), null,
                tradeItemMaterialSupport, null, null, null, null
        );

        String csv = "商品编码,品牌,材质,类别,规格,长度,单位,数量单位,件重(吨),每件支数,单价,批号管理,备注\r\n"
                + "MAT-001,宝钢,Q235B,板材,10mm,6m,吨,支,1.000,-5,500.00,否,\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "materials.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        MaterialImportResultResponse result = service.importCsv(file);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failures().get(0).reason()).contains("每件支数");
    }

    @Test
    void shouldImportCsvWithNegativeUnitPrice() throws Exception {
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        MaterialService service = new MaterialService(
                mock(MaterialRepository.class), new SnowflakeIdGenerator(1L), null,
                tradeItemMaterialSupport, null, null, null, null
        );

        String csv = "商品编码,品牌,材质,类别,规格,长度,单位,数量单位,件重(吨),每件支数,单价,批号管理,备注\r\n"
                + "MAT-001,宝钢,Q235B,板材,10mm,6m,吨,支,1.000,10,-500.00,否,\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "materials.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        MaterialImportResultResponse result = service.importCsv(file);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failures().get(0).reason()).contains("单价");
    }

    @Test
    void shouldImportCsvWithInvalidBatchNoEnabled() throws Exception {
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        MaterialService service = new MaterialService(
                mock(MaterialRepository.class), new SnowflakeIdGenerator(1L), null,
                tradeItemMaterialSupport, null, null, null, null
        );

        String csv = "商品编码,品牌,材质,类别,规格,长度,单位,数量单位,件重(吨),每件支数,单价,批号管理,备注\r\n"
                + "MAT-001,宝钢,Q235B,板材,10mm,6m,吨,支,1.000,10,500.00,abc,\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "materials.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        MaterialImportResultResponse result = service.importCsv(file);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failures().get(0).reason()).contains("批号管理");
    }

    @Test
    void shouldImportCsvWithCoilCategoryAndNullPiecesPerBundle() throws Exception {
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        TradeItemMaterialSupport tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        when(materialRepository.findByMaterialCode("MAT-COIL")).thenReturn(Optional.empty());
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeItemMaterialSupport.normalizeBatchNoEnabled(false)).thenReturn(false);

        MaterialService service = new MaterialService(
                materialRepository, new SnowflakeIdGenerator(1L), null,
                tradeItemMaterialSupport, null, null, null, null
        );

        String csv = "商品编码,品牌,材质,类别,规格,长度,单位,数量单位,件重(吨),每件支数,单价,批号管理,备注\r\n"
                + "MAT-COIL,宝钢,HPB300,盘螺,10mm,6m,吨,件,1.000,-,500.00,否,\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "materials.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        MaterialImportResultResponse result = service.importCsv(file);

        assertThat(result.successCount()).isEqualTo(1);
    }

    @Test
    void shouldImportCsvWithInvalidPiecesPerBundle() throws Exception {
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        MaterialService service = new MaterialService(
                mock(MaterialRepository.class), new SnowflakeIdGenerator(1L), null,
                tradeItemMaterialSupport, null, null, null, null
        );

        String csv = "商品编码,品牌,材质,类别,规格,长度,单位,数量单位,件重(吨),每件支数,单价,批号管理,备注\r\n"
                + "MAT-001,宝钢,Q235B,板材,10mm,6m,吨,支,1.000,abc,500.00,否,\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "materials.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        MaterialImportResultResponse result = service.importCsv(file);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failures().get(0).reason()).contains("每件支数");
    }

    @Test
    void shouldImportCsvWithInvalidUnitPrice() throws Exception {
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        MaterialService service = new MaterialService(
                mock(MaterialRepository.class), new SnowflakeIdGenerator(1L), null,
                tradeItemMaterialSupport, null, null, null, null
        );

        String csv = "商品编码,品牌,材质,类别,规格,长度,单位,数量单位,件重(吨),每件支数,单价,批号管理,备注\r\n"
                + "MAT-001,宝钢,Q235B,板材,10mm,6m,吨,支,1.000,10,abc,否,\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "materials.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        MaterialImportResultResponse result = service.importCsv(file);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failures().get(0).reason()).contains("单价");
    }

    @Test
    void shouldImportCsvWithInvalidPieceWeight() throws Exception {
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        MaterialService service = new MaterialService(
                mock(MaterialRepository.class), new SnowflakeIdGenerator(1L), null,
                tradeItemMaterialSupport, null, null, null, null
        );

        String csv = "商品编码,品牌,材质,类别,规格,长度,单位,数量单位,件重(吨),每件支数,单价,批号管理,备注\r\n"
                + "MAT-001,宝钢,Q235B,板材,10mm,6m,吨,支,abc,10,500.00,否,\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "materials.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        MaterialImportResultResponse result = service.importCsv(file);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failures().get(0).reason()).contains("件重(吨)");
    }

    @Test
    void shouldImportCsvWithMissingRequiredField() throws Exception {
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        MaterialService service = new MaterialService(
                mock(MaterialRepository.class), new SnowflakeIdGenerator(1L), null,
                tradeItemMaterialSupport, null, null, null, null
        );

        String csv = "商品编码,品牌,材质,类别,规格,长度,单位,数量单位,件重(吨),每件支数,单价,批号管理,备注\r\n"
                + ",宝钢,Q235B,板材,10mm,6m,吨,支,1.000,10,500.00,否,\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "materials.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        MaterialImportResultResponse result = service.importCsv(file);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failures().get(0).reason()).contains("商品编码");
    }

    @Test
    void shouldImportCsvAndCatchDataAccessException() throws Exception {
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        TradeItemMaterialSupport tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        when(materialRepository.findByMaterialCode("MAT-ERR")).thenReturn(Optional.empty());
        when(materialRepository.save(any(Material.class))).thenThrow(new org.springframework.dao.DataIntegrityViolationException("db error"));

        MaterialService service = new MaterialService(
                materialRepository, new SnowflakeIdGenerator(1L), null,
                tradeItemMaterialSupport, null, null, null, null
        );

        String csv = "商品编码,品牌,材质,类别,规格,长度,单位,数量单位,件重(吨),每件支数,单价,批号管理,备注\r\n"
                + "MAT-ERR,宝钢,Q235B,板材,10mm,6m,吨,支,1.000,10,500.00,否,\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "materials.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        MaterialImportResultResponse result = service.importCsv(file);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failures().get(0).reason()).contains("保存失败");
    }

    @Test
    void shouldImportCsvWithEmptyContent() {
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        MaterialService service = new MaterialService(
                mock(MaterialRepository.class), new SnowflakeIdGenerator(1L), null,
                tradeItemMaterialSupport, null, null, null, null
        );

        String csv = "\uFEFF";
        MockMultipartFile file = new MockMultipartFile("file", "empty.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.importCsv(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入文件不能为空");
    }

    @Test
    void shouldImportCsvWithBatchNoEnabledValues() throws Exception {
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        TradeItemMaterialSupport tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        when(materialRepository.findByMaterialCode(anyString())).thenReturn(Optional.empty());
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeItemMaterialSupport.normalizeBatchNoEnabled(true)).thenReturn(true);
        when(tradeItemMaterialSupport.normalizeBatchNoEnabled(false)).thenReturn(false);

        MaterialService service = new MaterialService(
                materialRepository, new SnowflakeIdGenerator(1L), null,
                tradeItemMaterialSupport, null, null, null, null
        );

        String csv = "商品编码,品牌,材质,类别,规格,长度,单位,数量单位,件重(吨),每件支数,单价,批号管理,备注\r\n"
                + "MAT-001,宝钢,Q235B,板材,10mm,6m,吨,支,1.000,10,500.00,启用,\r\n"
                + "MAT-002,宝钢,Q235B,板材,10mm,6m,吨,支,1.000,10,500.00,关闭,\r\n"
                + "MAT-003,宝钢,Q235B,板材,10mm,6m,吨,支,1.000,10,500.00,1,\r\n"
                + "MAT-004,宝钢,Q235B,板材,10mm,6m,吨,支,1.000,10,500.00,0,\r\n"
                + "MAT-005,宝钢,Q235B,板材,10mm,6m,吨,支,1.000,10,500.00,true,\r\n"
                + "MAT-006,宝钢,Q235B,板材,10mm,6m,吨,支,1.000,10,500.00,false,\r\n"
                + "MAT-007,宝钢,Q235B,板材,10mm,6m,吨,支,1.000,10,500.00,y,\r\n"
                + "MAT-008,宝钢,Q235B,板材,10mm,6m,吨,支,1.000,10,500.00,n,\r\n"
                + "MAT-009,宝钢,Q235B,板材,10mm,6m,吨,支,1.000,10,500.00,,\r\n"
                + "MAT-010,宝钢,Q235B,线材,10mm,6m,吨,件,1.000,-,500.00,否,\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "materials.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        MaterialImportResultResponse result = service.importCsv(file);

        assertThat(result.successCount()).isEqualTo(10);
    }

    @Test
    void shouldReturnPage() {
        Material material = createMaterial(1L, "MAT-001");
        Page<Material> page = new PageImpl<>(List.of(material));
        var repository = (MaterialRepository) Proxy.newProxyInstance(
                MaterialRepository.class.getClassLoader(),
                new Class[]{MaterialRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> {
                        if (args.length == 2 && args[1] instanceof org.springframework.data.domain.Pageable) {
                            yield page;
                        }
                        yield List.of(material);
                    }
                    case "toString" -> "MaterialRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (MaterialMapper) Proxy.newProxyInstance(
                MaterialMapper.class.getClassLoader(),
                new Class[]{MaterialMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Material) args[0]);
                    case "toString" -> "MaterialMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        var service = new MaterialService(repository, new SnowflakeIdGenerator(1), mapper, tradeItemMaterialSupport, null, null, null, null);

        var result = service.page(new PageQuery(0, 10, null, null), null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void shouldSearch() {
        Material material = createMaterial(1L, "MAT-001");
        var repository = (MaterialRepository) Proxy.newProxyInstance(
                MaterialRepository.class.getClassLoader(),
                new Class[]{MaterialRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> new PageImpl<>(List.of(material));
                    case "toString" -> "MaterialRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (MaterialMapper) Proxy.newProxyInstance(
                MaterialMapper.class.getClassLoader(),
                new Class[]{MaterialMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Material) args[0]);
                    case "toString" -> "MaterialMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        var service = new MaterialService(repository, new SnowflakeIdGenerator(1), mapper, tradeItemMaterialSupport, null, null, null, null);

        var result = service.search("MAT", 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).materialCode()).isEqualTo("MAT-001");
    }

    @Test
    void shouldReturnMaterialGrades() {
        var repository = (MaterialRepository) Proxy.newProxyInstance(
                MaterialRepository.class.getClassLoader(),
                new Class[]{MaterialRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findDistinctMaterials" -> List.of("Q235B", "HRB400");
                    case "toString" -> "MaterialRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        var service = new MaterialService(repository, new SnowflakeIdGenerator(1), null, tradeItemMaterialSupport, null, null, null, null);

        var result = service.materialGrades();

        assertThat(result).containsExactly("Q235B", "HRB400");
    }

    @Test
    void shouldDownloadTemplateCsv() {
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        var service = new MaterialService(null, new SnowflakeIdGenerator(1), null, tradeItemMaterialSupport, null, null, null, null);

        byte[] result = service.downloadTemplateCsv();

        assertThat(result).isNotNull();
        String content = new String(result, StandardCharsets.UTF_8);
        assertThat(content).contains("商品编码");
        assertThat(content).contains("RB400-18-12");
    }

    @Test
    void shouldDownloadTemplateFile() {
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        var service = new MaterialService(null, new SnowflakeIdGenerator(1), null, tradeItemMaterialSupport, null, null, null, null);

        FileDownloadResponse result = service.downloadTemplateFile();

        assertThat(result.filename()).isEqualTo("商品资料导入模板.csv");
        assertThat(result.content()).isNotNull();
    }

    @Test
    void shouldExportCsv() {
        Material material = createMaterial(1L, "MAT-001");
        var repository = (MaterialRepository) Proxy.newProxyInstance(
                MaterialRepository.class.getClassLoader(),
                new Class[]{MaterialRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> {
                        if (args.length >= 1 && args[0] instanceof Sort) {
                            yield List.of(material);
                        }
                        yield List.of(material);
                    }
                    case "toString" -> "MaterialRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        var service = new MaterialService(repository, new SnowflakeIdGenerator(1), null, tradeItemMaterialSupport, null, null, null, null);

        byte[] result = service.exportCsv(null);

        assertThat(result).isNotNull();
        String content = new String(result, StandardCharsets.UTF_8);
        assertThat(content).contains("MAT-001");
        assertThat(content).contains("商品编码");
    }

    @Test
    void shouldExportFile() {
        Material material = createMaterial(1L, "MAT-001");
        var repository = (MaterialRepository) Proxy.newProxyInstance(
                MaterialRepository.class.getClassLoader(),
                new Class[]{MaterialRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> List.of(material);
                    case "toString" -> "MaterialRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        var service = new MaterialService(repository, new SnowflakeIdGenerator(1), null, tradeItemMaterialSupport, null, null, null, null);

        FileDownloadResponse result = service.exportFile(null);

        assertThat(result.filename()).isEqualTo("materials.csv");
        assertThat(result.content()).isNotNull();
    }

    @Test
    void shouldExportExcel() {
        Material material = createMaterial(1L, "MAT-001");
        var repository = (MaterialRepository) Proxy.newProxyInstance(
                MaterialRepository.class.getClassLoader(),
                new Class[]{MaterialRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> List.of(material);
                    case "toString" -> "MaterialRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var excelExportService = mock(ExcelExportService.class);
        when(excelExportService.export(any(), eq(MaterialImportDTO.class))).thenReturn(new byte[]{1, 2, 3});
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        var service = new MaterialService(repository, new SnowflakeIdGenerator(1), null, tradeItemMaterialSupport, excelExportService, null, null, null);

        FileDownloadResponse result = service.exportExcel(null);

        assertThat(result.filename()).isEqualTo("material.xlsx");
        assertThat(result.content()).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    void shouldGenerateExcelTemplate() {
        var excelTemplateService = mock(ExcelTemplateService.class);
        when(excelTemplateService.generateTemplate(MaterialImportDTO.class)).thenReturn(new byte[]{4, 5, 6});
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        var service = new MaterialService(null, new SnowflakeIdGenerator(1), null, tradeItemMaterialSupport, null, null, excelTemplateService, null);

        FileDownloadResponse result = service.excelTemplate();

        assertThat(result.filename()).isEqualTo("商品资料导入模板.xlsx");
        assertThat(result.content()).isEqualTo(new byte[]{4, 5, 6});
    }

    @Test
    void shouldImportExcel_createNew() throws Exception {
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        TradeItemMaterialSupport tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        when(materialRepository.findByMaterialCode("MAT-NEW")).thenReturn(Optional.empty());
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeItemMaterialSupport.normalizeBatchNoEnabled(true)).thenReturn(true);

        ExcelImportService excelImportService = mock(ExcelImportService.class);
        List<MaterialImportDTO> dtos = List.of(new MaterialImportDTO(
                "MAT-NEW", "宝钢", "Q235B", "板材", "10mm", "6m", "吨", "支",
                "1.000", "10", "500.00", "是", "备注"
        ));
        when(excelImportService.parseAndValidate(any(MultipartFile.class), eq(MaterialImportDTO.class))).thenReturn(dtos);

        var service = new MaterialService(materialRepository, new SnowflakeIdGenerator(1), null,
                tradeItemMaterialSupport, null, excelImportService, null, null);

        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);
        ImportResult result = service.importExcel(file);

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(0);
        verify(tradeItemMaterialSupport).evictCache();
    }

    @Test
    void shouldImportExcel_updateExisting() throws Exception {
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        TradeItemMaterialSupport tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        Material existing = createMaterial(1L, "MAT-001");
        when(materialRepository.findByMaterialCode("MAT-001")).thenReturn(Optional.of(existing));
        when(materialRepository.existsById(1L)).thenReturn(true);
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeItemMaterialSupport.normalizeBatchNoEnabled(false)).thenReturn(false);

        ExcelImportService excelImportService = mock(ExcelImportService.class);
        List<MaterialImportDTO> dtos = List.of(new MaterialImportDTO(
                "MAT-001", "新品牌", "Q235B", "板材", "20mm", "12m", "吨", "支",
                "2.000", "10", "600.00", "否", ""
        ));
        when(excelImportService.parseAndValidate(any(MultipartFile.class), eq(MaterialImportDTO.class))).thenReturn(dtos);

        var service = new MaterialService(materialRepository, new SnowflakeIdGenerator(1), null,
                tradeItemMaterialSupport, null, excelImportService, null, null);

        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);
        ImportResult result = service.importExcel(file);

        assertThat(result.createdCount()).isEqualTo(0);
        assertThat(result.updatedCount()).isEqualTo(1);
    }

    @Test
    void shouldDelete_whenNoReferences() {
        var repository = (MaterialRepository) Proxy.newProxyInstance(
                MaterialRepository.class.getClassLoader(),
                new Class[]{MaterialRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createMaterial(1L, "MAT-001"));
                    case "findById" -> Optional.of(createMaterial(1L, "MAT-001"));
                    case "save" -> args[0];
                    case "toString" -> "MaterialRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        org.springframework.jdbc.core.JdbcTemplate jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        var service = new MaterialService(repository, new SnowflakeIdGenerator(1), null, tradeItemMaterialSupport, null, null, null, jdbc);

        service.delete(1L);
    }

    @Test
    void shouldThrowException_whenDeleteWithReferences() {
        var repository = (MaterialRepository) Proxy.newProxyInstance(
                MaterialRepository.class.getClassLoader(),
                new Class[]{MaterialRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createMaterial(1L, "MAT-001"));
                    case "findById" -> Optional.of(createMaterial(1L, "MAT-001"));
                    case "toString" -> "MaterialRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        org.springframework.jdbc.core.JdbcTemplate jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(5);
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        var service = new MaterialService(repository, new SnowflakeIdGenerator(1), null, tradeItemMaterialSupport, null, null, null, jdbc);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该商品已被业务单据引用");
    }

    @Test
    void shouldThrowException_whenDetailNotFound() {
        var repository = (MaterialRepository) Proxy.newProxyInstance(
                MaterialRepository.class.getClassLoader(),
                new Class[]{MaterialRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.empty();
                    case "findById" -> Optional.empty();
                    case "toString" -> "MaterialRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        var service = new MaterialService(repository, new SnowflakeIdGenerator(1), null, tradeItemMaterialSupport, null, null, null, null);

        assertThatThrownBy(() -> service.detail(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品不存在");
    }

    @Test
    void shouldCreate_success() {
        var repository = (MaterialRepository) Proxy.newProxyInstance(
                MaterialRepository.class.getClassLoader(),
                new Class[]{MaterialRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByMaterialCodeAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "MaterialRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (MaterialMapper) Proxy.newProxyInstance(
                MaterialMapper.class.getClassLoader(),
                new Class[]{MaterialMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Material) args[0]);
                    case "toString" -> "MaterialMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        when(tradeItemMaterialSupport.normalizeBatchNoEnabled(false)).thenReturn(false);
        var service = new MaterialService(repository, new SnowflakeIdGenerator(1), mapper, tradeItemMaterialSupport, null, null, null, null);

        var request = new MaterialRequest("MAT-001", "宝钢", "Q235B", "板材", "10mm", null, "吨", null, BigDecimal.ONE, 1, BigDecimal.TEN, false, null);
        var result = service.create(request);

        assertThat(result).isNotNull();
        assertThat(result.materialCode()).isEqualTo("MAT-001");
    }

    @Test
    void shouldUpdate_successWithSameCode() {
        var repository = (MaterialRepository) Proxy.newProxyInstance(
                MaterialRepository.class.getClassLoader(),
                new Class[]{MaterialRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createMaterial(1L, "MAT-001"));
                    case "save" -> args[0];
                    case "toString" -> "MaterialRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (MaterialMapper) Proxy.newProxyInstance(
                MaterialMapper.class.getClassLoader(),
                new Class[]{MaterialMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Material) args[0]);
                    case "toString" -> "MaterialMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        when(tradeItemMaterialSupport.normalizeBatchNoEnabled(false)).thenReturn(false);
        var service = new MaterialService(repository, new SnowflakeIdGenerator(1), mapper, tradeItemMaterialSupport, null, null, null, null);

        var request = new MaterialRequest("MAT-001", "新品牌", "Q235B", "板材", "20mm", null, "吨", null, BigDecimal.ONE, 1, BigDecimal.TEN, false, null);
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
        assertThat(result.brand()).isEqualTo("新品牌");
    }

    @Test
    void shouldNormalizeBatchNoEnabledInApply() {
        var repository = (MaterialRepository) Proxy.newProxyInstance(
                MaterialRepository.class.getClassLoader(),
                new Class[]{MaterialRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByMaterialCodeAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "MaterialRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (MaterialMapper) Proxy.newProxyInstance(
                MaterialMapper.class.getClassLoader(),
                new Class[]{MaterialMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Material) args[0]);
                    case "toString" -> "MaterialMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        when(tradeItemMaterialSupport.normalizeBatchNoEnabled(true)).thenReturn(true);
        var service = new MaterialService(repository, new SnowflakeIdGenerator(1), mapper, tradeItemMaterialSupport, null, null, null, null);

        var request = new MaterialRequest("MAT-001", "宝钢", "Q235B", "板材", "10mm", null, "吨", null, BigDecimal.ONE, null, null, true, null);
        var result = service.create(request);

        assertThat(result).isNotNull();
        verify(tradeItemMaterialSupport).normalizeBatchNoEnabled(true);
    }

    @Test
    void shouldExportCsvWithNullValues() {
        Material material = new Material();
        material.setId(1L);
        material.setMaterialCode("MAT-001");
        material.setDeletedFlag(false);
        var repository = (MaterialRepository) Proxy.newProxyInstance(
                MaterialRepository.class.getClassLoader(),
                new Class[]{MaterialRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> List.of(material);
                    case "toString" -> "MaterialRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        var service = new MaterialService(repository, new SnowflakeIdGenerator(1), null, tradeItemMaterialSupport, null, null, null, null);

        byte[] result = service.exportCsv(null);

        assertThat(result).isNotNull();
        String content = new String(result, StandardCharsets.UTF_8);
        assertThat(content).contains("MAT-001");
    }

    private static Material createMaterial(Long id, String code) {
        Material m = new Material();
        m.setId(id);
        m.setMaterialCode(code);
        m.setBrand("宝钢");
        m.setMaterial("Q235B");
        m.setCategory("板材");
        m.setSpec("10mm");
        m.setUnit("吨");
        m.setQuantityUnit("支");
        m.setPieceWeightTon(new BigDecimal("1.000"));
        m.setPiecesPerBundle(10);
        m.setUnitPrice(new BigDecimal("500.00"));
        m.setDeletedFlag(false);
        return m;
    }

    private static MaterialResponse toResponse(Material m) {
        return new MaterialResponse(
                m.getId(), m.getMaterialCode(), m.getBrand(), m.getMaterial(),
                m.getCategory(), m.getSpec(), m.getLength(), m.getUnit(),
                m.getQuantityUnit(), m.getPieceWeightTon(), m.getPiecesPerBundle(),
                m.getUnitPrice(), m.getBatchNoEnabled(), m.getRemark()
        );
    }
}
