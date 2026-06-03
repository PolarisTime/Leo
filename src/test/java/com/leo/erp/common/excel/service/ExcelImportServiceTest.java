package com.leo.erp.common.excel.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.excel.annotation.ImportColumn;
import com.leo.erp.common.excel.config.ExcelProperties;
import com.leo.erp.common.excel.dto.ImportResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

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

    private MultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "test.csv", "text/csv", content.getBytes());
    }
}
