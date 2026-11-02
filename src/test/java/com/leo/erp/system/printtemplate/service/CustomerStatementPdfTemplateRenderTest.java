package com.leo.erp.system.printtemplate.service;

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

class CustomerStatementPdfTemplateRenderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rendersReferenceStyleSingleTableWithInlineDateGrouping() throws Exception {
        byte[] pdf = render(List.of(
                item("2026-08-25", "3", "0.600", "1200.00"),
                item("2026-08-20", "2", "0.400", "800.00"),
                item("2026-08-20", "1", "0.200", "400.00")
        ));

        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getFirstPage().getPageSize().getWidth()).isEqualTo(842f);
            assertThat(document.getFirstPage().getPageSize().getHeight()).isEqualTo(595f);
            String text = PdfTextExtractor.getTextFromPage(document.getFirstPage());

            assertThat(text)
                    .as("抬头照参考格式：公司名 + 对账单，项目名称/需方/时间")
                    .contains("嘉兴颖捷建材有限公司对账单")
                    .contains("项目名称")
                    .contains("需方")
                    .contains("时间")
                    .doesNotContain("null");
            assertThat(text)
                    .as("明细为一张连续表，列名照参考格式")
                    .contains("序号", "进货日期", "产品名称", "产地", "牌号商标", "规格型号",
                            "件重/吨", "件数", "重量小计/吨", "单价(元/吨)", "总金额/元", "备注")
                    .doesNotContain("分组序号", "销售单号", "交货日期");
            assertThat(count(text, "进货日期"))
                    .as("整页只出现一次表头（不再按分组重复）")
                    .isEqualTo(1);
            assertThat(count(text, "2026年08月20日"))
                    .as("同日期的多行只在第一行打印日期（合并单元格效果）")
                    .isEqualTo(1);
            assertThat(count(text, "2026年08月25日")).isEqualTo(1);
            assertThat(text)
                    .as("已收金额为空时汇总行显示 0.00 而不是空白")
                    .contains("已收金额：0.00 元");
        }
    }

    @Test
    void repeatsTableHeaderOnEveryPageWithoutGroupHeaderRows() throws Exception {
        List<Map<String, String>> items = new ArrayList<>();
        for (int dateIndex = 0; dateIndex < 4; dateIndex++) {
            String billTime = String.format("2026-08-%02d", 10 + dateIndex);
            for (int rowIndex = 0; rowIndex < 20; rowIndex++) {
                items.add(item(billTime, "20", "40.000", "128160.60"));
            }
        }

        byte[] pdf = render(items);

        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(3);
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                String text = PdfTextExtractor.getTextFromPage(document.getPage(page));
                assertThat(count(text, "进货日期"))
                        .as("第 %d 页应且只应有一行表头", page)
                        .isEqualTo(1);
                assertThat(text).doesNotContain("null");
            }
        }
    }

    @Test
    void repeatsGroupDateAtTopOfContinuationPage() throws Exception {
        List<Map<String, String>> items = new ArrayList<>();
        for (int index = 0; index < 60; index++) {
            items.add(item("2026-08-20", "20", "40.000", "128160.60"));
        }

        byte[] pdf = render(items);

        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(2);
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                String text = PdfTextExtractor.getTextFromPage(document.getPage(page));
                assertThat(text)
                        .as("第 %d 页续页首行应补回分组日期", page)
                        .contains("2026年08月20日");
            }
        }
    }

    private byte[] render(List<Map<String, String>> items) throws Exception {
        String template;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "print-forms/default-customer-statement.layout.json")) {
            assertThat(stream).isNotNull();
            template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        PrintPdfFormService service = new PrintPdfFormService(
                null,
                new PrintPdfFormTemplateValidator(objectMapper),
                objectMapper
        );
        return service.generateFromPayload(Map.of(
                "templateType", "PDF_FORM",
                "templateHtml", template,
                "data", statementData(),
                "items", items
        ));
    }

    private int count(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private Map<String, String> statementData() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("settlementCompanyName", "嘉兴颖捷建材有限公司");
        data.put("statementNo", "DZ-202608-001");
        data.put("customerName", "浙江卡森建设有限公司");
        data.put("projectName", "嘉兴瑞辰汽车零部件有限公司年产2660万个新能源汽车电池仓外壳及各类汽车零部件项目（车间、传达室）");
        data.put("startDate", "2026-08-01");
        data.put("endDate", "2026-08-31");
        data.put("salesAmount", "2000.00");
        data.put("receiptAmount", "");
        data.put("closingAmount", "1500.00");
        data.put("createdName", "管理员");
        return data;
    }

    private Map<String, String> item(
            String billTime,
            String quantity,
            String weightTon,
            String amount
    ) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("sourceNo", "SO-202608-001");
        item.put("billTime", billTime);
        item.put("category", "盘螺");
        item.put("brand", "永钢");
        item.put("material", "HRB400E");
        item.put("spec", "Φ20");
        item.put("length", "12");
        item.put("quantity", quantity);
        item.put("quantityUnit", "件");
        item.put("pieceWeightTon", "0.200");
        item.put("weightTon", weightTon);
        item.put("unitPrice", "2000.00");
        item.put("amount", amount);
        item.put("remark", "参照8月21日上午价+20");
        return item;
    }
}
