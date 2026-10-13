package com.leo.erp.common.excel.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.excel.annotation.ExportColumn;
import com.leo.erp.common.excel.config.ExcelProperties;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
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
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelExportService.class);

    /** Excel 日期时间列格式码；中文按字面量加引号转义，与 {@link com.leo.erp.common.excel.config.ExcelProperties} 的日期格式口径一致。 */
    private static final String DATE_TIME_FORMAT = "yyyy\"年\"MM\"月\"dd\"日\" HH:mm:ss";
    private static final int DEFAULT_COLUMN_WIDTH = 5120;

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

    /**
     * 通用表格式导出：由调用方提供表头与已排好序的行数据，返回 XLSX 字节。
     *
     * <p>与 {@link #export(List, Class)} 的差异（后者服务于 {@code @ExportColumn} DTO，行为保持不变）：</p>
     * <ul>
     *     <li>{@link java.math.BigDecimal} 仅在能由 {@code double} 精确表示时才写入数值单元格，
     *         否则写文本，避免 18 位精度的金额/重量在接口层被隐式舍入；</li>
     *     <li>日期/时间列一律写为真正的日期单元格（当天 00:00:00 只保留日期，否则保留到秒），便于 Excel 排序，
     *         并按 {@code X年X月X日}（带时间则 {@code X年X月X日 HH:mm:ss}）的格式码展示。</li>
     * </ul>
     */
    public byte[] exportTable(String sheetName, List<String> headers, List<List<Object>> rows) {
        if (headers == null || headers.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导出表头不能为空");
        }
        if (rows.size() > excelProperties.getMaxExportRows()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "导出数据超过限制: " + rows.size() + " 行 (最大 " + excelProperties.getMaxExportRows() + " 行)");
        }

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName == null || sheetName.isBlank() ? "Data" : sheetName);
            CreationHelper helper = workbook.getCreationHelper();

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat(excelProperties.getDefaultDateFormat()));
            CellStyle dateTimeStyle = workbook.createCellStyle();
            dateTimeStyle.setDataFormat(helper.createDataFormat().getFormat(DATE_TIME_FORMAT));

            int rowIdx = 0;
            Row headerRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, DEFAULT_COLUMN_WIDTH);
            }

            for (List<Object> row : rows) {
                Row dataRow = sheet.createRow(rowIdx++);
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = dataRow.createCell(i);
                    Object value = i < row.size() ? row.get(i) : null;
                    writeCellValue(cell, value, dateStyle, dateTimeStyle);
                }
            }

            workbook.write(baos);
            workbook.dispose();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("导出 XLSX 失败", e);
        }
    }

    private void writeCellValue(Cell cell, Object value, CellStyle dateStyle, CellStyle dateTimeStyle) {
        if (value == null) {
            return;
        }
        if (value instanceof BigDecimal decimal) {
            writeDecimal(cell, decimal);
        } else if (value instanceof BigInteger integer) {
            writeDecimal(cell, new BigDecimal(integer));
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool ? "是" : "否");
        } else if (value instanceof java.sql.Timestamp timestamp) {
            writeDateTime(cell, timestamp.toLocalDateTime(), dateStyle, dateTimeStyle);
        } else if (value instanceof java.sql.Date sqlDate) {
            // 真日期单元格：写 Excel 日期序列号而非 ISO 文本，日期样式才生效且 Excel 可按日期排序
            writeDateTime(cell, sqlDate.toLocalDate().atStartOfDay(), dateStyle, dateTimeStyle);
        } else if (value instanceof LocalDateTime dateTime) {
            writeDateTime(cell, dateTime, dateStyle, dateTimeStyle);
        } else if (value instanceof LocalDate date) {
            writeDateTime(cell, date.atStartOfDay(), dateStyle, dateTimeStyle);
        } else if (value instanceof Instant instant) {
            writeDateTime(cell, LocalDateTime.ofInstant(instant, ZoneId.systemDefault()), dateStyle, dateTimeStyle);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private void writeDecimal(Cell cell, BigDecimal value) {
        if (BigDecimal.valueOf(value.doubleValue()).compareTo(value) == 0) {
            cell.setCellValue(value.doubleValue());
            return;
        }
        // double 无法精确表示时写文本，宁可保留精度也不隐式舍入
        cell.setCellValue(value.toPlainString());
    }

    /**
     * 写入日期/时间单元格。
     *
     * <p>数值必须是 Excel 日期序列号（1900 日期系统），不能用 epoch millis：Excel 与 POI 都会把
     * 单元格数值直接当序列号解释，写 epoch millis 会显示成天文数字年份。序列号同时保证日期列可排序，
     * 配合 {@code X年X月X日} 格式码即得到展示口径。</p>
     */
    private void writeDateTime(Cell cell, LocalDateTime value, CellStyle dateStyle, CellStyle dateTimeStyle) {
        boolean dateOnly = value.toLocalTime().equals(LocalTime.MIDNIGHT);
        cell.setCellStyle(dateOnly ? dateStyle : dateTimeStyle);
        cell.setCellValue(DateUtil.getExcelDate(value));
        cell.setCellStyle(dateOnly ? dateStyle : dateTimeStyle);
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
            // DTO 路径只有日期样式：时间值同样写 Excel 序列号，保留可排序性并让日期样式生效
            writeDateTime(cell, dt, dateStyle, dateStyle);
        } else if (value instanceof LocalDate d) {
            // 真日期单元格：写 Excel 序列号而非 ISO 文本，日期样式才生效且可排序
            writeDateTime(cell, d.atStartOfDay(), dateStyle, dateStyle);
        } else if (value instanceof Instant inst) {
            writeDateTime(cell, LocalDateTime.ofInstant(inst, ZoneId.systemDefault()), dateStyle, dateStyle);
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
