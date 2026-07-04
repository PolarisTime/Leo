package com.leo.erp.common.excel.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.excel.annotation.ImportColumn;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExcelTemplateServiceTest {

    private final ExcelTemplateService service = new ExcelTemplateService();

    @Test
    void shouldGenerateTemplateBytes() {
        byte[] template = service.generateTemplate(TestTemplateDto.class);

        assertThat(template).isNotEmpty();
    }

    @Test
    void shouldThrow_whenDtoHasNoAnnotations() {
        assertThatThrownBy(() -> service.generateTemplate(NoAnnotationDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少 @ImportColumn 注解");
    }

    @Test
    void shouldResolveFields() {
        List<ExcelTemplateService.TemplateField> fields = service.resolveFields(TestTemplateDto.class);

        assertThat(fields).hasSize(2);
        assertThat(fields.get(0).header()).isEqualTo("名称");
        assertThat(fields.get(1).header()).isEqualTo("数量");
    }

    @Test
    void shouldResolveFieldsWithEnumValues() {
        List<ExcelTemplateService.TemplateField> fields = service.resolveFields(EnumTemplateDto.class);

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).enumValues()).containsExactly("A", "B", "C");
    }

    @Test
    void shouldBuildFormatDescription() {
        List<ExcelTemplateService.TemplateField> fields = service.resolveFields(RequiredRegexDto.class);

        assertThat(fields.get(0).required()).isTrue();
        assertThat(fields.get(0).regex()).isNotBlank();
    }

    @Test
    void shouldWriteFormatDescriptionsToHelpSheet() throws IOException {
        byte[] template = service.generateTemplate(FormatDescriptionDto.class);

        try (Workbook workbook = workbook(template)) {
            Sheet sheet = workbook.getSheet("填写说明");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("必填；需符合规则：^[A-Z]+$");
            assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("需符合规则：^[0-9]+$");
            assertThat(sheet.getRow(3).getCell(2).getStringCellValue()).isEqualTo("可选：A、B");
            assertThat(sheet.getRow(4).getCell(2).getStringCellValue()).isEqualTo("必填；可选：是、否");
        }
    }

    @Test
    void shouldLeaveExampleCellsBlank_whenExamplesAreNullOrBlank() throws IOException {
        ExcelTemplateService customService = new ExcelTemplateService() {
            @Override
            List<ExcelTemplateService.TemplateField> resolveFields(Class<?> dtoClass) {
                return List.of(
                        new ExcelTemplateService.TemplateField("空白示例", false, "   ", "", 1, new String[0]),
                        new ExcelTemplateService.TemplateField("空示例", false, null, "", 2, new String[0])
                );
            }
        };

        byte[] template = customService.generateTemplate(TestTemplateDto.class);

        try (Workbook workbook = workbook(template)) {
            Row row = workbook.getSheet("数据模板").getRow(1);
            assertBlankCell(row.getCell(0));
            assertBlankCell(row.getCell(1));
        }
    }

    @Test
    void shouldHandleEmptyFields() {
        List<ExcelTemplateService.TemplateField> fields = service.resolveFields(NoAnnotationDto.class);

        assertThat(fields).isEmpty();
    }

    @Test
    void shouldWrapIOExceptionWhenWorkbookWriteFails() {
        try (MockedConstruction<org.apache.poi.xssf.usermodel.XSSFWorkbook> ignored =
                     Mockito.mockConstruction(org.apache.poi.xssf.usermodel.XSSFWorkbook.class, (workbook, context) -> {
                         Sheet sheet = mock(Sheet.class);
                         Row row = mock(Row.class);
                         Cell cell = mock(Cell.class);
                         org.apache.poi.xssf.usermodel.XSSFSheet xssfSheet =
                                 mock(org.apache.poi.xssf.usermodel.XSSFSheet.class);
                         org.apache.poi.xssf.usermodel.XSSFRow xssfRow =
                                 mock(org.apache.poi.xssf.usermodel.XSSFRow.class);
                         org.apache.poi.xssf.usermodel.XSSFCell xssfCell =
                                 mock(org.apache.poi.xssf.usermodel.XSSFCell.class);
                         org.apache.poi.xssf.usermodel.XSSFCellStyle style =
                                 mock(org.apache.poi.xssf.usermodel.XSSFCellStyle.class);
                         org.apache.poi.xssf.usermodel.XSSFFont font =
                                 mock(org.apache.poi.xssf.usermodel.XSSFFont.class);

                         when(workbook.createSheet(anyString())).thenReturn(xssfSheet);
                         when(workbook.createCellStyle()).thenReturn(style);
                         when(workbook.createFont()).thenReturn(font);
                         when(xssfSheet.createRow(anyInt())).thenReturn(xssfRow);
                         when(xssfRow.createCell(anyInt())).thenReturn(xssfCell);
                         doThrow(new IOException("write failed")).when(workbook).write(any(OutputStream.class));
                     })) {
            assertThatThrownBy(() -> service.generateTemplate(TestTemplateDto.class))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("生成导入模板失败")
                    .hasCauseInstanceOf(IOException.class);
        }
    }

    private static Workbook workbook(byte[] content) throws IOException {
        return WorkbookFactory.create(new ByteArrayInputStream(content));
    }

    private static void assertBlankCell(Cell cell) {
        assertThat(cell).isNotNull();
        assertThat(cell.getCellType()).isEqualTo(CellType.BLANK);
    }

    record TestTemplateDto(
            @ImportColumn(header = "名称", required = true, example = "商品名称", order = 1)
            String name,
            @ImportColumn(header = "数量", example = "100", order = 2)
            Integer quantity
    ) {}

    record EnumTemplateDto(
            @ImportColumn(header = "类型", enumValues = {"A", "B", "C"}, order = 1)
            String type
    ) {}

    record RequiredRegexDto(
            @ImportColumn(header = "编码", required = true, regex = "^[A-Z]+$", order = 1)
            String code
    ) {}

    record FormatDescriptionDto(
            @ImportColumn(header = "必填正则", required = true, regex = "^[A-Z]+$", order = 1)
            String requiredRegex,
            @ImportColumn(header = "正则", regex = "^[0-9]+$", order = 2)
            String regexOnly,
            @ImportColumn(header = "枚举", enumValues = {"A", "B"}, order = 3)
            String enumOnly,
            @ImportColumn(header = "必填枚举", required = true, enumValues = {"是", "否"}, order = 4)
            String requiredEnum
    ) {}

    record NoAnnotationDto(String name) {}
}
