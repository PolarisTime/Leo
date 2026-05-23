package com.leo.erp.system.printtemplate.service;

import java.util.List;

/**
 * JSON-structured print template definition.
 * Supports CLODOP overlay printing and iText PDF generation.
 */
public record JsonPrintTemplate(
        double pageWidth,       // mm
        double pageHeight,      // mm
        List<TextField> fields,
        List<TableBlock> tables
) {
    public record TextField(
            String key,
            double x, double y,
            Integer fontSize,
            Boolean bold,
            String format    // "date" | "money" | null = plain text
    ) {}

    public record TableBlock(
            String key,         // data array key, e.g. "items"
            double x, double y,
            double rowHeight,
            List<TableColumn> columns
    ) {}

    public record TableColumn(
            String key,
            double width,
            String title
    ) {}
}
