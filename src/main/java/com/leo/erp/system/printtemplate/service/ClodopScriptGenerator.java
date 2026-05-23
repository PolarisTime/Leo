package com.leo.erp.system.printtemplate.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Generate CLODOP overlay print scripts from JSON template + data.
 * "套打" — prints variable data onto pre-printed forms at exact positions.
 */
@Component
public class ClodopScriptGenerator {

    /**
     * Generate LODOP script for overlay printing.
     *
     * @param template  JSON print template with field positions
     * @param data      row-level data (flat fields)
     * @param items     line items (may be empty)
     * @param title     print job title
     */
    public String generate(JsonPrintTemplate template,
                           Map<String, Object> data,
                           List<Map<String, Object>> items,
                           String title) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("LODOP.PRINT_INIT('").append(PrintScriptService.escapeJs(title)).append("');\n");

        // Set page size
        sb.append("LODOP.SET_PRINT_PAGESIZE(1, ")
                .append((int) (template.pageWidth() * 10)).append(", ")
                .append((int) (template.pageHeight() * 10)).append(", \"\");\n");

        // Render text fields at exact positions
        for (JsonPrintTemplate.TextField field : template.fields()) {
            String raw = String.valueOf(data.getOrDefault(field.key(), ""));
            String formatted = formatValue(raw, field.format());
            sb.append("LODOP.ADD_PRINT_TEXT(")
                    .append(mmToDot(field.y())).append(", ")
                    .append(mmToDot(field.x())).append(", ")
                    .append(fieldWidth(field)).append(", ")
                    .append(fontSize(field)).append(", '")
                    .append(PrintScriptService.escapeJs(formatted)).append("');\n");
            if (Boolean.TRUE.equals(field.bold())) {
                sb.append("LODOP.SET_PRINT_STYLEA(0, \"Bold\", 1);\n");
            }
        }

        // Render tables
        if (items != null && !items.isEmpty()) {
            for (JsonPrintTemplate.TableBlock table : template.tables()) {
                sb.append(renderTable(table, items));
            }
        }

        return sb.toString();
    }

    private String renderTable(JsonPrintTemplate.TableBlock table, List<Map<String, Object>> items) {
        StringBuilder sb = new StringBuilder(256);
        int colCount = table.columns().size();

        // Build LODOP table data string
        // Format: "col1标题,col2标题;row1col1,row1col2;row2col1,row2col2"
        StringBuilder dataStr = new StringBuilder();

        // Header row
        for (int i = 0; i < colCount; i++) {
            if (i > 0) dataStr.append(',');
            dataStr.append(PrintScriptService.escapeJs(table.columns().get(i).title()));
        }
        dataStr.append(';');

        // Data rows
        for (Map<String, Object> row : items) {
            for (int i = 0; i < colCount; i++) {
                if (i > 0) dataStr.append(',');
                String raw = String.valueOf(row.getOrDefault(table.columns().get(i).key(), ""));
                dataStr.append(PrintScriptService.escapeJs(raw));
            }
            dataStr.append(';');
        }

        // Column widths (mm × 10)
        StringBuilder widths = new StringBuilder();
        for (int i = 0; i < colCount; i++) {
            if (i > 0) widths.append(',');
            widths.append((int) (table.columns().get(i).width() * 10));
        }

        sb.append("LODOP.ADD_PRINT_TABLE(")
                .append(mmToDot(table.y())).append(", ")
                .append(mmToDot(table.x())).append(", ")
                .append(totalTableWidth(table)).append(", ")
                .append((int) (table.rowHeight() * 10)).append(", '")
                .append(dataStr).append("');\n");

        sb.append("LODOP.SET_PRINT_COLUMNSIZE(0, \"").append(widths).append("\");\n");
        return sb.toString();
    }

    private int mmToDot(double mm) {
        return (int) (mm * 10); // LODOP uses 0.1mm units
    }

    private int fieldWidth(JsonPrintTemplate.TextField f) {
        return 600; // default width, field positions are exact XY
    }

    private int fontSize(JsonPrintTemplate.TextField f) {
        return f.fontSize() != null ? f.fontSize() : 11;
    }

    private int totalTableWidth(JsonPrintTemplate.TableBlock table) {
        return (int) (table.columns().stream().mapToDouble(JsonPrintTemplate.TableColumn::width).sum() * 10);
    }

    private String formatValue(String raw, String format) {
        if (format == null) return raw;
        return switch (format) {
            case "money" -> {
                try {
                    double d = Double.parseDouble(raw);
                    yield String.format("%.2f", d);
                } catch (NumberFormatException e) {
                    yield raw;
                }
            }
            default -> raw;
        };
    }
}
