package com.leo.erp.system.printtemplate.service;

import com.itextpdf.kernel.colors.DeviceGray;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;

import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class PdfPrintGenerator {

    public byte[] generate(JsonPrintTemplate template,
                           Map<String, Object> data,
                           List<Map<String, Object>> items,
                           String title) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(out));
        PageSize page = new PageSize(
                (float) mmToPt(template.pageWidth()),
                (float) mmToPt(template.pageHeight()));
        pdf.setDefaultPageSize(page);
        Document doc = new Document(pdf);

        PdfFont font;
        try {
            font = PdfFontFactory.createFont("Helvetica", "WinAnsiEncoding");
        } catch (Exception e) {
            font = PdfFontFactory.createFont();
        }

        

        if (title != null && !title.isBlank()) {
            doc.add(new Paragraph(title)
                    .setFont(font).setFontSize(16)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10));
        }

        for (JsonPrintTemplate.TextField field : template.fields()) {
            String raw = String.valueOf(data.getOrDefault(field.key(), ""));
            Paragraph p = new Paragraph(formatValue(raw, field.format()))
                    .setFont(font)
                    .setFontSize(field.fontSize() != null ? field.fontSize() : 10)
                    .setFixedPosition(
                            (float) mmToPt(field.x()),
                            (float) (mmToPt(template.pageHeight()) - mmToPt(field.y())),
                            300);
            doc.add(p);
        }

        if (items != null && !items.isEmpty()) {
            for (JsonPrintTemplate.TableBlock block : template.tables()) {
                float[] widths = new float[block.columns().size()];
                for (int i = 0; i < widths.length; i++) {
                    widths[i] = (float) block.columns().get(i).width();
                }
                Table table = new Table(UnitValue.createPercentArray(widths));
                table.setFixedPosition(
                        (float) mmToPt(block.x()),
                        (float) (mmToPt(template.pageHeight()) - mmToPt(block.y())),
                        (float) mmToPt(totalWidth(block)));

                for (JsonPrintTemplate.TableColumn col : block.columns()) {
                    table.addHeaderCell(new Cell().add(new Paragraph(col.title())
                            .setFont(font).setFontSize(9))
                            .setBackgroundColor(DeviceGray.makeLighter(DeviceGray.GRAY)));
                }
                for (Map<String, Object> row : items) {
                    for (JsonPrintTemplate.TableColumn col : block.columns()) {
                        String val = String.valueOf(row.getOrDefault(col.key(), ""));
                        table.addCell(new Cell().add(new Paragraph(val)
                                .setFont(font).setFontSize(9)));
                    }
                }
                doc.add(table);
            }
        }

        doc.close();
        return out.toByteArray();
    }

    private double mmToPt(double mm) { return mm * 2.8346; }

    private double totalWidth(JsonPrintTemplate.TableBlock block) {
        return block.columns().stream().mapToDouble(JsonPrintTemplate.TableColumn::width).sum();
    }

    private String formatValue(String raw, String format) {
        if (format == null) return raw;
        if ("money".equals(format)) {
            try { return String.format("%.2f", Double.parseDouble(raw)); }
            catch (NumberFormatException e) { return raw; }
        }
        return raw;
    }
}
