package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.properties.TextAlignment;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PrintPdfFormService {

    private static final String YINGJIE_A4_REMARK_FORM = "YINGJIE_A4_REMARK";
    private static final String YINGJIE_A4_REMARK_PDF = "print-forms/yingjie-a4-remark.pdf";
    private static final int MAX_DETAIL_ROWS = 10;
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
    private static final Set<String> CENTER_ALIGNED_FIELDS = Set.of(
            "totalQuantity",
            "totalWeight"
    );
    private static final Set<String> TOP_ALIGNED_FIELDS = Set.of(
            "remark"
    );
    private static final TypeReference<Map<String, String>> CONFIG_TYPE = new TypeReference<>() {
    };

    private final PrintScriptService printScriptService;
    private final ObjectMapper objectMapper;

    public PrintPdfFormService(PrintScriptService printScriptService, ObjectMapper objectMapper) {
        this.printScriptService = printScriptService;
        this.objectMapper = objectMapper;
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
        if (!YINGJIE_A4_REMARK_FORM.equals(config.form())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的 PDF 表单模板");
        }
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) payload.getOrDefault("data", Collections.emptyMap());
        @SuppressWarnings("unchecked")
        List<Map<String, String>> items = (List<Map<String, String>>) payload.getOrDefault(
                "items",
                Collections.emptyList()
        );
        return fillYingjieA4Remark(config.template(), data, items);
    }

    private PdfFormTemplateConfig parseTemplateConfig(String templateHtml) {
        try {
            Map<String, String> config = objectMapper.readValue(templateHtml, CONFIG_TYPE);
            return new PdfFormTemplateConfig(
                    value(config, "form"),
                    value(config, "template", "templatePath", "pdf")
            );
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PDF 表单模板配置不是合法 JSON");
        }
    }

    private byte[] fillYingjieA4Remark(
            String templatePath,
            Map<String, String> data,
            List<Map<String, String>> items
    ) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String pdfTemplatePath = templatePath.isBlank() ? YINGJIE_A4_REMARK_PDF : templatePath;
        try (InputStream templateStream = new ClassPathResource(pdfTemplatePath).getInputStream();
             PdfDocument pdf = new PdfDocument(new PdfReader(templateStream), new PdfWriter(out))) {
            PdfAcroForm form = PdfAcroForm.getAcroForm(pdf, false);
            if (form == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "PDF 表单底版缺少 AcroForm 字段");
            }
            PdfFont font = createChineseFont();
            drawFields(pdf, form, buildYingjieA4RemarkFields(data, items), font);
            removeFormFields(pdf, form);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生成 PDF 表单失败");
        }
        return out.toByteArray();
    }

    private void drawFields(PdfDocument pdf, PdfAcroForm form, Map<String, String> values, PdfFont font) {
        Map<String, PdfFormField> fields = form.getAllFormFields();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            PdfFormField field = fields.get(entry.getKey());
            if (field == null) {
                continue;
            }
            List<PdfWidgetAnnotation> widgets = field.getWidgets();
            for (PdfWidgetAnnotation widget : widgets) {
                Rectangle rectangle = widget.getRectangle().toRectangle();
                PdfPage page = widget.getPage() == null ? pdf.getFirstPage() : widget.getPage();
                drawText(page, rectangle, entry.getKey(), value, font);
            }
        }
    }

    private void drawText(PdfPage page, Rectangle fieldRectangle, String fieldName, String text, PdfFont font) {
        TextStyle style = textStyle(fieldName);
        Rectangle textRectangle = shrink(fieldRectangle, style.horizontalPadding(), style.verticalPadding());
        if (style.multiline()) {
            drawWrappedText(page, textRectangle, text, font, style);
            return;
        }
        float fontSize = fitFontSize(font, text, textRectangle.getWidth(), style.fontSize(), style.minimumFontSize());
        float baselineY = baselineY(textRectangle, fontSize, style.verticalPosition());
        float textX = textX(font, text, fontSize, textRectangle, style.alignment());

        PdfCanvas canvas = new PdfCanvas(page);
        canvas.saveState()
                .setFillColor(ColorConstants.BLACK)
                .beginText()
                .setFontAndSize(font, fontSize)
                .moveText(textX, baselineY)
                .showText(text)
                .endText()
                .restoreState()
                .release();
    }

    private void drawWrappedText(PdfPage page, Rectangle rectangle, String text, PdfFont font, TextStyle style) {
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

        PdfCanvas canvas = new PdfCanvas(page);
        canvas.saveState()
                .setFillColor(ColorConstants.BLACK)
                .beginText()
                .setFontAndSize(font, fontSize);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            canvas.setTextMatrix(textX(font, line, fontSize, rectangle, style.alignment()), firstBaselineY - i * lineHeight)
                    .showText(line);
        }
        canvas.endText()
                .restoreState()
                .release();
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

    private TextStyle textStyle(String fieldName) {
        if (fieldName.startsWith("item_")) {
            if (fieldName.endsWith("_emptyMarker")) {
                return new TextStyle(8f, 6.5f, 2, 1, TextAlignment.CENTER, VerticalPosition.MIDDLE, false, 1.2f);
            }
            return new TextStyle(8f, 6.5f, 1.5f, 1, TextAlignment.CENTER, VerticalPosition.MIDDLE, false, 1.2f);
        }
        if (CENTER_ALIGNED_FIELDS.contains(fieldName)) {
            return new TextStyle(8.5f, 6.5f, 2, 1, TextAlignment.CENTER, VerticalPosition.MIDDLE, false, 1.2f);
        }
        if (TOP_ALIGNED_FIELDS.contains(fieldName)) {
            return new TextStyle(8.5f, 6.5f, 2, 1, TextAlignment.LEFT, VerticalPosition.TOP, true, 1.25f);
        }
        if ("projectName".equals(fieldName) || "customerName".equals(fieldName) || "projectAddress".equals(fieldName)) {
            return new TextStyle(9f, 7f, 2, 3, TextAlignment.LEFT, VerticalPosition.MIDDLE, true, 1.2f);
        }
        if ("totalSummary".equals(fieldName)) {
            return new TextStyle(9f, 7f, 2, 1, TextAlignment.LEFT, VerticalPosition.MIDDLE, false, 1.2f);
        }
        return new TextStyle(9f, 7f, 2, 2, TextAlignment.LEFT, VerticalPosition.MIDDLE, false, 1.2f);
    }

    private void removeFormFields(PdfDocument pdf, PdfAcroForm form) {
        List<String> fieldNames = new ArrayList<>(form.getAllFormFields().keySet());
        for (String fieldName : fieldNames) {
            form.removeField(fieldName);
        }
        pdf.getCatalog().remove(PdfName.AcroForm);
    }

    private Map<String, String> buildYingjieA4RemarkFields(
            Map<String, String> data,
            List<Map<String, String>> items
    ) {
        Map<String, String> values = new HashMap<>();
        values.put("remark", value(data, "remark"));
        values.put("customerName", value(data, "customerName"));
        values.put("billNo", resolveBillNo(data));
        values.put("projectName", value(data, "projectName"));
        values.put("billDate", resolveBillDate(data));
        values.put("projectAddress", value(data, "projectAddress"));
        values.put("vehiclePlate", value(data, "vehiclePlate"));

        Totals totals = new Totals();
        int rowCount = Math.min(items.size(), MAX_DETAIL_ROWS);
        for (int i = 0; i < MAX_DETAIL_ROWS; i++) {
            if (i < rowCount) {
                putItemFields(values, i, items.get(i), totals);
            } else if (i == rowCount) {
                values.put(itemField(i, "emptyMarker"), "----------------以下无内容----------------");
            }
        }
        String totalQuantity = formatTotalQuantity(totals.quantity);
        String totalWeight = formatTotalWeight(totals.weight);
        values.put("totalQuantity", totalQuantity);
        values.put("totalWeight", totalWeight);
        values.put("totalSummary", totalSummary(totalQuantity, totalWeight));
        return values;
    }

    private void putItemFields(Map<String, String> values, int rowIndex, Map<String, String> item, Totals totals) {
        String quantity = value(item, "quantity");
        String weight = displayWeight(item);
        totals.quantity = totals.quantity.add(decimal(quantity));
        totals.weight = totals.weight.add(decimal(weight));

        values.put(itemField(rowIndex, "brand"), value(item, "brand"));
        values.put(itemField(rowIndex, "category"), value(item, "category"));
        values.put(itemField(rowIndex, "material"), value(item, "material"));
        values.put(itemField(rowIndex, "spec"), value(item, "spec"));
        values.put(itemField(rowIndex, "length"), value(item, "length"));
        values.put(itemField(rowIndex, "quantity"), quantity);
        values.put(itemField(rowIndex, "pieceWeight"), displayPieceWeight(item));
        values.put(itemField(rowIndex, "weight"), weight);
    }

    private String itemField(int rowIndex, String fieldName) {
        return "item_" + rowIndex + "_" + fieldName;
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

    private String resolveBillNo(Map<String, String> data) {
        String outboundNo = value(data, "outboundNo");
        if (!outboundNo.isBlank()) {
            return outboundNo;
        }
        String orderNo = value(data, "orderNo");
        if (!orderNo.isBlank()) {
            return orderNo;
        }
        return value(data, "billNo");
    }

    private String resolveBillDate(Map<String, String> data) {
        return value(data, "deliveryDate", "outboundDate", "orderDate");
    }

    private String displayPieceWeight(Map<String, String> item) {
        return value(item, "pieceWeightTon");
    }

    private String displayWeight(Map<String, String> item) {
        return value(item, "weightTon");
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String formatTotalWeight(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0
                ? ""
                : value.setScale(3, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatTotalQuantity(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0
                ? ""
                : value.stripTrailingZeros().toPlainString();
    }

    private String totalSummary(String totalQuantity, String totalWeight) {
        if (totalQuantity.isBlank() && totalWeight.isBlank()) {
            return "";
        }
        String quantity = totalQuantity.isBlank() ? "0" : totalQuantity;
        String weight = totalWeight.isBlank() ? "0" : totalWeight;
        return "合计件数：" + quantity + "    合计重量：" + weight + " 吨";
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

    private static class Totals {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal weight = BigDecimal.ZERO;
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
            float lineHeightMultiplier
    ) {
    }

    private record PdfFormTemplateConfig(String form, String template) {
    }
}
