package com.leo.erp.common.excel.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.excel.annotation.ExportColumn;
import com.leo.erp.common.excel.config.ExcelProperties;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.streaming.SXSSFCell;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class ExcelExportServiceTest {

    private final ExcelProperties properties = new ExcelProperties();
    private final ExcelExportService service = new ExcelExportService(properties);

    @Test
    void shouldExportBytes() throws IOException {
        List<ExportTestDto> rows = List.of(
                new ExportTestDto("商品A", new BigDecimal("10.50")),
                new ExportTestDto("商品B", new BigDecimal("20.00"))
        );

        byte[] result = service.export(rows, ExportTestDto.class);

        assertThat(result).isNotEmpty();
        try (Workbook workbook = workbook(result)) {
            Sheet sheet = workbook.getSheet("Data");
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("名称");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("金额");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("商品A");
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(10.50D);
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("商品B");
            assertThat(sheet.getRow(2).getCell(1).getNumericCellValue()).isEqualTo(20.00D);
        }
    }

    @Test
    void shouldThrow_whenRowCountExceedsMax() {
        properties.setMaxExportRows(1);
        List<ExportTestDto> rows = List.of(
                new ExportTestDto("A", BigDecimal.ONE),
                new ExportTestDto("B", BigDecimal.valueOf(2))
        );

        assertThatThrownBy(() -> service.export(rows, ExportTestDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导出数据超过限制");
    }

    @Test
    void shouldThrow_whenDtoHasNoAnnotations() {
        assertThatThrownBy(() -> service.export(List.of(new NoExportDto("test")), NoExportDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少 @ExportColumn 注解");
    }

    @Test
    void shouldExportHeaderOnly_whenRowsAreEmpty() throws IOException {
        byte[] result = service.export(List.<ExportTestDto>of(), ExportTestDto.class);

        try (Workbook workbook = workbook(result)) {
            Sheet sheet = workbook.getSheet("Data");
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("名称");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("金额");
            assertThat(sheet.getRow(1)).isNull();
        }
    }

    @Test
    void shouldResolveColumns() {
        List<ExcelExportService.ColumnMeta> columns = service.resolveColumns(ExportTestDto.class);

        assertThat(columns).hasSize(2);
        assertThat(columns.get(0).header()).isEqualTo("名称");
        assertThat(columns.get(1).header()).isEqualTo("金额");
    }

    @Test
    void shouldExportWithDateFields() throws IOException {
        LocalDateTime dateTime = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        LocalDate date = LocalDate.of(2026, 1, 3);
        List<DateTestDto> rows = List.of(
                new DateTestDto("A", dateTime, date)
        );

        byte[] result = service.export(rows, DateTestDto.class);

        assertThat(result).isNotEmpty();
        try (Workbook workbook = workbook(result)) {
            Row row = workbook.getSheet("Data").getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("A");
            assertThat(row.getCell(1).getNumericCellValue())
                    .isEqualTo(dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            assertThat(row.getCell(1).getCellStyle().getDataFormatString()).isEqualTo("yyyy-MM-dd");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("2026-01-03");
        }
    }

    @Test
    void shouldExportWithBooleanFields() throws IOException {
        List<BooleanTestDto> rows = List.of(
                new BooleanTestDto(true),
                new BooleanTestDto(false)
        );

        byte[] result = service.export(rows, BooleanTestDto.class);

        assertThat(result).isNotEmpty();
        try (Workbook workbook = workbook(result)) {
            Sheet sheet = workbook.getSheet("Data");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("是");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("否");
        }
    }

    @Test
    void shouldExportWithCustomFormat() throws IOException {
        List<FormattedTestDto> rows = List.of(
                new FormattedTestDto(new BigDecimal("1234.5678"))
        );

        byte[] result = service.export(rows, FormattedTestDto.class);

        assertThat(result).isNotEmpty();
        try (Workbook workbook = workbook(result)) {
            Cell cell = workbook.getSheet("Data").getRow(1).getCell(0);
            assertThat(cell.getNumericCellValue()).isEqualTo(1234.5678D);
            assertThat(cell.getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
        }
    }

    @Test
    void shouldHandleNullValues() throws IOException {
        List<ExportTestDto> rows = List.of(
                new ExportTestDto(null, null)
        );

        byte[] result = service.export(rows, ExportTestDto.class);

        assertThat(result).isNotEmpty();
        try (Workbook workbook = workbook(result)) {
            Row row = workbook.getSheet("Data").getRow(1);
            assertBlankCell(row.getCell(0));
            assertBlankCell(row.getCell(1));
        }
    }

    @Test
    void shouldExportInstantBigDecimalAndFallbackObjectValues() throws IOException {
        byte[] result = service.export(
                List.of(new MixedValueDto(
                        Instant.ofEpochMilli(1_234_567_890L),
                        new BigDecimal("12.345"),
                        new CustomValue("自定义")
                )),
                MixedValueDto.class
        );

        try (Workbook workbook = workbook(result)) {
            Row row = workbook.getSheet("Data").getRow(1);
            assertThat(row.getCell(0).getNumericCellValue()).isEqualTo(1_234_567_890D);
            assertThat(row.getCell(1).getNumericCellValue()).isEqualTo(12.345D);
            assertThat(row.getCell(1).getCellStyle().getDataFormatString()).isEqualTo("#,##0.000");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("自定义");
        }
    }

    @Test
    void shouldSortColumnsByOrderAndUseConfiguredWidth() throws IOException {
        List<ExcelExportService.ColumnMeta> columns = service.resolveColumns(OrderedDto.class);

        assertThat(columns)
                .extracting(ExcelExportService.ColumnMeta::header)
                .containsExactly("第一列", "第二列");
        assertThat(columns.get(0).width()).isEqualTo(5000);

        byte[] result = service.export(List.of(new OrderedDto("第二值", "第一值")), OrderedDto.class);

        try (Workbook workbook = workbook(result)) {
            Sheet sheet = workbook.getSheet("Data");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("第一列");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("第二列");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("第一值");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("第二值");
            assertThat(sheet.getColumnWidth(0)).isEqualTo(5000);
        }
    }

    @Test
    void shouldLeaveCellBlankWhenGetterCannotBeInvoked() throws IOException {
        Logger logger = (Logger) LoggerFactory.getLogger(ExcelExportService.class);
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.OFF);
        byte[] result;
        try {
            result = service.export(List.of(new ThrowingGetterDto("隐藏值")), ThrowingGetterDto.class);
        } finally {
            logger.setLevel(originalLevel);
        }

        try (Workbook workbook = workbook(result)) {
            assertBlankCell(workbook.getSheet("Data").getRow(1).getCell(0));
        }
    }

    @Test
    void shouldWrapIoException_whenWorkbookWriteFails() throws IOException {
        try (MockedConstruction<SXSSFWorkbook> ignored = mockConstruction(SXSSFWorkbook.class,
                (workbook, context) -> stubFailingWorkbook(workbook))) {
            assertThatThrownBy(() -> service.export(List.<ExportTestDto>of(), ExportTestDto.class))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("导出 XLSX 失败")
                    .hasCauseInstanceOf(IOException.class)
                    .hasRootCauseMessage("write failed");
        }
    }

    private static Workbook workbook(byte[] content) throws IOException {
        return WorkbookFactory.create(new ByteArrayInputStream(content));
    }

    private static void assertBlankCell(Cell cell) {
        if (cell != null) {
            assertThat(cell.getCellType()).isEqualTo(CellType.BLANK);
        }
    }

    private static void stubFailingWorkbook(SXSSFWorkbook workbook) throws IOException {
        SXSSFSheet sheet = mock(SXSSFSheet.class);
        CreationHelper helper = mock(CreationHelper.class);
        DataFormat dataFormat = mock(DataFormat.class);
        CellStyle headerStyle = mock(CellStyle.class);
        CellStyle dateStyle = mock(CellStyle.class);
        Font headerFont = mock(Font.class);
        SXSSFRow headerRow = mock(SXSSFRow.class);
        SXSSFCell headerCell = mock(SXSSFCell.class);

        when(workbook.createSheet("Data")).thenReturn(sheet);
        when(workbook.getCreationHelper()).thenReturn(helper);
        when(workbook.createCellStyle()).thenReturn(headerStyle, dateStyle);
        when(workbook.createFont()).thenReturn(headerFont);
        when(helper.createDataFormat()).thenReturn(dataFormat);
        when(sheet.createRow(0)).thenReturn(headerRow);
        when(headerRow.createCell(anyInt())).thenReturn(headerCell);
        doThrow(new IOException("write failed")).when(workbook).write(any(ByteArrayOutputStream.class));
    }

    record ExportTestDto(
            @ExportColumn(header = "名称", order = 1)
            String name,
            @ExportColumn(header = "金额", order = 2)
            BigDecimal amount
    ) {}

    record DateTestDto(
            @ExportColumn(header = "名称", order = 1)
            String name,
            @ExportColumn(header = "时间", order = 2)
            LocalDateTime dateTime,
            @ExportColumn(header = "日期", order = 3)
            LocalDate date
    ) {}

    record BooleanTestDto(
            @ExportColumn(header = "启用", order = 1)
            Boolean enabled
    ) {}

    record FormattedTestDto(
            @ExportColumn(header = "金额", order = 1, format = "#,##0.00")
            BigDecimal amount
    ) {}

    record MixedValueDto(
            @ExportColumn(header = "时间戳", order = 1)
            Instant instant,
            @ExportColumn(header = "金额", order = 2, format = "#,##0.000")
            BigDecimal amount,
            @ExportColumn(header = "对象", order = 3)
            CustomValue value
    ) {}

    record OrderedDto(
            @ExportColumn(header = "第二列", order = 2)
            String second,
            @ExportColumn(header = "第一列", order = 1, width = 5000)
            String first
    ) {}

    record CustomValue(String value) {
        @Override
        public String toString() {
            return value;
        }
    }

    record ThrowingGetterDto(
            @ExportColumn(header = "隐藏", order = 1)
            String hidden
    ) {
        @Override
        public String hidden() {
            throw new IllegalStateException("boom");
        }
    }

    record NoExportDto(String name) {}
}
