package com.leo.erp.common.excel.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.excel.config.ExcelProperties;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 通用表格式导出测试：表头/单元格写入、十进制精度保护、日期单元格与导出行数上限。
 */
class ExcelExportServiceTest {

    private ExcelProperties excelProperties;
    private ExcelExportService excelExportService;

    @BeforeEach
    void setUp() {
        excelProperties = new ExcelProperties();
        excelExportService = new ExcelExportService(excelProperties);
    }

    @Test
    void exportTable_shouldWriteHeadersAndRows() throws Exception {
        byte[] content = excelExportService.exportTable(
                "销售订单",
                List.of("订单号", "客户"),
                List.of(List.of("SO-1", "客户甲"), List.of("SO-2", "客户乙")));

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("销售订单");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("订单号");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("SO-1");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("客户乙");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
        }
    }

    @Test
    void exportTable_shouldKeepExactDecimalsNumericAndWriteOthersAsText() throws Exception {
        BigDecimal exact = new BigDecimal("123.45");
        BigDecimal inexact = new BigDecimal("12345678901234.56789");

        byte[] content = excelExportService.exportTable(
                null, List.of("精确", "不精确"), List.of(List.of(exact, inexact)));

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Row row = workbook.getSheetAt(0).getRow(1);
            assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row.getCell(0).getNumericCellValue()).isEqualTo(123.45d);
            // double 无法精确表示时写文本，禁止隐式舍入
            assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("12345678901234.56789");
        }
    }

    @Test
    void exportTable_shouldWriteDateCellsAndSkipNulls() throws Exception {
        byte[] content = excelExportService.exportTable(
                null,
                List.of("日期", "时间", "空值"),
                List.of(Arrays.asList(
                        Timestamp.valueOf(LocalDateTime.of(2026, 9, 9, 0, 0)),
                        Timestamp.valueOf(LocalDateTime.of(2026, 9, 9, 13, 45, 30)),
                        null)));

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Row row = workbook.getSheetAt(0).getRow(1);
            assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row.getCell(0).getCellStyle().getDataFormatString()).isEqualTo("yyyy-MM-dd");
            assertThat(row.getCell(1).getCellStyle().getDataFormatString()).isEqualTo("yyyy-MM-dd HH:mm:ss");
            Cell blank = row.getCell(2);
            assertThat(blank == null || blank.getCellType() == CellType.BLANK).isTrue();
        }
    }

    @Test
    void exportTable_shouldRejectWhenRowCapExceeded() {
        excelProperties.setMaxExportRows(1);
        List<List<Object>> rows = List.of(List.of("a"), List.of("b"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> excelExportService.exportTable(null, List.of("列"), rows));
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(error).hasMessageContaining("超过限制");
    }

    @Test
    void exportTable_shouldRejectEmptyHeaders() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> excelExportService.exportTable(null, new ArrayList<>(), List.of()));
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
