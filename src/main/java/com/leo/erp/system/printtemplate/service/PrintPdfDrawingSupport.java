package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.properties.TextAlignment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PrintPdfDrawingSupport {

    private static final float DEFAULT_PAGE_WIDTH = 595f;
    private static final float DEFAULT_PAGE_HEIGHT = 842f;

    Rectangle configuredRectangle(JsonNode fieldConfig, PageMetrics pageMetrics) {
        if (!fieldConfig.has("left") || !fieldConfig.has("top") || !fieldConfig.has("width") || !fieldConfig.has("height")) {
            return null;
        }
        float left = number(fieldConfig, "left", 0f);
        float top = number(fieldConfig, "top", 0f);
        float width = number(fieldConfig, "width", 1f);
        float height = number(fieldConfig, "height", 1f);
        return new Rectangle(left, topY(top + height, pageMetrics), width, height);
    }

    void drawText(
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
        float singleLineFontSize = fitFontSize(font, text, rectangle.getWidth(), style.fontSize(), style.minimumFontSize());
        if (font.getWidth(text, singleLineFontSize) <= rectangle.getWidth()) {
            drawTextLine(canvas, rectangle, text, font, style, singleLineFontSize);
            return;
        }
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

    private void drawTextLine(PdfCanvas canvas, Rectangle rectangle, String text, PdfFont font, TextStyle style, float fontSize) {
        canvas.saveState()
                .setFillColor(style.color())
                .beginText()
                .setFontAndSize(font, fontSize)
                .moveText(
                        textX(font, text, fontSize, rectangle, style.alignment()),
                        baselineY(rectangle, fontSize, style.verticalPosition())
                )
                .showText(text)
                .endText()
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

    float fitFontSize(PdfFont font, String text, float maxWidth, float fontSize, float minimumFontSize) {
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

    List<String> wrapLines(PdfFont font, String text, float fontSize, float maxWidth) {
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

    String truncateToWidth(PdfFont font, String text, float fontSize, float maxWidth) {
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

    void drawRect(PdfCanvas canvas, float left, float top, float width, float height, JsonNode config, PageMetrics pageMetrics) {
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

    void drawRect(
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

    void drawLine(PdfCanvas canvas, JsonNode line, PageMetrics pageMetrics) {
        canvas.saveState()
                .setLineWidth(Math.max(0.1f, number(line, "lineWidth", 1f)))
                .setStrokeColor(color(line, "strokeColor", ColorConstants.BLACK))
                .moveTo(number(line, "x1", 0f), topY(number(line, "y1", 0f), pageMetrics))
                .lineTo(number(line, "x2", 0f), topY(number(line, "y2", 0f), pageMetrics))
                .stroke()
                .restoreState();
    }

    void drawCanvasText(
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

    void drawParagraphs(
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

    float topY(float top, PageMetrics pageMetrics) {
        return pageMetrics.height() - top;
    }

    float tableWidth(JsonNode tableConfig) {
        if (tableConfig.has("width")) {
            return number(tableConfig, "width", 539f);
        }
        float width = 0f;
        for (JsonNode column : childObjects(tableConfig.path("columns"))) {
            width += number(column, "width", 60f);
        }
        return width;
    }

    boolean isAsciiText(String value) {
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

    List<JsonNode> childObjects(JsonNode node) {
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

    List<String> childTextValues(JsonNode node) {
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

    String text(JsonNode node, String field, String fallback) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? fallback : child.asText(fallback);
    }

    float number(JsonNode node, String field, float fallback) {
        JsonNode child = node.path(field);
        return child.isNumber() ? (float) child.asDouble() : fallback;
    }

    int integer(JsonNode node, String field, int fallback) {
        JsonNode child = node.path(field);
        return child.isInt() ? child.asInt() : fallback;
    }

    boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode child = node.path(field);
        return child.isBoolean() ? child.asBoolean() : fallback;
    }

    Color color(JsonNode node, String field, Color fallback) {
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

    PageMetrics pageMetrics(JsonNode pageConfig) {
        return new PageMetrics(
                number(pageConfig, "width", DEFAULT_PAGE_WIDTH),
                number(pageConfig, "height", DEFAULT_PAGE_HEIGHT)
        );
    }

    TextAlignment alignment(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase()) {
            case "center" -> TextAlignment.CENTER;
            case "right" -> TextAlignment.RIGHT;
            default -> TextAlignment.LEFT;
        };
    }

    private VerticalPosition verticalPosition(String value) {
        return "top".equalsIgnoreCase(value) ? VerticalPosition.TOP : VerticalPosition.MIDDLE;
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

    record PageMetrics(float width, float height) {
    }
}
