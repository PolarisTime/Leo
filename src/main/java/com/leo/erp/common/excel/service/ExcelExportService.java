package com.leo.erp.common.excel.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.excel.annotation.ExportColumn;
import com.leo.erp.common.excel.config.ExcelProperties;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelExportService.class);

    private final ExcelProperties excelProperties;

    public ExcelExportService(ExcelProperties excelProperties) {
        this.excelProperties = excelProperties;
    }

    record ColumnMeta(String header, int order, int width, String format, RecordComponent component) {}

    public <T> byte[] export(List<T> rows, Class<T> dtoClass) {
        if (rows.size() > excelProperties.getMaxExportRows()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "导出数据超过限制: " + rows.size() + " 行 (最大 " + excelProperties.getMaxExportRows() + " 行)");
        }

        List<ColumnMeta> columns = resolveColumns(dtoClass);
        if (columns.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导出 DTO 缺少 @ExportColumn 注解");
        }

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Data");
            CreationHelper helper = workbook.getCreationHelper();

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat(excelProperties.getDefaultDateFormat()));

            int rowIdx = 0;
            Row headerRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < columns.size(); i++) {
                ColumnMeta col = columns.get(i);
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(col.header());
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, col.width());
            }

            for (T row : rows) {
                Row dataRow = sheet.createRow(rowIdx++);
                for (int i = 0; i < columns.size(); i++) {
                    ColumnMeta col = columns.get(i);
                    Cell cell = dataRow.createCell(i);
                    Object value = invokeGetter(col.component(), row);
                    setCellValue(cell, value, col.format(), dateStyle, helper);
                }
            }

            workbook.write(baos);
            workbook.dispose();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("导出 XLSX 失败", e);
        }
    }

    private void setCellValue(Cell cell, Object value, String format, CellStyle dateStyle, CreationHelper helper) {
        if (value == null) {
            return;
        }
        if (value instanceof String s) {
            cell.setCellValue(s);
        } else if (value instanceof Number n) {
            if (!format.isBlank()) {
                CellStyle style = cell.getSheet().getWorkbook().createCellStyle();
                style.setDataFormat(helper.createDataFormat().getFormat(format));
                cell.setCellStyle(style);
            }
            cell.setCellValue(n.doubleValue());
        } else if (value instanceof Boolean b) {
            cell.setCellValue(b ? "是" : "否");
        } else if (value instanceof LocalDateTime dt) {
            cell.setCellStyle(dateStyle);
            cell.setCellValue(dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            cell.setCellStyle(dateStyle);
        } else if (value instanceof LocalDate d) {
            cell.setCellValue(d.toString());
        } else if (value instanceof Instant inst) {
            cell.setCellStyle(dateStyle);
            cell.setCellValue(inst.toEpochMilli());
        } else if (value instanceof BigDecimal bd) {
            if (!format.isBlank()) {
                CellStyle style = cell.getSheet().getWorkbook().createCellStyle();
                style.setDataFormat(helper.createDataFormat().getFormat(format));
                cell.setCellStyle(style);
            }
            cell.setCellValue(bd.doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }

    <T> List<ColumnMeta> resolveColumns(Class<T> dtoClass) {
        RecordComponent[] components = dtoClass.getRecordComponents();
        List<ColumnMeta> metas = new ArrayList<>();
        for (RecordComponent component : components) {
            ExportColumn annotation = component.getAnnotation(ExportColumn.class);
            if (annotation != null) {
                metas.add(new ColumnMeta(annotation.header(), annotation.order(), annotation.width(),
                        annotation.format(), component));
            }
        }
        metas.sort(Comparator.comparingInt(ColumnMeta::order));
        return metas;
    }

    private Object invokeGetter(RecordComponent component, Object record) {
        try {
            return component.getAccessor().invoke(record);
        } catch (IllegalAccessException | InvocationTargetException e) {
            log.warn("Failed to invoke getter for {}", component.getName(), e);
            return null;
        }
    }
}
