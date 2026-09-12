package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A4 销售订单 PDF 模板汇总区契约：汇总拆成两行，第一行合计件数/合计重量，
 * 第二行单据备注/附加费用，且附加费用由 ${totalChargeAmount} 回填。
 */
class SalesOrderA4PdfTemplateRenderTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "print-forms/sales-order-a4-yingjie.layout.json",
            "print-forms/sales-order-a4-yiqi.layout.json",
            "print-forms/yingjie-a4-remark.layout.json"
    })
    void rendersTwoLineSummaryWithChargeAmount(String templatePath) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        PrintPdfFormService service = new PrintPdfFormService(
                null,
                new PrintPdfFormTemplateValidator(objectMapper),
                objectMapper
        );
        String template;
        try (var stream = getClass().getClassLoader().getResourceAsStream(templatePath)) {
            assertThat(stream).as(templatePath).isNotNull();
            template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        Map<String, String> data = new LinkedHashMap<>();
        data.put("customerName", "示例客户有限公司");
        data.put("orderNo", "SO-001");
        data.put("projectName", "示例项目");
        data.put("deliveryDate", "2026-09-12");
        data.put("remark", "请雨天遮盖");
        data.put("totalQuantity", "5");
        data.put("totalWeight", "1.000");
        data.put("totalAmount", "5000.00");
        data.put("chargeItemsText", "运费 1000元（大写壹仟元整）、装卸费 250.50元（大写贰佰伍拾元伍角）");

        byte[] pdf = service.generateFromPayload(Map.of(
                "templateType", "PDF_FORM",
                "templateHtml", template,
                "data", data,
                "items", List.of(item())
        ));

        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            String text = PdfTextExtractor.getTextFromPage(document.getFirstPage());
            assertThat(text)
                    .contains("合计件数：5件")
                    .contains("合计重量：1.000吨")
                    .contains("单据备注：请雨天遮盖")
                    .contains("附加费用：运费 1000元（大写壹仟元整）")
                    .doesNotContain("null")
                    .doesNotContain("${");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "print-forms/sales-order-a4-yingjie.layout.json",
            "print-forms/sales-order-a4-yiqi.layout.json",
            "print-forms/yingjie-a4-remark.layout.json"
    })
    void rendersEmptyChargeAsPlaceholder(String templatePath) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        PrintPdfFormService service = new PrintPdfFormService(
                null,
                new PrintPdfFormTemplateValidator(objectMapper),
                objectMapper
        );
        String template;
        try (var stream = getClass().getClassLoader().getResourceAsStream(templatePath)) {
            assertThat(stream).as(templatePath).isNotNull();
            template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        byte[] pdf = service.generateFromPayload(Map.of(
                "templateType", "PDF_FORM",
                "templateHtml", template,
                "data", Map.of(
                        "remark", "",
                        "totalQuantity", "1",
                        "totalWeight", "0.200",
                        "chargeItemsText", "无"
                ),
                "items", List.of(item())
        ));

        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            assertThat(PdfTextExtractor.getTextFromPage(document.getFirstPage()))
                    .contains("附加费用：无");
        }
    }

    private Map<String, String> item() {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("brand", "永钢");
        item.put("category", "螺纹钢");
        item.put("material", "HRB400E");
        item.put("spec", "Φ20");
        item.put("length", "12");
        item.put("quantity", "5");
        item.put("pieceWeightTon", "0.200");
        item.put("weightTon", "1.000");
        return item;
    }
}
