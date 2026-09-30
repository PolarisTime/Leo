package com.leo.erp.common.excel.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.excel.annotation.ImportColumn;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExcelTemplateService {

    private static final Logger log = LoggerFactory.getLogger(ExcelTemplateService.class);
    private static final int MAX_COL_WIDTH = 30 * 256;
    private static final int MIN_COL_WIDTH = 10 * 256;
    /** SXSSF 滑动窗口行数：模板本身只有表头+示例行，窗口只需覆盖模板规模。 */
    private static final int SXSSF_WINDOW_SIZE = 100;

    record TemplateField(String header, boolean required, String example, String regex, int order,
                         String[] enumValues) {
    }

    /**
     * 模板字节按 DTO 类型缓存。
     *
     * <p>模板内容完全由 {@link ImportColumn} 注解决定，运行期不会变化；原先每次下载模板
     * 都要反射解析注解并新建整个工作簿。缓存后稳态零生成，重复下载直接返回字节副本。</p>
     *
     * <p>缓存的是<strong>不可变字节</strong>而非 workbook 对象：{@code Workbook} 可变且
     * 非线程安全，并发下载共享同一实例会互相污染。返回 {@code clone()} 是为了让调用方
     * 拿到独立数组，不会改坏缓存。</p>
     */
    private final ConcurrentHashMap<Class<?>, byte[]> templateCache = new ConcurrentHashMap<>();

    public byte[] generateTemplate(Class<?> dtoClass) {
        byte[] cached = templateCache.get(dtoClass);
        if (cached != null) {
            return cached.clone();
        }
        byte[] generated = buildTemplate(dtoClass);
        // 并发首次生成时以先到者为准，输家直接用赢家的结果，保证只缓存一份
        byte[] winner = templateCache.putIfAbsent(dtoClass, generated);
        return (winner == null ? generated : winner).clone();
    }

    private byte[] buildTemplate(Class<?> dtoClass) {
        List<TemplateField> fields = resolveFields(dtoClass);
        if (fields.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板 DTO 缺少 @ImportColumn 注解");
        }

        // SXSSF 流式写出：即使模板行数增长，也不会把整簿驻留堆；
        // try-with-resources 关闭时 POI 5.5 会自动 dispose 临时文件
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(SXSSF_WINDOW_SIZE);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet dataSheet = workbook.createSheet("数据模板");
            buildDataSheet(workbook, dataSheet, fields);

            Sheet helpSheet = workbook.createSheet("填写说明");
            buildHelpSheet(workbook, helpSheet, fields);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成导入模板失败", e);
        }
    }

    private void buildDataSheet(Workbook workbook, Sheet sheet, List<TemplateField> fields) {
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        // Header row
        int rowIdx = 0;
        Row headerRow = sheet.createRow(rowIdx++);
        for (int i = 0; i < fields.size(); i++) {
            TemplateField field = fields.get(i);
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(field.header());
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, computeColumnWidth(field.header()));
        }

        // Example row
        Row exampleRow = sheet.createRow(rowIdx);
        for (int i = 0; i < fields.size(); i++) {
            TemplateField field = fields.get(i);
            Cell cell = exampleRow.createCell(i);
            String example = field.example();
            if (example != null && !example.isBlank()) {
                cell.setCellValue(example);
            }
        }
    }

    private void buildHelpSheet(Workbook workbook, Sheet sheet, List<TemplateField> fields) {
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        CellStyle wrapStyle = workbook.createCellStyle();
        wrapStyle.setWrapText(true);

        String[] helpHeaders = {"字段名", "必填", "格式要求", "示例值"};
        int[] helpWidths = {5120, 2560, 7680, 5120};

        int rowIdx = 0;
        Row hdrRow = sheet.createRow(rowIdx++);
        for (int i = 0; i < helpHeaders.length; i++) {
            Cell cell = hdrRow.createCell(i);
            cell.setCellValue(helpHeaders[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, helpWidths[i]);
        }

        for (TemplateField field : fields) {
            Row row = sheet.createRow(rowIdx++);
            Cell nameCell = row.createCell(0);
            nameCell.setCellValue(field.header());
            nameCell.setCellStyle(wrapStyle);

            Cell requiredCell = row.createCell(1);
            requiredCell.setCellValue(field.required() ? "是" : "否");

            Cell formatCell = row.createCell(2);
            formatCell.setCellValue(buildFormatDescription(field));
            formatCell.setCellStyle(wrapStyle);

            Cell exampleCell = row.createCell(3);
            exampleCell.setCellValue(field.example());
            exampleCell.setCellStyle(wrapStyle);
        }
    }

    private String buildFormatDescription(TemplateField field) {
        StringBuilder sb = new StringBuilder();
        if (field.required()) {
            sb.append("必填");
        }
        if (!field.regex().isBlank()) {
            if (!sb.isEmpty()) sb.append("；");
            sb.append("需符合规则：").append(field.regex());
        }
        if (field.enumValues().length > 0) {
            if (!sb.isEmpty()) sb.append("；");
            sb.append("可选：").append(String.join("、", field.enumValues()));
        }
        return sb.isEmpty() ? "无限制" : sb.toString();
    }

    private int computeColumnWidth(String header) {
        int w = (header.length() + 2) * 256;
        return Math.max(MIN_COL_WIDTH, Math.min(w, MAX_COL_WIDTH));
    }

    List<TemplateField> resolveFields(Class<?> dtoClass) {
        RecordComponent[] components = dtoClass.getRecordComponents();
        List<TemplateField> fields = new ArrayList<>();
        for (RecordComponent component : components) {
            ImportColumn annotation = component.getAnnotation(ImportColumn.class);
            if (annotation != null) {
                fields.add(new TemplateField(annotation.header(), annotation.required(), annotation.example(),
                        annotation.regex(), annotation.order(), annotation.enumValues()));
            }
        }
        fields.sort(Comparator.comparingInt(TemplateField::order));
        return fields;
    }
}
