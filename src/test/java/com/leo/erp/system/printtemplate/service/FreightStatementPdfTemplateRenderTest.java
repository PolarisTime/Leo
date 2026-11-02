package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FreightStatementPdfTemplateRenderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void defaultTemplateUsesStructuredHeaderAndReadableTablePalette() throws Exception {
        JsonNode root;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "print-forms/default-freight-statement.layout.json")) {
            assertThat(stream).isNotNull();
            root = objectMapper.readTree(stream);
        }

        JsonNode staticElements = root.path("static");
        assertThat(staticElements.get(0).path("type").asText()).isEqualTo("rect");
        assertThat(staticElements.get(0).path("fillColor").asText()).isEqualTo("#173f46");
        assertThat(staticElements.get(1).path("text").asText()).isEqualTo("物流对账单");
        assertThat(staticElements.get(1).path("color").asText()).isEqualTo("#ffffff");
        assertThat(root.path("fields").path("billNo").path("width").asDouble())
                .as("长单号必须在标题带内完整显示")
                .isGreaterThanOrEqualTo(110D);

        JsonNode table = root.path("table");
        assertThat(table.path("headerFillColor").asText()).isEqualTo("#e8f1f2");
        assertThat(table.path("borderColor").asText()).isEqualTo("#c9d8da");
        assertThat(table.path("lineWidth").asDouble()).isLessThan(1D);
        assertThat(root.path("summary").path("border").asBoolean()).isTrue();
    }

    @Test
    void defaultTemplateMatchesDetailCardLayoutForGroupsAndNumbers() throws Exception {
        JsonNode root;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "print-forms/default-freight-statement.layout.json")) {
            assertThat(stream).isNotNull();
            root = objectMapper.readTree(stream);
        }

        // 分组头两行：第一行身份（来源物流单/单据日期/车号）左对齐，第二行指标右对齐，
        // 与详情卡片头「来源物流单 + 单据日期 | 数量/重量/运费单价/运费总价」一致。
        JsonNode sourceHeader = root.path("table").path("groupHeader").path("source");
        JsonNode lines = sourceHeader.path("lines");
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).asText())
                .contains("来源物流单")
                .contains("单据日期")
                .contains("车号");
        assertThat(lines.get(1).asText())
                .contains("数量")
                .contains("重量")
                .contains("运费单价")
                .contains("运费总价");
        assertThat(sourceHeader.path("lineAligns").get(0).asText()).isEqualTo("left");
        assertThat(sourceHeader.path("lineAligns").get(1).asText()).isEqualTo("right");

        // 数量/件重/重量三列右对齐，与详情只读表一致；文本列保持居中。
        Map<String, String> alignByKey = new LinkedHashMap<>();
        for (JsonNode column : root.path("table").path("columns")) {
            alignByKey.put(column.path("key").asText(), column.path("align").asText("center"));
        }
        assertThat(alignByKey)
                .containsEntry("quantity", "right")
                .containsEntry("pieceWeightTon", "right")
                .containsEntry("weightTon", "right")
                .containsEntry("brand", "center")
                .containsEntry("warehouseName", "center");
    }

    @Test
    void rendersPortraitPdfWithLongStatementNumberAndGroupedRows() throws Exception {
        String template;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "print-forms/default-freight-statement.layout.json")) {
            assertThat(stream).isNotNull();
            template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        PrintPdfFormService service = new PrintPdfFormService(
                null,
                new PrintPdfFormTemplateValidator(objectMapper),
                objectMapper
        );
        String statementNo = "35314192407436492";
        byte[] pdf = service.generateFromPayload(Map.of(
                "templateType", "PDF_FORM",
                "templateHtml", template,
                "data", Map.of(
                        "carrierName", "示例物流运输有限公司",
                        "statementNo", statementNo,
                        "period", "2026-08-01 至 2026-08-31",
                        "endDate", "2026-08-31"
                ),
                "items", List.of(freightItem())
        ));

        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getFirstPage().getPageSize().getWidth()).isEqualTo(595f);
            assertThat(document.getFirstPage().getPageSize().getHeight()).isEqualTo(842f);
            assertThat(PdfTextExtractor.getTextFromPage(document.getFirstPage()))
                    .contains("物流对账单", statementNo, "2026年08月31日", "示例物流运输有限公司")
                    .doesNotContain("null");
        }
    }

    @Test
    void rendersSourceGroupHeaderWithSnapshotDateAndCardMetrics() throws Exception {
        String template;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "print-forms/default-freight-statement.layout.json")) {
            assertThat(stream).isNotNull();
            template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 与 PrintScriptService.groupItemsForPdf 产出的分组行一致：分组头行携带
        // 来源物流单日期快照与合计/运费，字体与布局走模板的 groupHeader.source。
        Map<String, String> groupHeader = new LinkedHashMap<>();
        groupHeader.put("isGroupHeader", "source");
        groupHeader.put("sourceNo", "WL-202608-001");
        groupHeader.put("billTime", "2026-08-25");
        groupHeader.put("vehiclePlate", "浙F12345");
        groupHeader.put("totalQuantity", "12");
        groupHeader.put("totalWeightTon", "2.400");
        groupHeader.put("unitPrice", "160.00");
        groupHeader.put("totalFreight", "384.00");

        PrintPdfFormService service = new PrintPdfFormService(
                null,
                new PrintPdfFormTemplateValidator(objectMapper),
                objectMapper
        );
        byte[] pdf = service.generateFromPayload(Map.of(
                "templateType", "PDF_FORM",
                "templateHtml", template,
                "data", Map.of(
                        "carrierName", "示例物流运输有限公司",
                        "statementNo", "35314192407436492",
                        "period", "2026-08-01 至 2026-08-31",
                        "endDate", "2026-08-31"
                ),
                "items", List.of(groupHeader, freightItem())
        ));

        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            String text = PdfTextExtractor.getTextFromPage(document.getFirstPage());
            assertThat(text)
                    .as("分组头要显示来源物流单日期（快照）与该单合计/运费，而不是空日期")
                    .contains("来源物流单")
                    .contains("2026年08月25日")
                    .contains("运费总价")
                    .contains("数量")
                    .doesNotContain("null");
        }
    }

    private Map<String, String> freightItem() {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("sourceNo", "WL-202608-001");
        item.put("billTime", "2026-08-25");
        item.put("vehiclePlate", "浙F12345");
        item.put("customerName", "示例客户有限公司");
        item.put("projectName", "示例项目");
        item.put("brand", "永钢");
        item.put("category", "螺纹钢");
        item.put("material", "HRB400E");
        item.put("spec", "Φ20");
        item.put("length", "12");
        item.put("quantity", "12");
        item.put("pieceWeightTon", "0.200");
        item.put("weightTon", "2.400");
        item.put("warehouseName", "一号仓");
        item.put("unitPrice", "160.00");
        item.put("totalFreight", "384.00");
        return item;
    }
}
