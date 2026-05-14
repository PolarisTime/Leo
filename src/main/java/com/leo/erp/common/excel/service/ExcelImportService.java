package com.leo.erp.common.excel.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.excel.annotation.ImportColumn;
import com.leo.erp.common.excel.config.ExcelProperties;
import com.leo.erp.common.excel.dto.ImportErrorDetail;
import com.leo.erp.common.excel.dto.ImportResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

@Component
public class ExcelImportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelImportService.class);
    private static final Set<String> BOOLEAN_TRUE = Set.of("是", "启用", "1", "true", "y", "yes");
    private static final Set<String> BOOLEAN_FALSE = Set.of("否", "关闭", "0", "false", "n", "no");
    private static final DataFormatter DATA_FORMATTER = new DataFormatter(Locale.getDefault());

    private final ExcelProperties excelProperties;

    public ExcelImportService(ExcelProperties excelProperties) {
        this.excelProperties = excelProperties;
    }

    record FieldMeta(String header, boolean required, String example, String regex, int order,
                     String[] enumValues, RecordComponent component) {
    }

    public <T> ImportResult importFile(MultipartFile file, Class<T> dtoClass, BiConsumer<T, ImportResult> persister)
            throws IOException {
        validateFile(file);

        List<FieldMeta> fields = resolveFields(dtoClass);
        if (fields.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导入 DTO 缺少 @ImportColumn 注解");
        }

        List<List<String>> rows = parseRows(file);

        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导入文件不能为空");
        }

        Map<String, Integer> headerIndexes = buildHeaderMap(rows.get(0), fields);
        List<T> successRows = new ArrayList<>();
        List<ImportErrorDetail> errors = new ArrayList<>();
        int totalRows = 0;

        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            totalRows++;
            int rowNumber = i + 1;

            // Build constructor args — null means validation failure
            Object[] args = new Object[fields.size()];
            boolean hasError = false;

            for (int fi = 0; fi < fields.size(); fi++) {
                FieldMeta field = fields.get(fi);
                String raw = getCellValue(row, headerIndexes, field.header());
                RecordComponent component = field.component();

                // Required check
                if (field.required() && (raw == null || raw.isBlank())) {
                    errors.add(new ImportErrorDetail(rowNumber, field.header(), field.header() + " 不能为空"));
                    hasError = true;
                    continue;
                }

                // Regex check
                if (raw != null && !raw.isBlank() && !field.regex().isBlank()) {
                    if (!raw.trim().matches(field.regex())) {
                        errors.add(new ImportErrorDetail(rowNumber, field.header(), field.header() + " 格式不正确"));
                        hasError = true;
                        continue;
                    }
                }

                // Enum check
                if (raw != null && !raw.isBlank() && field.enumValues().length > 0) {
                    Set<String> allowed = Set.of(field.enumValues());
                    if (!allowed.contains(raw.trim())) {
                        errors.add(new ImportErrorDetail(rowNumber, field.header(),
                                field.header() + " 必须是以下值之一：" + String.join(", ", field.enumValues())));
                        hasError = true;
                        continue;
                    }
                }

                // Convert value
                try {
                    args[fi] = convertValue(raw, component.getType());
                } catch (IllegalArgumentException e) {
                    errors.add(new ImportErrorDetail(rowNumber, field.header(), field.header() + " 格式不正确"));
                    hasError = true;
                }
            }

            if (!hasError) {
                try {
                    T record = instantiateRecord(dtoClass, args);
                    successRows.add(record);
                } catch (ReflectiveOperationException e) {
                    errors.add(new ImportErrorDetail(rowNumber, "", "保存失败，请检查该行数据"));
                }
            }
        }

        ImportResult result = new ImportResult(
                totalRows, successRows.size(), 0, 0, errors.size(),
                errors, new ArrayList<>(successRows)
        );

        if (!successRows.isEmpty()) {
            persister.accept(successRows.get(0), result);
        }

        return result;
    }

    public <T> List<T> parseAndValidate(MultipartFile file, Class<T> dtoClass) throws IOException {
        validateFile(file);

        List<FieldMeta> fields = resolveFields(dtoClass);
        if (fields.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导入 DTO 缺少 @ImportColumn 注解");
        }

        List<List<String>> rows = parseRows(file);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导入文件不能为空");
        }

        Map<String, Integer> headerIndexes = buildHeaderMap(rows.get(0), fields);
        List<T> results = new ArrayList<>();
        List<String> allErrors = new ArrayList<>();

        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            int rowNumber = i + 1;

            Object[] args = new Object[fields.size()];
            boolean hasError = false;
            for (int fi = 0; fi < fields.size(); fi++) {
                FieldMeta field = fields.get(fi);
                String raw = getCellValue(row, headerIndexes, field.header());
                RecordComponent component = field.component();

                if (field.required() && (raw == null || raw.isBlank())) {
                    allErrors.add("第" + rowNumber + "行【" + field.header() + "】不能为空");
                    hasError = true;
                    continue;
                }

                if (raw != null && !raw.isBlank() && !field.regex().isBlank()) {
                    if (!raw.trim().matches(field.regex())) {
                        allErrors.add("第" + rowNumber + "行【" + field.header() + "】格式不正确");
                        hasError = true;
                        continue;
                    }
                }

                if (raw != null && !raw.isBlank() && field.enumValues().length > 0) {
                    if (!Set.of(field.enumValues()).contains(raw.trim())) {
                        allErrors.add("第" + rowNumber + "行【" + field.header() + "】必须是以下值之一：" + String.join(", ", field.enumValues()));
                        hasError = true;
                        continue;
                    }
                }

                try {
                    args[fi] = convertValue(raw, component.getType());
                } catch (IllegalArgumentException e) {
                    allErrors.add("第" + rowNumber + "行【" + field.header() + "】格式不正确");
                    hasError = true;
                }
            }

            if (!hasError) {
                try {
                    results.add(instantiateRecord(dtoClass, args));
                } catch (ReflectiveOperationException e) {
                    allErrors.add("第" + rowNumber + "行保存失败，请检查数据");
                }
            }
        }

        if (!allErrors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "导入数据校验失败:\n" + String.join("\n", allErrors));
        }

        return results;
    }

    // ── File parsing ────────────────────────────────

    private List<List<String>> parseRows(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            return parseCsv(file);
        }
        return parseExcel(file);
    }

    private List<List<String>> parseExcel(MultipartFile file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            workbook.setMissingCellPolicy(Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Excel 文件无工作表");
            }
            // NOTE: SXSSFWorkbook handles large files with a streaming window
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < row.getLastCellNum(); i++) {
                    Cell cell = row.getCell(i);
                    cells.add(cell == null ? null : DATA_FORMATTER.formatCellValue(cell));
                }
                rows.add(cells);
            }
        }
        return rows;
    }

    private List<List<String>> parseCsv(MultipartFile file) throws IOException {
        byte[] raw = file.getBytes();
        String content = decodeAndStripBom(raw, StandardCharsets.UTF_8);
        List<List<String>> rows = parseCsvContent(content);
        if (!rows.isEmpty() && !hasKnownHeaders(rows.get(0))) {
            content = decodeAndStripBom(raw, Charset.forName("GBK"));
            rows = parseCsvContent(content);
        }
        return rows;
    }

    private List<List<String>> parseCsvContent(String content) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder().setRecordSeparator("\r\n").build();
        try (CSVParser parser = CSVParser.parse(content, format)) {
            for (CSVRecord record : parser) {
                rows.add(List.copyOf(Arrays.asList(record.values())));
            }
        }
        return rows;
    }

    private String decodeAndStripBom(byte[] raw, Charset charset) {
        String content = new String(raw, charset);
        if (!content.isEmpty() && content.charAt(0) == '﻿') {
            content = content.substring(1);
        }
        return content;
    }

    private boolean hasKnownHeaders(List<String> headerRow) {
        for (String h : headerRow) {
            String lower = (h == null ? "" : h).trim().toLowerCase(Locale.ROOT).replace(" ", "");
            // If any header looks like a known Chinese/English column name, return true
            if (!lower.isEmpty() && !lower.matches("[a-z_0-9]+")) {
                return true;
            }
        }
        return false;
    }

    // ── Header mapping ──────────────────────────────

    private Map<String, Integer> buildHeaderMap(List<String> headerRow, List<FieldMeta> fields) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String value = headerRow.get(i);
            if (value != null) {
                map.put(normalizeHeader(value), i);
            }
        }
        for (FieldMeta field : fields) {
            String normalized = normalizeHeader(field.header());
            if (!map.containsKey(normalized)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导入模板缺少列：" + field.header());
            }
        }
        return map;
    }

    static String normalizeHeader(String header) {
        return header == null ? "" : header.replace(" ", "").trim().toLowerCase(Locale.ROOT);
    }

    private String getCellValue(List<String> row, Map<String, Integer> indexes, String header) {
        Integer idx = indexes.get(normalizeHeader(header));
        if (idx == null || idx >= row.size()) {
            return null;
        }
        String value = row.get(idx);
        return value == null ? null : value.trim();
    }

    private boolean isBlankRow(List<String> row) {
        return row.stream().allMatch(v -> v == null || v.trim().isEmpty());
    }

    // ── Value conversion ────────────────────────────

    private Object convertValue(String raw, Class<?> type) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (type == String.class) {
            return raw;
        }
        if (type == Integer.class || type == int.class) {
            return Integer.valueOf(raw.trim());
        }
        if (type == Long.class || type == long.class) {
            return Long.valueOf(raw.trim());
        }
        if (type == BigDecimal.class) {
            return new BigDecimal(raw.trim());
        }
        if (type == Boolean.class || type == boolean.class) {
            String lower = raw.trim().toLowerCase(Locale.ROOT);
            if (BOOLEAN_TRUE.contains(lower)) return Boolean.TRUE;
            if (BOOLEAN_FALSE.contains(lower)) return Boolean.FALSE;
            throw new IllegalArgumentException("无法识别的布尔值: " + raw);
        }
        if (type == Double.class || type == double.class) {
            return Double.valueOf(raw.trim());
        }
        return raw;
    }

    // ── Record construction ──────────────────────────

    @SuppressWarnings("unchecked")
    private <T> T instantiateRecord(Class<T> dtoClass, Object[] args)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        RecordComponent[] components = dtoClass.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
        }
        Constructor<T> constructor = dtoClass.getDeclaredConstructor(paramTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    // ── Field resolution ─────────────────────────────

    <T> List<FieldMeta> resolveFields(Class<T> dtoClass) {
        RecordComponent[] components = dtoClass.getRecordComponents();
        List<FieldMeta> metas = new ArrayList<>();
        for (RecordComponent component : components) {
            ImportColumn annotation = component.getAnnotation(ImportColumn.class);
            if (annotation != null) {
                metas.add(new FieldMeta(annotation.header(), annotation.required(), annotation.example(),
                        annotation.regex(), annotation.order(), annotation.enumValues(), component));
            }
        }
        metas.sort(Comparator.comparingInt(FieldMeta::order));
        return metas;
    }

    // ── File validation ──────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件不能为空");
        }
        if (file.getSize() > excelProperties.getMaxFileSize()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "文件大小超过限制: " + (file.getSize() / 1024 / 1024) + "MB (最大 " + (excelProperties.getMaxFileSize() / 1024 / 1024) + "MB)");
        }
        String filename = file.getOriginalFilename();
        if (filename != null) {
            String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (!excelProperties.getAllowedExtensions().contains(ext)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "不支持的文件类型: ." + ext + " (允许: " + String.join(", ", excelProperties.getAllowedExtensions()) + ")");
            }
        }
    }
}
