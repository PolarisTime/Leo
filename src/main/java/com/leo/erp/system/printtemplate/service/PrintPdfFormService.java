package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.properties.TextAlignment;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PrintPdfFormService {

    private static final Pattern TEMPLATE_PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");
    private static final float DEFAULT_PAGE_WIDTH = 595f;
    private static final float DEFAULT_PAGE_HEIGHT = 842f;
    private static final List<String> SIMSUN_FONT_CANDIDATES = List.of(
            "/usr/share/fonts/truetype/windows/simsun.ttc",
            "/usr/share/fonts/truetype/windows/simsun.ttf",
            "/usr/share/fonts/truetype/msttcorefonts/simsun.ttc",
            "/usr/share/fonts/truetype/msttcorefonts/simsun.ttf",
            "/usr/share/fonts/simsun.ttc",
            "/usr/share/fonts/simsun.ttf",
            "/System/Library/Fonts/Supplemental/Songti.ttc",
            "/Library/Fonts/Songti.ttc",
            "C:/Windows/Fonts/simsun.ttc",
            "C:/Windows/Fonts/simsun.ttf"
    );
    private static final List<String> CHINESE_FONT_FALLBACKS = List.of(
            "/usr/share/fonts/google-droid-sans-fonts/DroidSansFallbackFull.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/wenquanyi/wqy-microhei/wqy-microhei.ttc"
    );
    private final PrintScriptService printScriptService;
    private final PrintPdfFormTemplateValidator templateValidator;

    public PrintPdfFormService(PrintScriptService printScriptService, PrintPdfFormTemplateValidator templateValidator) {
        this.printScriptService = printScriptService;
        this.templateValidator = templateValidator;
    }

    public byte[] generateFromRecord(String templateId, String moduleKey, Long recordId) {
        Map<String, Object> payload = printScriptService.generateFromRecord(templateId, moduleKey, recordId);
        return generateFromPayload(payload);
    }

    public byte[] generateFromPayload(Map<String, Object> payload) {
        String templateType = String.valueOf(payload.getOrDefault("templateType", ""));
        if (!"PDF_FORM".equals(templateType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前模板不是 PDF_FORM 类型");
        }
        PdfFormTemplateConfig config = parseTemplateConfig(String.valueOf(payload.getOrDefault("templateHtml", "")));
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) payload.getOrDefault("data", Collections.emptyMap());
        @SuppressWarnings("unchecked")
        List<Map<String, String>> items = (List<Map<String, String>>) payload.getOrDefault(
                "items",
                Collections.emptyList()
        );
        return fillPdfForm(config, data, items);
    }

    private PdfFormTemplateConfig parseTemplateConfig(String templateHtml) {
        return new PdfFormTemplateConfig(templateValidator.validate(templateHtml));
    }

    private byte[] fillPdfForm(PdfFormTemplateConfig config, Map<String, String> data, List<Map<String, String>> items) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(out))) {
            PdfFont font = createChineseFont();
            drawContent(pdf, font, config.root(), data, items);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生成 PDF 表单失败");
        }
        return out.toByteArray();
    }

    private void drawFields(
            PdfCanvas canvas,
            JsonNode fieldsConfig,
            Map<String, String> data,
            PdfFont font,
            PageMetrics pageMetrics
    ) {
        if (!fieldsConfig.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> configuredFields = fieldsConfig.fields();
        while (configuredFields.hasNext()) {
            Map.Entry<String, JsonNode> entry = configuredFields.next();
            String fieldName = entry.getKey();
            JsonNode fieldConfig = entry.getValue();
            String value = resolveValue(data, fieldConfig.path("source"), fieldName);
            value = formatValue(value, text(fieldConfig, "format", ""));
            if (value == null || value.isBlank()) {
                continue;
            }
            Rectangle rectangle = configuredRectangle(fieldConfig, pageMetrics);
            if (rectangle != null) {
                drawText(canvas, rectangle, fieldName, fieldConfig, value, font);
            }
        }
    }

    private void drawContent(
            PdfDocument pdf,
            PdfFont font,
            JsonNode root,
            Map<String, String> data,
            List<Map<String, String>> items
    ) {
        JsonNode tableConfig = root.path("table");
        JsonNode fieldsConfig = root.path("fields");
        if (!tableConfig.isObject() && !fieldsConfig.isObject() && !root.path("static").isArray()) {
            return;
        }

        PdfFont latinFont = createLatinFont(font);
        Totals totals = totals(items);
        Map<String, String> variables = new HashMap<>(data);
        variables.put("totalQuantity", formatQuantity(totals.quantity()));
        variables.put("totalWeight", formatDecimal(totals.weight(), 3));

        PageMetrics pageMetrics = pageMetrics(root.path("page"));
        float tableTop = number(tableConfig, "top", 176f);
        float rowHeight = number(tableConfig, "rowHeight", 26f);
        float headerHeight = number(tableConfig, "headerHeight", 28f);
        int maxRowsPerPage = Math.max(1, integer(tableConfig, "maxRowsPerPage", 16));
        List<JsonNode> columns = childObjects(tableConfig.path("columns"));

        int pageIndex = 0;
        int itemIndex = 0;
        do {
            PdfCanvas canvas = new PdfCanvas(pdf.addNewPage(new PageSize(pageMetrics.width(), pageMetrics.height())));
            drawStatic(canvas, font, root.path("static"), pageMetrics);
            drawFields(canvas, fieldsConfig, data, font, pageMetrics);
            int rowsOnPage = 0;
            if (tableConfig.isObject()) {
                rowsOnPage = Math.min(items.size() - itemIndex, maxRowsPerPage);
                boolean lastPage = itemIndex + rowsOnPage >= items.size();
                drawTableHeader(canvas, font, tableConfig, columns, pageMetrics);
                for (int row = 0; row < rowsOnPage; row++) {
                    drawItemRow(
                            canvas,
                            font,
                            latinFont,
                            tableConfig,
                            columns,
                            tableTop + headerHeight + row * rowHeight,
                            items.get(itemIndex + row),
                            pageMetrics
                    );
                }
                float nextTop = tableTop + headerHeight + rowsOnPage * rowHeight;
                if (items.isEmpty()) {
                    drawNoContentRow(canvas, font, tableConfig, nextTop, pageMetrics);
                    nextTop += rowHeight;
                }
                if (lastPage) {
                    nextTop = drawSummary(canvas, font, root.path("summary"), tableConfig, variables, nextTop, pageMetrics);
                    drawClauses(canvas, font, root.path("clauses"), tableConfig, nextTop, pageMetrics);
                }
            }
            canvas.release();
            itemIndex += rowsOnPage;
            pageIndex++;
        } while (tableConfig.isObject() && itemIndex < items.size());
    }

    private void drawStatic(PdfCanvas canvas, PdfFont font, JsonNode staticConfig, PageMetrics pageMetrics) {
        for (JsonNode element : childObjects(staticConfig)) {
            String type = text(element, "type", "text").trim().toLowerCase();
            if ("rect".equals(type)) {
                drawRect(
                        canvas,
                        number(element, "left", 0f),
                        number(element, "top", 0f),
                        number(element, "width", 1f),
                        number(element, "height", 1f),
                        element,
                        pageMetrics
                );
                continue;
            }
            if ("line".equals(type)) {
                drawLine(canvas, element, pageMetrics);
                continue;
            }
            if ("text".equals(type)) {
                drawCanvasText(
                        canvas,
                        font,
                        text(element, "text", ""),
                        number(element, "left", 0f),
                        number(element, "top", 0f),
                        number(element, "width", 100f),
                        number(element, "height", number(element, "fontSize", 9f) + 4f),
                        number(element, "fontSize", 9f),
                        alignment(text(element, "align", "left")),
                        color(element, "color", ColorConstants.BLACK),
                        pageMetrics
                );
            }
        }
    }

    private void drawTableHeader(PdfCanvas canvas, PdfFont font, JsonNode tableConfig, List<JsonNode> columns, PageMetrics pageMetrics) {
        float left = number(tableConfig, "left", 28f);
        float top = number(tableConfig, "top", 176f);
        float headerHeight = number(tableConfig, "headerHeight", 28f);
        Color headerFillColor = color(tableConfig, "headerFillColor", null);
        Color borderColor = color(tableConfig, "borderColor", ColorConstants.BLACK);
        Color textColor = color(tableConfig, "headerTextColor", color(tableConfig, "textColor", ColorConstants.BLACK));
        float lineWidth = number(tableConfig, "lineWidth", 1f);
        for (JsonNode column : columns) {
            float width = number(column, "width", 60f);
            drawRect(
                    canvas,
                    left,
                    top,
                    width,
                    headerHeight,
                    headerFillColor,
                    color(column, "borderColor", borderColor),
                    number(column, "lineWidth", lineWidth),
                    pageMetrics
            );
            drawCanvasText(
                    canvas,
                    font,
                    text(column, "label", ""),
                    left + 2,
                    top + 6,
                    width - 4,
                    16,
                    number(column, "headerFontSize", 9f),
                    TextAlignment.CENTER,
                    color(column, "headerTextColor", textColor),
                    pageMetrics
            );
            left += width;
        }
    }

    private void drawItemRow(
            PdfCanvas canvas,
            PdfFont font,
            PdfFont latinFont,
            JsonNode tableConfig,
            List<JsonNode> columns,
            float top,
            Map<String, String> item,
            PageMetrics pageMetrics
    ) {
        float left = number(tableConfig, "left", 28f);
        float rowHeight = number(tableConfig, "rowHeight", 26f);
        Color borderColor = color(tableConfig, "borderColor", ColorConstants.BLACK);
        Color textColor = color(tableConfig, "textColor", ColorConstants.BLACK);
        float lineWidth = number(tableConfig, "lineWidth", 1f);
        for (JsonNode column : columns) {
            float width = number(column, "width", 60f);
            String value = itemValue(item, column);
            PdfFont cellFont = "latinIfAscii".equals(text(column, "font", "")) && isAsciiText(value) ? latinFont : font;
            drawRect(
                    canvas,
                    left,
                    top,
                    width,
                    rowHeight,
                    null,
                    color(column, "borderColor", borderColor),
                    number(column, "lineWidth", lineWidth),
                    pageMetrics
            );
            drawCanvasText(
                    canvas,
                    cellFont,
                    value,
                    left + 2,
                    top + 7,
                    width - 4,
                    12,
                    number(column, "fontSize", 8f),
                    alignment(text(column, "align", "center")),
                    color(column, "textColor", textColor),
                    pageMetrics
            );
            left += width;
        }
    }

    private void drawNoContentRow(PdfCanvas canvas, PdfFont font, JsonNode tableConfig, float top, PageMetrics pageMetrics) {
        float left = number(tableConfig, "left", 28f);
        float width = tableWidth(tableConfig);
        float rowHeight = number(tableConfig, "rowHeight", 26f);
        drawRect(
                canvas,
                left,
                top,
                width,
                rowHeight,
                color(tableConfig, "emptyFillColor", null),
                color(tableConfig, "borderColor", ColorConstants.BLACK),
                number(tableConfig, "lineWidth", 1f),
                pageMetrics
        );
        drawCanvasText(
                canvas,
                font,
                text(tableConfig, "emptyText", "----------------以下无内容----------------"),
                left,
                top + 7,
                width,
                12,
                number(tableConfig, "emptyFontSize", 8f),
                TextAlignment.CENTER,
                color(tableConfig, "emptyTextColor", color(tableConfig, "textColor", ColorConstants.BLACK)),
                pageMetrics
        );
    }

    private float drawSummary(
            PdfCanvas canvas,
            PdfFont font,
            JsonNode summaryConfig,
            JsonNode tableConfig,
            Map<String, String> variables,
            float top,
            PageMetrics pageMetrics
    ) {
        if (!summaryConfig.isObject()) {
            return top;
        }
        float left = number(tableConfig, "left", 28f);
        float width = tableWidth(tableConfig);
        float height = number(summaryConfig, "height", number(tableConfig, "rowHeight", 26f));
        if (bool(summaryConfig, "border", true)) {
            drawRect(
                    canvas,
                    left,
                    top,
                    width,
                    height,
                    color(summaryConfig, "fillColor", null),
                    color(summaryConfig, "borderColor", color(tableConfig, "borderColor", ColorConstants.BLACK)),
                    number(summaryConfig, "lineWidth", number(tableConfig, "lineWidth", 1f)),
                    pageMetrics
            );
        }
        drawCanvasText(
                canvas,
                font,
                applyTemplate(text(summaryConfig, "template", ""), variables),
                left + number(summaryConfig, "paddingLeft", 6f),
                top + number(summaryConfig, "paddingTop", 7f),
                width - number(summaryConfig, "paddingLeft", 6f) * 2,
                12,
                number(summaryConfig, "fontSize", 8.5f),
                alignment(text(summaryConfig, "align", "left")),
                color(summaryConfig, "color", color(summaryConfig, "textColor", ColorConstants.BLACK)),
                pageMetrics
        );
        return top + height;
    }

    private void drawClauses(PdfCanvas canvas, PdfFont font, JsonNode clausesConfig, JsonNode tableConfig, float top, PageMetrics pageMetrics) {
        if (!clausesConfig.isObject()) {
            return;
        }
        List<String> paragraphs = childTextValues(clausesConfig.path("lines"));
        if (paragraphs.isEmpty()) {
            return;
        }
        float left = number(clausesConfig, "left", number(tableConfig, "left", 28f));
        float width = number(clausesConfig, "width", tableWidth(tableConfig));
        drawParagraphs(
                canvas,
                font,
                paragraphs,
                left,
                top + number(clausesConfig, "paddingTop", 8f),
                width,
                number(clausesConfig, "height", 96f),
                number(clausesConfig, "fontSize", 7.8f),
                number(clausesConfig, "lineHeight", 1.28f),
                color(clausesConfig, "color", color(clausesConfig, "textColor", ColorConstants.BLACK)),
                pageMetrics
        );
    }

    private Rectangle configuredRectangle(JsonNode fieldConfig, PageMetrics pageMetrics) {
        if (!fieldConfig.has("left") || !fieldConfig.has("top") || !fieldConfig.has("width") || !fieldConfig.has("height")) {
            return null;
        }
        float left = number(fieldConfig, "left", 0f);
        float top = number(fieldConfig, "top", 0f);
        float width = number(fieldConfig, "width", 1f);
        float height = number(fieldConfig, "height", 1f);
        return new Rectangle(left, topY(top + height, pageMetrics), width, height);
    }

    private void drawText(
            PdfCanvas canvas,
            Rectangle fieldRectangle,
            String fieldName,
            JsonNode fieldConfig,
            String text,
            PdfFont font
    ) {
        TextStyle style = textStyle(fieldName, fieldConfig);
        Rectangle textRectangle = shrink(fieldRectangle, style.horizontalPadding(), style.verticalPadding());
        if (style.multiline()) {
            drawWrappedText(canvas, textRectangle, text, font, style);
            return;
        }
        float fontSize = fitFontSize(font, text, textRectangle.getWidth(), style.fontSize(), style.minimumFontSize());
        float baselineY = baselineY(textRectangle, fontSize, style.verticalPosition());
        float textX = textX(font, text, fontSize, textRectangle, style.alignment());

        canvas.saveState()
                .setFillColor(style.color())
                .beginText()
                .setFontAndSize(font, fontSize)
                .moveText(textX, baselineY)
                .showText(text)
                .endText()
                .restoreState();
    }

    private void drawWrappedText(PdfCanvas canvas, Rectangle rectangle, String text, PdfFont font, TextStyle style) {
        float fontSize = fitMultilineFontSize(font, text, rectangle, style);
        float lineHeight = fontSize * style.lineHeightMultiplier();
        List<String> lines = limitLines(
                wrapLines(font, text, fontSize, rectangle.getWidth()),
                font,
                fontSize,
                rectangle.getWidth(),
                maxLineCount(rectangle, fontSize, style.lineHeightMultiplier())
        );
        if (lines.isEmpty()) {
            return;
        }
        float firstBaselineY = firstBaselineY(rectangle, fontSize, lineHeight, lines.size(), style.verticalPosition());

        canvas.saveState()
                .setFillColor(style.color())
                .beginText()
                .setFontAndSize(font, fontSize);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            canvas.setTextMatrix(textX(font, line, fontSize, rectangle, style.alignment()), firstBaselineY - i * lineHeight)
                    .showText(line);
        }
        canvas.endText()
                .restoreState();
    }

    private Rectangle shrink(Rectangle rectangle, float horizontalPadding, float verticalPadding) {
        return new Rectangle(
                rectangle.getX() + horizontalPadding,
                rectangle.getY() + verticalPadding,
                Math.max(1, rectangle.getWidth() - horizontalPadding * 2),
                Math.max(1, rectangle.getHeight() - verticalPadding * 2)
        );
    }

    private float baselineY(Rectangle rectangle, float fontSize, VerticalPosition verticalPosition) {
        float descent = fontSize * 0.25f;
        if (verticalPosition == VerticalPosition.TOP) {
            return rectangle.getTop() - fontSize;
        }
        return rectangle.getY() + (rectangle.getHeight() - fontSize) / 2 + descent;
    }

    private float firstBaselineY(
            Rectangle rectangle,
            float fontSize,
            float lineHeight,
            int lineCount,
            VerticalPosition verticalPosition
    ) {
        if (verticalPosition == VerticalPosition.TOP) {
            return rectangle.getTop() - fontSize;
        }
        float textHeight = lineHeight * lineCount;
        return rectangle.getY() + (rectangle.getHeight() + textHeight) / 2 - fontSize;
    }

    private float textX(PdfFont font, String text, float fontSize, Rectangle rectangle, TextAlignment alignment) {
        if (alignment == TextAlignment.LEFT) {
            return rectangle.getX();
        }
        float width = font.getWidth(text, fontSize);
        if (alignment == TextAlignment.RIGHT) {
            return rectangle.getRight() - width;
        }
        return rectangle.getX() + (rectangle.getWidth() - width) / 2;
    }

    private float fitFontSize(PdfFont font, String text, float maxWidth, float fontSize, float minimumFontSize) {
        if (text.isBlank() || maxWidth <= 0) {
            return fontSize;
        }
        float actualWidth = font.getWidth(text, fontSize);
        if (actualWidth <= maxWidth) {
            return fontSize;
        }
        return Math.max(minimumFontSize, fontSize * maxWidth / actualWidth);
    }

    private float fitMultilineFontSize(PdfFont font, String text, Rectangle rectangle, TextStyle style) {
        float fontSize = style.fontSize();
        while (fontSize > style.minimumFontSize()) {
            List<String> lines = wrapLines(font, text, fontSize, rectangle.getWidth());
            if (lines.size() <= maxLineCount(rectangle, fontSize, style.lineHeightMultiplier())) {
                return fontSize;
            }
            fontSize -= 0.5f;
        }
        return style.minimumFontSize();
    }

    private int maxLineCount(Rectangle rectangle, float fontSize, float lineHeightMultiplier) {
        return Math.max(1, (int) Math.floor(rectangle.getHeight() / (fontSize * lineHeightMultiplier)));
    }

    private List<String> wrapLines(PdfFont font, String text, float fontSize, float maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R", -1)) {
            StringBuilder line = new StringBuilder();
            for (int offset = 0; offset < paragraph.length(); ) {
                int codePoint = paragraph.codePointAt(offset);
                String next = new String(Character.toChars(codePoint));
                if (!line.isEmpty() && font.getWidth(line + next, fontSize) > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(next);
                offset += Character.charCount(codePoint);
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
        }
        return lines;
    }

    private List<String> limitLines(
            List<String> lines,
            PdfFont font,
            float fontSize,
            float maxWidth,
            int maxLineCount
    ) {
        if (lines.size() <= maxLineCount) {
            return lines;
        }
        List<String> visibleLines = new ArrayList<>(lines.subList(0, maxLineCount));
        StringBuilder lastLine = new StringBuilder();
        for (int i = maxLineCount - 1; i < lines.size(); i++) {
            lastLine.append(lines.get(i));
        }
        visibleLines.set(maxLineCount - 1, truncateToWidth(font, lastLine.toString(), fontSize, maxWidth));
        return visibleLines;
    }

    private String truncateToWidth(PdfFont font, String text, float fontSize, float maxWidth) {
        if (font.getWidth(text, fontSize) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            String next = new String(Character.toChars(codePoint));
            if (font.getWidth(result + next + suffix, fontSize) > maxWidth) {
                break;
            }
            result.append(next);
            offset += Character.charCount(codePoint);
        }
        return result + suffix;
    }

    private TextStyle textStyle(String fieldName, JsonNode fieldConfig) {
        boolean defaultMultiline = "projectName".equals(fieldName)
                || "customerName".equals(fieldName)
                || "projectAddress".equals(fieldName);
        return new TextStyle(
                number(fieldConfig, "fontSize", 9f),
                number(fieldConfig, "minimumFontSize", 7f),
                number(fieldConfig, "horizontalPadding", 2f),
                number(fieldConfig, "verticalPadding", defaultMultiline ? 3f : 2f),
                alignment(text(fieldConfig, "align", "left")),
                verticalPosition(text(fieldConfig, "vertical", defaultMultiline ? "top" : "middle")),
                bool(fieldConfig, "multiline", defaultMultiline),
                number(fieldConfig, "lineHeight", 1.2f),
                color(fieldConfig, "color", ColorConstants.BLACK)
        );
    }

    private void drawRect(PdfCanvas canvas, float left, float top, float width, float height, JsonNode config, PageMetrics pageMetrics) {
        drawRect(
                canvas,
                left,
                top,
                width,
                height,
                color(config, "fillColor", null),
                color(config, "strokeColor", ColorConstants.BLACK),
                number(config, "lineWidth", 1f),
                pageMetrics
        );
    }

    private void drawRect(
            PdfCanvas canvas,
            float left,
            float top,
            float width,
            float height,
            Color fillColor,
            Color strokeColor,
            float lineWidth,
            PageMetrics pageMetrics
    ) {
        canvas.saveState();
        canvas.setLineWidth(Math.max(0.1f, lineWidth));
        canvas.setStrokeColor(strokeColor);
        canvas.rectangle(left, topY(top + height, pageMetrics), width, height);
        if (fillColor != null) {
            canvas.setFillColor(fillColor).fillStroke();
        } else {
            canvas.stroke();
        }
        canvas.restoreState();
    }

    private void drawLine(PdfCanvas canvas, JsonNode line, PageMetrics pageMetrics) {
        canvas.saveState()
                .setLineWidth(Math.max(0.1f, number(line, "lineWidth", 1f)))
                .setStrokeColor(color(line, "strokeColor", ColorConstants.BLACK))
                .moveTo(number(line, "x1", 0f), topY(number(line, "y1", 0f), pageMetrics))
                .lineTo(number(line, "x2", 0f), topY(number(line, "y2", 0f), pageMetrics))
                .stroke()
                .restoreState();
    }

    private void drawCanvasText(
            PdfCanvas canvas,
            PdfFont font,
            String text,
            float left,
            float top,
            float width,
            float height,
            float fontSize,
            TextAlignment alignment,
            Color textColor,
            PageMetrics pageMetrics
    ) {
        String value = text == null ? "" : text;
        float maxWidth = Math.max(1, width - 2);
        float effectiveFontSize = fitFontSize(font, value, maxWidth, fontSize, 6.5f);
        float textWidth = font.getWidth(value, effectiveFontSize);
        float x = switch (alignment) {
            case CENTER -> left + Math.max(0, (width - textWidth) / 2);
            case RIGHT -> left + width - textWidth;
            default -> left + 1;
        };
        float y = topY(top, pageMetrics) - height / 2 - effectiveFontSize * 0.32f;
        canvas.saveState()
                .setFillColor(textColor)
                .beginText()
                .setFontAndSize(font, effectiveFontSize)
                .moveText(x, y)
                .showText(value)
                .endText()
                .restoreState();
    }

    private void drawParagraphs(
            PdfCanvas canvas,
            PdfFont font,
            List<String> paragraphs,
            float left,
            float top,
            float width,
            float height,
            float fontSize,
            float lineHeightMultiplier,
            Color textColor,
            PageMetrics pageMetrics
    ) {
        float y = topY(top, pageMetrics) - fontSize;
        float minY = topY(top + height, pageMetrics) + fontSize;
        float lineHeight = fontSize * lineHeightMultiplier;
        canvas.saveState()
                .setFillColor(textColor)
                .beginText()
                .setFontAndSize(font, fontSize);
        for (String paragraph : paragraphs) {
            StringBuilder line = new StringBuilder();
            for (int offset = 0; offset < paragraph.length();) {
                int codePoint = paragraph.codePointAt(offset);
                String next = new String(Character.toChars(codePoint));
                if (!line.isEmpty() && font.getWidth(line + next, fontSize) > width) {
                    canvas.setTextMatrix(left, y).showText(line.toString());
                    y -= lineHeight;
                    line.setLength(0);
                }
                line.append(next);
                offset += Character.charCount(codePoint);
                if (y < minY) {
                    break;
                }
            }
            if (!line.isEmpty() && y >= minY) {
                canvas.setTextMatrix(left, y).showText(line.toString());
                y -= lineHeight;
            }
            if (y < minY) {
                break;
            }
        }
        canvas.endText().restoreState();
    }

    private float topY(float top, PageMetrics pageMetrics) {
        return pageMetrics.height() - top;
    }

    private float tableWidth(JsonNode tableConfig) {
        if (tableConfig.has("width")) {
            return number(tableConfig, "width", 539f);
        }
        float width = 0f;
        for (JsonNode column : childObjects(tableConfig.path("columns"))) {
            width += number(column, "width", 60f);
        }
        return width;
    }

    private String itemValue(Map<String, String> item, JsonNode column) {
        String value = resolveValue(item, column.path("source"), text(column, "key", ""));
        if ("compactAscii".equals(text(column, "normalize", ""))) {
            return compactAsciiToken(value);
        }
        return value;
    }

    private String resolveValue(Map<String, String> data, JsonNode source, String fallbackKey) {
        if (source.isArray()) {
            for (JsonNode key : source) {
                String value = data.get(key.asText(""));
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return "";
        }
        String key = source.isTextual() ? source.asText() : fallbackKey;
        return data.getOrDefault(key, "");
    }

    private String formatValue(String value, String format) {
        if ("chineseDate".equals(format)) {
            return formatChineseDate(value);
        }
        return value;
    }

    private String formatChineseDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return "";
        }
        String datePart = rawDate.trim().split("[T\\s]", 2)[0];
        if (datePart.contains("年")) {
            return datePart;
        }
        String[] parts = datePart.split("[-/]");
        if (parts.length < 3) {
            return rawDate;
        }
        String month = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
        String day = parts[2].length() == 1 ? "0" + parts[2] : parts[2];
        return parts[0] + "年" + month + "月" + day + "日";
    }

    private Totals totals(List<Map<String, String>> items) {
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        for (Map<String, String> item : items) {
            quantity = quantity.add(decimal(item.get("quantity")));
            weight = weight.add(decimal(item.get("weightTon")));
        }
        return new Totals(quantity, weight);
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String formatDecimal(BigDecimal value, int scale) {
        return value.compareTo(BigDecimal.ZERO) == 0
                ? "0"
                : value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatQuantity(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String applyTemplate(String template, Map<String, String> variables) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Matcher matcher = TEMPLATE_PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            matcher.appendReplacement(result, Matcher.quoteReplacement(templateValue(key, variables.get(key))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String templateValue(String key, String value) {
        if ("remark".equals(key) && (value == null || value.isBlank())) {
            return "无";
        }
        return value == null ? "" : value;
    }

    private String compactAsciiToken(String value) {
        return value == null ? "" : value.replaceAll("(?<=[A-Za-z0-9])\\s+(?=[A-Za-z0-9])", "");
    }

    private boolean isAsciiText(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    private PdfFont createLatinFont(PdfFont fallback) {
        try {
            return PdfFontFactory.createFont(StandardFonts.HELVETICA);
        } catch (IOException ex) {
            return fallback;
        }
    }

    private List<JsonNode> childObjects(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isObject()) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    private List<String> childTextValues(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (!item.asText("").isBlank()) {
                    result.add(item.asText());
                }
            }
        }
        return result;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? fallback : child.asText(fallback);
    }

    private String value(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = text(node, key, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private float number(JsonNode node, String field, float fallback) {
        JsonNode child = node.path(field);
        return child.isNumber() ? (float) child.asDouble() : fallback;
    }

    private int integer(JsonNode node, String field, int fallback) {
        JsonNode child = node.path(field);
        return child.isInt() ? child.asInt() : fallback;
    }

    private boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode child = node.path(field);
        return child.isBoolean() ? child.asBoolean() : fallback;
    }

    private Color color(JsonNode node, String field, Color fallback) {
        String value = text(node, field, "");
        if (value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() != 6) {
            return fallback;
        }
        try {
            int red = Integer.parseInt(normalized.substring(0, 2), 16);
            int green = Integer.parseInt(normalized.substring(2, 4), 16);
            int blue = Integer.parseInt(normalized.substring(4, 6), 16);
            return new DeviceRgb(red, green, blue);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private PageMetrics pageMetrics(JsonNode pageConfig) {
        return new PageMetrics(
                number(pageConfig, "width", DEFAULT_PAGE_WIDTH),
                number(pageConfig, "height", DEFAULT_PAGE_HEIGHT)
        );
    }

    private TextAlignment alignment(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase()) {
            case "center" -> TextAlignment.CENTER;
            case "right" -> TextAlignment.RIGHT;
            default -> TextAlignment.LEFT;
        };
    }

    private VerticalPosition verticalPosition(String value) {
        return "top".equalsIgnoreCase(value) ? VerticalPosition.TOP : VerticalPosition.MIDDLE;
    }

    private PdfFont createChineseFont() throws IOException {
        for (String fontPath : SIMSUN_FONT_CANDIDATES) {
            PdfFont font = createFontFromPath(fontPath);
            if (font != null) {
                return font;
            }
        }
        try {
            return PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");
        } catch (Exception ignored) {
            for (String fontPath : CHINESE_FONT_FALLBACKS) {
                PdfFont font = createFontFromPath(fontPath);
                if (font != null) {
                    return font;
                }
            }
        }
        return PdfFontFactory.createFont();
    }

    private PdfFont createFontFromPath(String fontPath) throws IOException {
        Path path = Path.of(fontPath);
        if (!Files.isReadable(path)) {
            return null;
        }
        String normalizedPath = path.toString();
        try {
            if (normalizedPath.endsWith(".ttc") || normalizedPath.endsWith(".TTC")) {
                normalizedPath = normalizedPath + ",0";
            }
            return PdfFontFactory.createFont(
                    normalizedPath,
                    PdfEncodings.IDENTITY_H,
                    EmbeddingStrategy.PREFER_EMBEDDED
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private String value(Map<String, String> data, String... keys) {
        for (String key : keys) {
            String value = data.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private enum VerticalPosition {
        TOP,
        MIDDLE
    }

    private record TextStyle(
            float fontSize,
            float minimumFontSize,
            float horizontalPadding,
            float verticalPadding,
            TextAlignment alignment,
            VerticalPosition verticalPosition,
            boolean multiline,
            float lineHeightMultiplier,
            Color color
    ) {
    }

    private record Totals(BigDecimal quantity, BigDecimal weight) {
    }

    private record PageMetrics(float width, float height) {
    }

    private record PdfFormTemplateConfig(JsonNode root) {
    }
}
