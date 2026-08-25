package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerStatementPdfTemplateRenderTest {

    @Test
    void rendersLandscapeItextPdfFromLayoutJson() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        PrintPdfFormService service = new PrintPdfFormService(
                null,
                new PrintPdfFormTemplateValidator(objectMapper)
        );
        String template;
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "print-forms/default-customer-statement.layout.json")) {
            assertThat(stream).isNotNull();
            template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        byte[] pdf = service.generateFromPayload(Map.of(
                "templateType", "PDF_FORM",
                "templateHtml", template,
                "data", statementData(),
                "items", List.of(
                        item("SO-002", "2026-08-25", "3", "0.600", "1200.00"),
                        item("SO-001", "2026-08-20", "2", "0.400", "800.00")
                )
        ));

        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getFirstPage().getPageSize().getWidth()).isEqualTo(842f);
            assertThat(document.getFirstPage().getPageSize().getHeight()).isEqualTo(595f);
            String text = PdfTextExtractor.getTextFromPage(document.getFirstPage());
            assertThat(text)
                    .contains("客户对账单", "SO-001", "SO-002", "2026年08月20日")
                    .doesNotContain("null");
        }
    }

    private Map<String, String> statementData() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("settlementCompanyName", "嘉兴颖捷建材有限公司");
        data.put("statementNo", "DZ-202608-001");
        data.put("customerName", "示例客户有限公司");
        data.put("projectName", "示例项目");
        data.put("startDate", "2026-08-01");
        data.put("endDate", "2026-08-25");
        data.put("salesAmount", "2000.00");
        data.put("receiptAmount", "500.00");
        data.put("closingAmount", "1500.00");
        data.put("createdName", "管理员");
        return data;
    }

    private Map<String, String> item(
            String sourceNo,
            String billTime,
            String quantity,
            String weightTon,
            String amount
    ) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("sourceNo", sourceNo);
        item.put("billTime", billTime);
        item.put("brand", "永钢");
        item.put("category", "螺纹钢");
        item.put("material", "HRB400E");
        item.put("spec", "Φ20");
        item.put("length", "12");
        item.put("quantity", quantity);
        item.put("quantityUnit", "件");
        item.put("pieceWeightTon", "0.200");
        item.put("weightTon", weightTon);
        item.put("unitPrice", "2000.00");
        item.put("amount", amount);
        return item;
    }
}
