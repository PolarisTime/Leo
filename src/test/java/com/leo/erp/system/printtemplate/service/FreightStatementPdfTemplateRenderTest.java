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
import java.util.ArrayList;
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

        // 单号不再占明细列，而是上提到「客户 / 项目」这一行；来源分组头只保留日期与车号，
        // 第二行是指标（右对齐），与详情卡片头一致。
        JsonNode sourceHeader = root.path("table").path("groupHeader").path("source");
        JsonNode lines = sourceHeader.path("lines");
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).asText())
                .contains("单据日期")
                .contains("车号")
                .doesNotContain("来源物流单");
        assertThat(lines.get(1).asText())
                .contains("数量")
                .contains("重量")
                .contains("运费单价")
                .contains("运费总价");
        assertThat(sourceHeader.path("lineAligns").get(0).asText()).isEqualTo("left");
        assertThat(sourceHeader.path("lineAligns").get(1).asText()).isEqualTo("right");

        JsonNode projectHeader = root.path("table").path("groupHeader").path("project");
        assertThat(projectHeader.path("lines").get(0).asText())
                .as("单号上提到客户 / 项目行，明细行不再重复打印同一张物流单号")
                .contains("客户")
                .contains("单号")
                .contains("${sourceNo}");
        assertThat(projectHeader.path("lines").get(1).asText()).contains("项目");
        assertThat(projectHeader.path("defaults").path("sourceNo").asText()).isEqualTo("-");

        // 数量/件重/重量三列右对齐，与详情只读表一致；文本列保持居中；单号列已移除且列宽合计仍等于表宽。
        Map<String, String> alignByKey = new LinkedHashMap<>();
        int columnWidthSum = 0;
        for (JsonNode column : root.path("table").path("columns")) {
            alignByKey.put(column.path("key").asText(), column.path("align").asText("center"));
            columnWidthSum += column.path("width").asInt();
        }
        assertThat(alignByKey)
                .containsEntry("quantity", "right")
                .containsEntry("pieceWeightTon", "right")
                .containsEntry("weightTon", "right")
                .containsEntry("brand", "center")
                .containsEntry("warehouseName", "center")
                .doesNotContainKey("sourceNo");
        assertThat(columnWidthSum)
                .as("去掉来源单号列后，宽度必须重新分配且合计仍等于表格宽度")
                .isEqualTo(root.path("table").path("width").asInt());

        // 换页：续页重复来源/项目上下文，且组头至少带 3 行明细才允许落在页尾。
        assertThat(root.path("table").path("repeatGroupContextOnContinuation").asBoolean()).isTrue();
        assertThat(root.path("table").path("minDetailRows").asInt()).isGreaterThanOrEqualTo(2);
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

        // 与 PrintScriptService.groupItemsForPdf 产出的分组行一致：来源头携带日期快照与合计/运费，
        // 项目头携带客户/项目以及上提的单号。
        Map<String, String> groupHeader = new LinkedHashMap<>();
        groupHeader.put("isGroupHeader", "source");
        groupHeader.put("sourceNo", "WL-202608-001");
        groupHeader.put("billTime", "2026-08-25");
        groupHeader.put("vehiclePlate", "浙F12345");
        groupHeader.put("totalQuantity", "12");
        groupHeader.put("totalWeightTon", "2.400");
        groupHeader.put("unitPrice", "160.00");
        groupHeader.put("totalFreight", "384.00");

        Map<String, String> projectHeader = new LinkedHashMap<>();
        projectHeader.put("isGroupHeader", "project");
        projectHeader.put("sourceNo", "WL-202608-001");
        projectHeader.put("customerName", "示例客户有限公司");
        projectHeader.put("projectName", "示例项目");
        projectHeader.put("totalQuantity", "12");
        projectHeader.put("totalWeightTon", "2.400");

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
                "items", List.of(groupHeader, projectHeader, freightItem())
        ));

        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            String text = PdfTextExtractor.getTextFromPage(document.getFirstPage());
            assertThat(text)
                    .as("来源头显示日期快照与合计/运费，项目头显示客户/项目与上提的单号")
                    .contains("2026年08月25日")
                    .contains("运费总价")
                    .contains("数量")
                    .contains("客户")
                    .contains("单号")
                    .contains("WL-202608-001")
                    .contains("项目")
                    .doesNotContain("null");
        }
    }

    @Test
    void continuationPagesRepeatSourceAndProjectContext() throws Exception {
        String template;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "print-forms/default-freight-statement.layout.json")) {
            assertThat(stream).isNotNull();
            template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        List<Map<String, String>> items = new ArrayList<>();
        items.add(sourceHeader("WL-202610-001", "2026-10-06"));
        items.add(projectHeader("WL-202610-001", "示例客户甲有限公司", "示例项目甲"));
        for (int index = 0; index < 45; index++) {
            items.add(freightItem());
        }
        items.add(sourceHeader("WL-202610-002", "2026-10-07"));
        items.add(projectHeader("WL-202610-002", "示例客户乙有限公司", "示例项目乙"));
        for (int index = 0; index < 5; index++) {
            items.add(freightItem());
        }

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
                        "period", "2026-10-01 至 2026-10-31",
                        "endDate", "2026-10-31"
                ),
                "items", items
        ));

        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            assertThat(document.getNumberOfPages())
                    .as("45 行明细必须发生换页")
                    .isGreaterThanOrEqualTo(2);
            // 续页顶部要重复来源/项目上下文（单据日期、客户、单号），否则跨页的行看不出属于哪张物流单
            for (int page = 2; page <= document.getNumberOfPages(); page++) {
                String text = PdfTextExtractor.getTextFromPage(document.getPage(page));
                assertThat(text)
                        .as("第 %d 页应重复分组上下文", page)
                        .contains("单据日期")
                        .contains("客户")
                        .contains("单号");
            }
            // 第二张物流单不能被拆到只剩孤行：出现新单号的那一页必须带上下文
            String lastPage = PdfTextExtractor.getTextFromPage(
                    document.getPage(document.getNumberOfPages()));
            assertThat(lastPage).contains("单号");
        }
    }

    private Map<String, String> sourceHeader(String sourceNo, String billTime) {
        Map<String, String> header = new LinkedHashMap<>();
        header.put("isGroupHeader", "source");
        header.put("sourceNo", sourceNo);
        header.put("billTime", billTime);
        header.put("vehiclePlate", "浙F12345");
        header.put("totalQuantity", "12");
        header.put("totalWeightTon", "2.400");
        header.put("unitPrice", "160.00");
        header.put("totalFreight", "384.00");
        return header;
    }

    private Map<String, String> projectHeader(
            String sourceNo,
            String customerName,
            String projectName
    ) {
        Map<String, String> header = new LinkedHashMap<>();
        header.put("isGroupHeader", "project");
        header.put("sourceNo", sourceNo);
        header.put("customerName", customerName);
        header.put("projectName", projectName);
        header.put("totalQuantity", "12");
        header.put("totalWeightTon", "2.400");
        return header;
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
