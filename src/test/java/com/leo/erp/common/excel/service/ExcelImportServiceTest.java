package com.leo.erp.common.excel.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.excel.annotation.ImportColumn;
import com.leo.erp.common.excel.config.ExcelProperties;
import com.leo.erp.common.excel.dto.ImportResult;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelImportServiceTest {

    private final ExcelProperties properties = new ExcelProperties();
    private final ExcelImportService service = new ExcelImportService(properties);

    @Test
    void shouldRejectNullFile() {
        assertThatThrownBy(() -> service.importFile(null, TestDto.class, (dto, result) -> {}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传文件不能为空");
    }

    @Test
    void shouldRejectEmptyFile() {
        MultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        assertThatThrownBy(() -> service.importFile(file, TestDto.class, (dto, result) -> {}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传文件不能为空");
    }

    @Test
    void shouldRejectOversizedFile() {
        properties.setMaxFileSize(100);
        MultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[200]);

        assertThatThrownBy(() -> service.importFile(file, TestDto.class, (dto, result) -> {}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件大小超过限制");
    }

    @Test
    void shouldRejectUnsupportedExtension() {
        MultipartFile file = new MockMultipartFile("file", "test.exe",
                "application/x-msdownload", "data".getBytes());

        assertThatThrownBy(() -> service.importFile(file, TestDto.class, (dto, result) -> {}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型");
    }

    @Test
    void shouldRejectDtoWithoutImportColumn() {
        MultipartFile file = csvFile("名称,数量\r\nval,10");

        assertThatThrownBy(() -> service.importFile(file, NoAnnotationDto.class, (dto, result) -> {}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少 @ImportColumn 注解");
    }

    @Test
    void shouldRejectEmptyFileAfterHeader() throws IOException {
        MultipartFile file = csvFile("名称,数量\r\n");

        ImportResult result = service.importFile(file, TestDto.class, (dto, r) -> {});

        assertThat(result.totalRows()).isEqualTo(0);
        assertThat(result.successCount()).isEqualTo(0);
    }

    @Test
    void shouldResolveFieldsForAnnotatedDto() {
        List<ExcelImportService.FieldMeta> fields = service.resolveFields(TestDto.class);

        assertThat(fields).hasSize(2);
        assertThat(fields.get(0).header()).isEqualTo("名称");
        assertThat(fields.get(1).header()).isEqualTo("数量");
    }

    @Test
    void shouldNormalizeHeader() {
        assertThat(ExcelImportService.normalizeHeader(" 用户 名称 ")).isEqualTo("用户名称");
        assertThat(ExcelImportService.normalizeHeader(null)).isEqualTo("");
        assertThat(ExcelImportService.normalizeHeader("  ABC  ")).isEqualTo("abc");
    }

    @Test
    void shouldDetectKnownHeadersByCsvContent() throws IOException {
        MultipartFile file = csvFile("名称,数量\r\n商品A,10");

        List<TestDto> result = service.parseAndValidate(file, TestDto.class);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("商品A");
        assertThat(result.get(0).quantity()).isEqualTo(10);
    }

    @Test
    void shouldCollectAllErrorsInParseAndValidate() {
        MultipartFile file = csvFile("名称,数量\r\n,abc");

        assertThatThrownBy(() -> service.parseAndValidate(file, TestDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入数据校验失败");
    }

    @Test
    void shouldSkipBlankRows() throws IOException {
        MultipartFile file = csvFile("名称,数量\r\n商品A,10\r\n,\r\n商品B,20");

        List<TestDto> result = service.parseAndValidate(file, TestDto.class);

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldHandleMissingHeader() {
        MultipartFile file = csvFile("其他,数量\r\nval,10");

        assertThatThrownBy(() -> service.parseAndValidate(file, TestDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入模板缺少列");
    }

    @Test
    void shouldHandleRequiredFieldValidation() {
        MultipartFile file = csvFile("名称,数量\r\n,10");

        assertThatThrownBy(() -> service.parseAndValidate(file, TestDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void shouldHandleRegexValidation() {
        MultipartFile file = csvFile("名称,数量\r\n商品A,abc");

        assertThatThrownBy(() -> service.parseAndValidate(file, RegexTestDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式不正确");
    }

    @Test
    void shouldHandleConversionError() {
        MultipartFile file = csvFile("名称,数量\r\n商品A,not-a-number");

        assertThatThrownBy(() -> service.parseAndValidate(file, TestDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式不正确");
    }

    @Test
    void shouldHandleImportWithPersister() throws IOException {
        MultipartFile file = csvFile("名称,数量\r\n商品A,10");

        ImportResult result = service.importFile(file, TestDto.class, (dto, r) -> {});

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
    }

    @Test
    void shouldCollectErrorsInImportFile() throws IOException {
        MultipartFile file = csvFile("名称,数量\r\n,not-a-number");

        ImportResult result = service.importFile(file, TestDto.class, (dto, r) -> {});

        assertThat(result.failCount()).isEqualTo(2);
        assertThat(result.errors()).hasSize(2);
    }

    @Test
    void shouldHandleCsvWithGbkEncoding() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv",
                new byte[]{(byte) 0xFF, (byte) 0xFE, 0x6E, 0x61, 0x6D, 0x65, 0x0D, 0x0A});

        assertThatThrownBy(() -> service.parseAndValidate(file, TestDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入模板缺少列");
    }

    @Test
    void shouldHandleEnumValidation() {
        MultipartFile file = csvFile("类型\r\n未知");

        assertThatThrownBy(() -> service.parseAndValidate(file, EnumTestDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须是以下值之一");
    }

    @Test
    void shouldThrow_whenDtoHasNoImportColumns() {
        assertThatThrownBy(() -> service.parseAndValidate(csvFile("名称\r\ntest"), NoAnnotationDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少 @ImportColumn 注解");
    }

    @Test
    void shouldParseXlsxAndConvertSupportedScalarTypes() throws IOException {
        MultipartFile file = xlsxFile(
                List.of("名称", "长整型", "金额", "启用", "比例"),
                List.of("商品A", "9223372036854775807", "123.45", "是", "1.25"),
                List.of("商品B", "2", "0", "no", "2.5")
        );

        List<ScalarDto> result = service.parseAndValidate(file, ScalarDto.class);

        assertThat(result).containsExactly(
                new ScalarDto("商品A", Long.MAX_VALUE, new BigDecimal("123.45"), true, 1.25),
                new ScalarDto("商品B", 2L, BigDecimal.ZERO, false, 2.5)
        );
    }

    @Test
    void shouldRejectEmptyXlsxWorkbook() throws IOException {
        MultipartFile file = xlsxFile(List.of());

        assertThatThrownBy(() -> service.parseAndValidate(file, TestDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入文件不能为空");
    }

    @Test
    void shouldRejectEmptyXlsxWorkbookInImportFile() throws IOException {
        MultipartFile file = xlsxFile(List.of());

        assertThatThrownBy(() -> service.importFile(file, TestDto.class, (dto, result) -> {}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入文件不能为空");
    }

    @Test
    void shouldStripUtf8BomFromCsvHeader() throws IOException {
        MultipartFile file = csvFile("\uFEFF名称,数量\r\n商品A,10");

        List<TestDto> result = service.parseAndValidate(file, TestDto.class);

        assertThat(result).containsExactly(new TestDto("商品A", 10));
    }

    @Test
    void shouldFallbackToGbkWhenCsvHeadersArePlainAscii() {
        byte[] gbkBytes = "name,quantity\r\n商品A,10".getBytes(java.nio.charset.Charset.forName("GBK"));
        MultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", gbkBytes);

        assertThatThrownBy(() -> service.parseAndValidate(file, TestDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入模板缺少列");
    }

    @Test
    void shouldReturnRawStringForUnsupportedTargetType() throws IOException {
        MultipartFile file = csvFile("值\r\nraw");

        List<UnsupportedTypeDto> result = service.parseAndValidate(file, UnsupportedTypeDto.class);

        assertThat(result).containsExactly(new UnsupportedTypeDto("raw"));
    }

    @Test
    void shouldReturnNullForMissingTrailingOptionalColumn() throws IOException {
        MultipartFile file = csvFile("名称,备注\r\n商品A");

        List<OptionalTailDto> result = service.parseAndValidate(file, OptionalTailDto.class);

        assertThat(result).containsExactly(new OptionalTailDto("商品A", null));
    }

    @Test
    void shouldRejectUnknownBooleanValue() {
        MultipartFile file = csvFile("启用\r\nmaybe");

        assertThatThrownBy(() -> service.parseAndValidate(file, BooleanDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式不正确");
    }

    @Test
    void shouldCollectRegexAndEnumErrorsInImportFile() throws IOException {
        MultipartFile file = csvFile("编码,类型\r\nabc,C");

        ImportResult result = service.importFile(file, ImportValidationDto.class, (dto, r) -> {});

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.failCount()).isEqualTo(2);
        assertThat(result.errors())
                .extracting(error -> error.message())
                .containsExactly("编码 格式不正确", "类型 必须是以下值之一：A, B");
    }

    @Test
    void shouldSkipBlankRowsInImportFile() throws IOException {
        MultipartFile file = csvFile("名称,数量\r\n,\r\n商品A,10");

        ImportResult result = service.importFile(file, TestDto.class, (dto, r) -> {});

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.successRows()).containsExactly(new TestDto("商品A", 10));
    }

    @Test
    void shouldCollectRecordConstructionErrorInImportFile() throws IOException {
        MultipartFile file = csvFile("名称\r\nboom");

        ImportResult result = service.importFile(file, ThrowingDto.class, (dto, r) -> {});

        assertThat(result.failCount()).isEqualTo(1);
        assertThat(result.errors().getFirst().message()).contains("保存失败");
    }

    @Test
    void shouldCollectRecordConstructionErrorInParseAndValidate() {
        MultipartFile file = csvFile("名称\r\nboom");

        assertThatThrownBy(() -> service.parseAndValidate(file, ThrowingDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("保存失败");
    }

    @Test
    void shouldCollectRequiredNullErrorsWhenRequiredTrailingColumnIsMissing() throws IOException {
        MultipartFile file = csvFile("名称,数量\r\n商品A");

        ImportResult result = service.importFile(file, TestDto.class, (dto, r) -> {});

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.failCount()).isEqualTo(1);
        assertThat(result.errors().getFirst().message()).isEqualTo("数量 不能为空");
    }

    @Test
    void shouldRejectRequiredNullInParseAndValidateWhenTrailingColumnIsMissing() {
        MultipartFile file = csvFile("名称,数量\r\n商品A");

        assertThatThrownBy(() -> service.parseAndValidate(file, TestDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第2行【数量】不能为空");
    }

    @Test
    void shouldAcceptRegexAndEnumValuesInImportFile() throws IOException {
        MultipartFile file = csvFile("编码,类型\r\n123,A");

        ImportResult result = service.importFile(file, ImportValidationDto.class, (dto, r) -> {});

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void shouldAcceptRegexAndEnumValuesInParseAndValidate() throws IOException {
        MultipartFile file = csvFile("编码,类型\r\n123,B");

        List<ImportValidationDto> result = service.parseAndValidate(file, ImportValidationDto.class);

        assertThat(result).containsExactly(new ImportValidationDto("123", "B"));
    }

    @Test
    void shouldIgnoreBlankOptionalRegexAndEnumValuesInImportFile() throws IOException {
        MultipartFile file = csvFile("编码,类型,名称\r\n , ,商品A");

        ImportResult result = service.importFile(file, OptionalValidationDto.class, (dto, r) -> {});

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successRows()).containsExactly(new OptionalValidationDto(null, null, "商品A"));
    }

    @Test
    void shouldIgnoreBlankOptionalRegexAndEnumValuesInParseAndValidate() throws IOException {
        MultipartFile file = csvFile("编码,类型,名称\r\n , ,商品A");

        List<OptionalValidationDto> result = service.parseAndValidate(file, OptionalValidationDto.class);

        assertThat(result).containsExactly(new OptionalValidationDto(null, null, "商品A"));
    }

    @Test
    void shouldIgnoreMissingOptionalRegexAndEnumValuesInImportFile() throws IOException {
        MultipartFile file = csvFile("名称,编码,类型\r\n商品A");

        ImportResult result = service.importFile(file, OptionalValidationDto.class, (dto, r) -> {});

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successRows()).containsExactly(new OptionalValidationDto(null, null, "商品A"));
    }

    @Test
    void shouldIgnoreMissingOptionalRegexAndEnumValuesInParseAndValidate() throws IOException {
        MultipartFile file = csvFile("名称,编码,类型\r\n商品A");

        List<OptionalValidationDto> result = service.parseAndValidate(file, OptionalValidationDto.class);

        assertThat(result).containsExactly(new OptionalValidationDto(null, null, "商品A"));
    }

    @Test
    void shouldConvertPrimitiveScalarTypes() throws IOException {
        MultipartFile file = csvFile("数量,长整型,启用,比例\r\n7,8,true,3.5");

        List<PrimitiveScalarDto> result = service.parseAndValidate(file, PrimitiveScalarDto.class);

        assertThat(result).containsExactly(new PrimitiveScalarDto(7, 8L, true, 3.5));
    }

    @Test
    void shouldReturnNullForBlankOptionalValue() throws IOException {
        MultipartFile file = csvFile("名称,备注\r\n商品A, ");

        List<OptionalTailDto> result = service.parseAndValidate(file, OptionalTailDto.class);

        assertThat(result).containsExactly(new OptionalTailDto("商品A", null));
    }

    @Test
    void shouldParseXlsxWhenOriginalFilenameIsNull() throws IOException {
        MultipartFile file = xlsxFile((String) null, List.of("名称", "数量"), List.of("商品A", "10"));

        List<TestDto> result = service.parseAndValidate(file, TestDto.class);

        assertThat(result).containsExactly(new TestDto("商品A", 10));
    }

    @Test
    void shouldRejectCsvWithOnlyBomAsEmptyImportFile() {
        MultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv",
                "\uFEFF".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.parseAndValidate(file, TestDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入文件不能为空");
    }

    @Test
    void shouldFallbackForCsvWithBlankHeaders() {
        MultipartFile file = csvFile(",\r\n商品A,10");

        assertThatThrownBy(() -> service.parseAndValidate(file, TestDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入模板缺少列");
    }

    @Test
    void shouldSkipSparseBlankXlsxRows() throws IOException {
        MultipartFile file = xlsxFile(
                List.of("名称", "数量"),
                Arrays.asList(null, ""),
                List.of("商品A", "10")
        );

        List<TestDto> result = service.parseAndValidate(file, TestDto.class);

        assertThat(result).containsExactly(new TestDto("商品A", 10));
    }

    @Test
    void shouldReadSparseXlsxRowsWithNullHeaderAndCellValues() throws IOException {
        MultipartFile file = xlsxFile(
                Arrays.asList("名称", null, "备注", "忽略"),
                Arrays.asList("商品A", null, null, "ignored")
        );

        List<OptionalTailDto> result = service.parseAndValidate(file, OptionalTailDto.class);

        assertThat(result).containsExactly(new OptionalTailDto("商品A", null));
    }

    @Test
    void shouldRejectExcelWorkbookWithoutSheet() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx".getBytes());
        Workbook workbook = Mockito.mock(Workbook.class);

        try (MockedStatic<WorkbookFactory> factory = Mockito.mockStatic(WorkbookFactory.class)) {
            factory.when(() -> WorkbookFactory.create(Mockito.any(InputStream.class))).thenReturn(workbook);
            Mockito.when(workbook.getSheetAt(0)).thenReturn(null);

            assertThatThrownBy(() -> service.parseAndValidate(file, TestDto.class))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Excel 文件无工作表");
        }
    }

    @Test
    void shouldTreatMissingPrivateCellIndexAsNull() throws Exception {
        String value = invokePrivate("getCellValue",
                new Class<?>[]{List.class, Map.class, String.class},
                List.of("商品A"), Map.of(), "名称");

        assertThat(value).isNull();
    }

    @Test
    void shouldTreatNullPrivateCsvHeaderAsUnknown() throws Exception {
        Boolean result = invokePrivate("hasKnownHeaders",
                new Class<?>[]{List.class},
                Arrays.asList(null, "name"));

        assertThat(result).isFalse();
    }

    @Test
    void shouldStripBomFromEmptyPrivateContent() throws Exception {
        String content = invokePrivate("decodeAndStripBom",
                new Class<?>[]{byte[].class, java.nio.charset.Charset.class},
                new byte[0], StandardCharsets.UTF_8);

        assertThat(content).isEmpty();
    }

    record TestDto(
            @ImportColumn(header = "名称", required = true, order = 1)
            String name,
            @ImportColumn(header = "数量", required = true, order = 2)
            Integer quantity
    ) {}

    record NoAnnotationDto(String name) {}

    record RegexTestDto(
            @ImportColumn(header = "名称", order = 1)
            String name,
            @ImportColumn(header = "数量", regex = "\\d+", order = 2)
            String quantity
    ) {}

    record EnumTestDto(
            @ImportColumn(header = "类型", enumValues = {"A", "B", "C"}, order = 1)
            String type
    ) {}

    record ScalarDto(
            @ImportColumn(header = "名称", order = 1)
            String name,
            @ImportColumn(header = "长整型", order = 2)
            Long longValue,
            @ImportColumn(header = "金额", order = 3)
            BigDecimal amount,
            @ImportColumn(header = "启用", order = 4)
            Boolean enabled,
            @ImportColumn(header = "比例", order = 5)
            Double ratio
    ) {}

    record OptionalTailDto(
            @ImportColumn(header = "名称", order = 1)
            String name,
            @ImportColumn(header = "备注", order = 2)
            String remark
    ) {}

    record BooleanDto(
            @ImportColumn(header = "启用", order = 1)
            Boolean enabled
    ) {}

    record ImportValidationDto(
            @ImportColumn(header = "编码", regex = "\\d+", order = 1)
            String code,
            @ImportColumn(header = "类型", enumValues = {"A", "B"}, order = 2)
            String type
    ) {}

    record OptionalValidationDto(
            @ImportColumn(header = "编码", regex = "\\d+", order = 1)
            String code,
            @ImportColumn(header = "类型", enumValues = {"A", "B"}, order = 2)
            String type,
            @ImportColumn(header = "名称", order = 3)
            String name
    ) {}

    record PrimitiveScalarDto(
            @ImportColumn(header = "数量", order = 1)
            int quantity,
            @ImportColumn(header = "长整型", order = 2)
            long longValue,
            @ImportColumn(header = "启用", order = 3)
            boolean enabled,
            @ImportColumn(header = "比例", order = 4)
            double ratio
    ) {}

    record ThrowingDto(
            @ImportColumn(header = "名称", order = 1)
            String name
    ) {
        ThrowingDto {
            throw new IllegalStateException("boom");
        }
    }

    record UnsupportedTypeDto(
            @ImportColumn(header = "值", order = 1)
            Object value
    ) {}

    private MultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "test.csv", "text/csv", content.getBytes());
    }

    private MultipartFile xlsxFile(List<String> headers, List<?>... rows) throws IOException {
        return xlsxFile("test.xlsx", headers, rows);
    }

    private MultipartFile xlsxFile(String originalFilename, List<String> headers, List<?>... rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Data");
            if (!headers.isEmpty()) {
                var headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.size(); i++) {
                    String header = headers.get(i);
                    if (header != null) {
                        headerRow.createCell(i).setCellValue(header);
                    }
                }
            }
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                var row = sheet.createRow(rowIndex + 1);
                List<?> values = rows[rowIndex];
                for (int cellIndex = 0; cellIndex < values.size(); cellIndex++) {
                    Object value = values.get(cellIndex);
                    if (value != null) {
                        row.createCell(cellIndex).setCellValue(String.valueOf(value));
                    }
                }
            }
            workbook.write(output);
            byte[] content = output.toByteArray();
            if (originalFilename == null) {
                return multipartFileWithoutOriginalFilename(content);
            }
            return new MockMultipartFile(
                    "file",
                    originalFilename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    content
            );
        }
    }

    private MultipartFile multipartFileWithoutOriginalFilename(byte[] content) throws IOException {
        MultipartFile file = Mockito.mock(MultipartFile.class);
        Mockito.when(file.isEmpty()).thenReturn(content.length == 0);
        Mockito.when(file.getSize()).thenReturn((long) content.length);
        Mockito.when(file.getOriginalFilename()).thenReturn(null);
        Mockito.when(file.getInputStream()).thenAnswer(ignored -> new ByteArrayInputStream(content));
        Mockito.when(file.getBytes()).thenReturn(content);
        return file;
    }

    @SuppressWarnings("unchecked")
    private <T> T invokePrivate(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        var method = ExcelImportService.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(service, args);
    }
}
