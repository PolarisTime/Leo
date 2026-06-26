package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.leo.erp.attachment.service.AttachmentRecordAccessService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrintScriptServiceTest {

    @BeforeEach
    void setUp() {
        SecurityPrincipal principal = SecurityPrincipal.authenticated(1L, "tester", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldEnrichSalesOutboundPrintDataWithCurrentFields() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PrintScriptService service = printScriptService(repository("sales-outbound"), jdbc);

        when(jdbc.queryForMap(anyString(), eq(317377016682774528L))).thenReturn(Map.of(
                "id", 317377016682774528L,
                "outbound_no", "SOO-001",
                "project_name", "项目A",
                "deleted_flag", false
        ));
        when(jdbc.queryForList(anyString(), eq(317377016682774528L))).thenReturn(List.of(Map.of(
                "line_no", 1,
                "quantity", 2,
                "weight_ton", new BigDecimal("1.230")
        )));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("SOO-001"))).thenReturn(List.of("浙A12345"));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("项目A"))).thenReturn(List.of("项目地址A"));

        Map<String, Object> result = service.generateFromRecord("1", "sales-outbound", 317377016682774528L);

        Map<?, ?> data = (Map<?, ?>) result.get("data");
        assertThat(data.get("vehiclePlate")).isEqualTo("浙A12345");
        assertThat(data.get("projectAddress")).isEqualTo("项目地址A");
    }

    @Test
    void shouldEnrichCustomerStatementItemsWithBillTime() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PrintScriptService service = printScriptService(repository("customer-statement"), jdbc);

        when(jdbc.queryForMap(anyString(), anyLong())).thenReturn(Map.of(
                "id", 1L,
                "statement_no", "CS-001",
                "deleted_flag", false
        ));
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(Map.of(
                "line_no", 1,
                "source_no", "SO-001",
                "quantity", 2
        )));
        when(jdbc.queryForList(anyString(), eq("SO-001"))).thenReturn(List.of(Map.of(
                "order_no", "SO-001",
                "delivery_date", Date.valueOf("2026-05-30")
        )));

        Map<String, Object> result = service.generateFromRecord("1", "customer-statement", 1L);

        List<?> items = (List<?>) result.get("items");
        Map<?, ?> item = (Map<?, ?>) items.get(0);
        assertThat(item.get("billTime")).isEqualTo("2026-05-30");
    }

    @Test
    void shouldEnrichFreightStatementItemsWithSourceBillValues() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PrintScriptService service = printScriptService(repository("freight-statement"), jdbc);

        when(jdbc.queryForMap(anyString(), anyLong())).thenReturn(Map.of(
                "id", 1L,
                "statement_no", "FS-001",
                "carrier_name", "物流A",
                "deleted_flag", false
        ));
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(Map.of(
                "line_no", 1,
                "source_no", "FB-001",
                "weight_ton", new BigDecimal("3.000")
        )));
        when(jdbc.queryForList(anyString(), eq("FB-001"))).thenReturn(List.of(Map.of(
                "bill_no", "FB-001",
                "bill_time", Date.valueOf("2026-05-30"),
                "carrier_name", "物流A",
                "unit_price", new BigDecimal("12.50"),
                "total_freight", new BigDecimal("37.50"),
                "remark", "备注"
        )));

        Map<String, Object> result = service.generateFromRecord("1", "freight-statement", 1L);

        List<?> items = (List<?>) result.get("items");
        Map<?, ?> item = (Map<?, ?>) items.get(0);
        assertThat(item.get("billTime")).isEqualTo("2026-05-30");
        assertThat(item.get("carrierName")).isEqualTo("物流A");
        assertThat(item.get("unitPrice")).isEqualTo("12.50");
        assertThat(item.get("amount")).isEqualTo("37.50");
    }

    @Test
    void shouldFillAndFlattenPdfFormFromSharedLayout() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PrintScriptService scriptService = printScriptService(repository(
                "sales-order",
                "PDF_FORM",
                yingjieA4RemarkLayout()
        ), jdbc);
        PrintPdfFormService pdfFormService = new PrintPdfFormService(
                scriptService,
                new PrintPdfFormTemplateValidator(new ObjectMapper())
        );
        String projectName = "海宁市高新区启辉路西侧之江北路北侧地块项目1号楼2号楼配电房地下室工程";

        when(jdbc.queryForMap(anyString(), eq(1L))).thenReturn(Map.of(
                "id", 1L,
                "order_no", "SO-001",
                "customer_name", "客户A",
                "project_name", projectName,
                "delivery_date", Date.valueOf("2026-05-31"),
                "remark", "6月4日报单",
                "deleted_flag", false
        ));
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(Map.of(
                "line_no", 1,
                "brand", "抚顺新钢",
                "category", "螺纹钢",
                "material", "HRB400E",
                "spec", "Ф18",
                "quantity", 2,
                "weight_ton", new BigDecimal("2.345")
        )));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("SO-001"))).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(String.class), eq(projectName))).thenReturn(List.of("项目地址A"));

        byte[] pdf = pdfFormService.generateFromRecord("1", "sales-order", 1L);

        assertThat(pdf).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(pdf.length).isGreaterThan(1000);
        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            assertThat(PdfAcroForm.getAcroForm(document, false)).isNull();
            String text = PdfTextExtractor.getTextFromPage(document.getFirstPage());
            assertThat(text).contains("客户A", "SO-001", "2026年05月31日", "抚顺新钢", "螺纹钢");
            assertThat(text).contains("地下室工程");
            assertThat(text).contains("单据备注：6月4日报单", "合计件数：2件", "合计重量：2.345吨");
            assertThat(text).contains("同时承担供方", "实现债权支出的一切费用");
        }
    }

    @Test
    void shouldRenderCoordScriptFromSharedLayoutConfig() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PrintScriptService service = printScriptService(repository(
                "sales-order",
                "COORD",
                """
                        {
                          "fields": {
                            "customerName": {"source": "customerName", "left": 10, "top": 20, "width": 120, "height": 18}
                          },
                          "table": {
                            "left": 10,
                            "top": 50,
                            "headerHeight": 20,
                            "rowHeight": 18,
                            "columns": [
                              {"key": "material", "label": "材质", "width": 80, "normalize": "compactAscii"}
                            ]
                          },
                          "summary": {
                            "height": 18,
                            "template": "合计件数：${totalQuantity}件    |    合计重量：${totalWeight}吨"
                          }
                        }
                        """
        ), jdbc);

        when(jdbc.queryForMap(anyString(), eq(1L))).thenReturn(Map.of(
                "id", 1L,
                "order_no", "SO-001",
                "customer_name", "客户A",
                "deleted_flag", false
        ));
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(Map.of(
                "line_no", 1,
                "material", "H R B 4 0 0 E",
                "quantity", 2,
                "weight_ton", new BigDecimal("2.345")
        )));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("SO-001"))).thenReturn(List.of());

        Map<String, Object> result = service.generateFromRecord("1", "sales-order", 1L);

        assertThat(result.get("templateHtml").toString()).contains(
                "LODOP.PRINT_INIT",
                "LODOP.ADD_PRINT_TEXT",
                "客户A",
                "HRB400E",
                "合计件数：2件    |    合计重量：2.345吨"
        );
    }

    @Test
    void shouldPaginateSalesOrderA5WithoutTruncatingRows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PrintScriptService service = printScriptService(repositoryWithTemplateName(
                "sales-order",
                "A5套打模版",
                "COORD",
                """
                        LODOP.PRINT_INIT("A5套打模版");
                        {{#each details}}
                        {{#if needsNewPage}}LODOP.NewPage();{{/if}}
                        LODOP.ADD_PRINT_TEXT({{rowTop}},10,80,16,"{{index}}");
                        {{/each}}
                        """
        ), jdbc);

        when(jdbc.queryForMap(anyString(), eq(1L))).thenReturn(Map.of(
                "id", 1L,
                "order_no", "SO-001",
                "project_name", "项目A",
                "deleted_flag", false
        ));
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(
                Map.of("line_no", 1, "quantity", 1, "weight_ton", new BigDecimal("1.000")),
                Map.of("line_no", 2, "quantity", 2, "weight_ton", new BigDecimal("2.000")),
                Map.of("line_no", 3, "quantity", 3, "weight_ton", new BigDecimal("3.000")),
                Map.of("line_no", 4, "quantity", 4, "weight_ton", new BigDecimal("4.000")),
                Map.of("line_no", 5, "quantity", 5, "weight_ton", new BigDecimal("5.000")),
                Map.of("line_no", 6, "quantity", 6, "weight_ton", new BigDecimal("6.000")),
                Map.of("line_no", 7, "quantity", 7, "weight_ton", new BigDecimal("7.000")),
                Map.of("line_no", 8, "quantity", 8, "weight_ton", new BigDecimal("8.000"))
        ));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("SO-001"))).thenReturn(List.of());

        Map<String, Object> result = service.generateFromRecord("1", "sales-order", 1L);

        List<?> items = (List<?>) result.get("items");
        assertThat(items).hasSize(8);
        Map<?, ?> seventhItem = (Map<?, ?>) items.get(6);
        assertThat(seventhItem.get("index")).isEqualTo("7");
        assertThat(seventhItem.get("rowTop")).isEqualTo("407");
        Map<?, ?> eighthItem = (Map<?, ?>) items.get(7);
        assertThat(eighthItem.get("index")).isEqualTo("8");
        assertThat(eighthItem.get("rowTop")).isEqualTo("161");
        assertThat(eighthItem.get("needsNewPage")).isEqualTo("true");
        assertThat(eighthItem.get("totalQuantity")).isEqualTo("36");
        assertThat(eighthItem.get("totalWeight")).isEqualTo("36.000");
        Map<?, ?> data = (Map<?, ?>) result.get("data");
        assertThat(data.get("totalQuantity")).isEqualTo("36");
        assertThat(data.get("totalWeight")).isEqualTo("36.000");
        assertThat(data.get("projectNameTop")).isEqualTo("90");
        assertThat(data.get("projectNameHeight")).isEqualTo("20");
        assertThat(data.get("projectNameFontSize")).isEqualTo("12");
        assertThat(data.get("projectNameWordBreak")).isEqualTo("0");
    }

    @Test
    void shouldAdaptSalesOrderA5ProjectNameLayoutByDisplayWidth() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PrintScriptService service = printScriptService(repository("sales-order"), jdbc);
        String longProjectName = "海宁市高新区启辉路西侧之江北路北侧地块项目（1#-20#楼、1#裙房配电房）";

        when(jdbc.queryForMap(anyString(), eq(1L))).thenReturn(Map.of(
                "id", 1L,
                "order_no", "SO-001",
                "project_name", longProjectName,
                "deleted_flag", false
        ));
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(String.class), eq("SO-001"))).thenReturn(List.of());

        Map<String, Object> longResult = service.generateFromRecord("1", "sales-order", 1L);

        Map<?, ?> longData = (Map<?, ?>) longResult.get("data");
        assertThat(longData.get("projectNameMultiline")).isEqualTo("true");
        assertThat(longData.get("projectNameTop")).isEqualTo("78");
        assertThat(longData.get("projectNameHeight")).isEqualTo("42");
        assertThat(longData.get("projectNameFontSize")).isEqualTo("12");
        assertThat(longData.get("projectNameWordBreak")).isEqualTo("1");

        String veryLongProjectName = "海宁市高新区启辉路西侧之江北路北侧地块项目（1#-20#楼、1#裙房配电房地下室及配套工程施工总承包项目）";
        when(jdbc.queryForMap(anyString(), eq(3L))).thenReturn(Map.of(
                "id", 3L,
                "order_no", "SO-003",
                "project_name", veryLongProjectName,
                "deleted_flag", false
        ));
        when(jdbc.queryForList(anyString(), eq(3L))).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(String.class), eq("SO-003"))).thenReturn(List.of());

        Map<String, Object> veryLongResult = service.generateFromRecord("1", "sales-order", 3L);

        Map<?, ?> veryLongData = (Map<?, ?>) veryLongResult.get("data");
        assertThat(veryLongData.get("projectNameMultiline")).isEqualTo("true");
        assertThat(veryLongData.get("projectNameTop")).isEqualTo("70");
        assertThat(veryLongData.get("projectNameHeight")).isEqualTo("54");
        assertThat(veryLongData.get("projectNameFontSize")).isEqualTo("10");
        assertThat(veryLongData.get("projectNameWordBreak")).isEqualTo("1");

        String extraLongProjectName = veryLongProjectName + veryLongProjectName;
        when(jdbc.queryForMap(anyString(), eq(4L))).thenReturn(Map.of(
                "id", 4L,
                "order_no", "SO-004",
                "project_name", extraLongProjectName,
                "deleted_flag", false
        ));
        when(jdbc.queryForList(anyString(), eq(4L))).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(String.class), eq("SO-004"))).thenReturn(List.of());

        Map<String, Object> extraLongResult = service.generateFromRecord("1", "sales-order", 4L);

        Map<?, ?> extraLongData = (Map<?, ?>) extraLongResult.get("data");
        assertThat(extraLongData.get("projectNameMultiline")).isEqualTo("true");
        assertThat(extraLongData.get("projectNameTop")).isEqualTo("62");
        assertThat(extraLongData.get("projectNameHeight")).isEqualTo("58");
        assertThat(extraLongData.get("projectNameFontSize")).isEqualTo("9");
        assertThat(extraLongData.get("projectNameWordBreak")).isEqualTo("1");

        when(jdbc.queryForMap(anyString(), eq(2L))).thenReturn(Map.of(
                "id", 2L,
                "order_no", "SO-002",
                "project_name", "项目A",
                "deleted_flag", false
        ));
        when(jdbc.queryForList(anyString(), eq(2L))).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(String.class), eq("SO-002"))).thenReturn(List.of());

        Map<String, Object> shortResult = service.generateFromRecord("1", "sales-order", 2L);

        Map<?, ?> shortData = (Map<?, ?>) shortResult.get("data");
        assertThat(shortData.get("projectNameMultiline")).isEqualTo("");
        assertThat(shortData.get("projectNameTop")).isEqualTo("90");
        assertThat(shortData.get("projectNameHeight")).isEqualTo("20");
        assertThat(shortData.get("projectNameFontSize")).isEqualTo("12");
        assertThat(shortData.get("projectNameWordBreak")).isEqualTo("0");
    }

    @Test
    void shouldRenderEmptyRemarkAsNoneInSharedLayoutSummary() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PrintScriptService service = printScriptService(repository(
                "sales-order",
                "COORD",
                """
                        {
                          "table": {
                            "left": 10,
                            "top": 50,
                            "headerHeight": 20,
                            "rowHeight": 18,
                            "columns": [
                              {"key": "quantity", "label": "件数", "width": 80}
                            ]
                          },
                          "summary": {
                            "height": 18,
                            "template": "单据备注：${remark}    |    合计件数：${totalQuantity}件"
                          }
                        }
                        """
        ), jdbc);

        when(jdbc.queryForMap(anyString(), eq(1L))).thenReturn(Map.of(
                "id", 1L,
                "order_no", "SO-001",
                "deleted_flag", false
        ));
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(Map.of("quantity", 2)));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("SO-001"))).thenReturn(List.of());

        Map<String, Object> result = service.generateFromRecord("1", "sales-order", 1L);

        assertThat(result.get("templateHtml").toString()).contains("单据备注：无    |    合计件数：2件");
    }

    @Test
    void shouldHideUnitPriceWhenPrintOptionEnabled() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PrintScriptService service = printScriptService(repository("purchase-order"), jdbc);

        when(jdbc.queryForMap(anyString(), eq(1L))).thenReturn(Map.of(
                "id", 1L,
                "order_no", "PO-001",
                "remark", "单据备注",
                "deleted_flag", false
        ));
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(Map.of(
                "line_no", 1,
                "brand", "抚顺新钢",
                "quantity", 2,
                "unit_price", new BigDecimal("12.50"),
                "amount", new BigDecimal("25.00")
        )));

        Map<String, Object> result = service.generateFromRecord(
                "1",
                "purchase-order",
                1L,
                new PrintRenderOptions(true, true, "", Map.of("抚顺新钢", "沙钢"), Map.of(), List.of())
        );

        Map<?, ?> data = (Map<?, ?>) result.get("data");
        List<?> items = (List<?>) result.get("items");
        Map<?, ?> item = (Map<?, ?>) items.get(0);
        assertThat(data.get("remark")).isEqualTo("");
        assertThat(item.get("unitPrice")).isEqualTo("");
        assertThat(item.get("brand")).isEqualTo("沙钢");
        assertThat(item.get("amount")).isEqualTo("25.00");
    }

    @Test
    void shouldOverrideBrandByItemId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PrintScriptService service = printScriptService(repository("purchase-order"), jdbc);

        when(jdbc.queryForMap(anyString(), eq(1L))).thenReturn(Map.of(
                "id", 1L,
                "order_no", "PO-001",
                "deleted_flag", false
        ));
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(
                Map.of(
                        "id", 11L,
                        "line_no", 1,
                        "brand", "抚顺新钢",
                        "quantity", 2,
                        "weight_ton", new BigDecimal("2.000")
                ),
                Map.of(
                        "id", 12L,
                        "line_no", 2,
                        "brand", "抚顺新钢",
                        "quantity", 3,
                        "weight_ton", new BigDecimal("3.000")
                )
        ));

        Map<String, Object> result = service.generateFromRecord(
                "1",
                "purchase-order",
                1L,
                new PrintRenderOptions(false, false, "", Map.of(), Map.of("11", "沙钢"), List.of())
        );

        List<?> items = (List<?>) result.get("items");
        assertThat(((Map<?, ?>) items.get(0)).get("brand")).isEqualTo("沙钢");
        assertThat(((Map<?, ?>) items.get(1)).get("brand")).isEqualTo("抚顺新钢");
    }

    @Test
    void shouldApplyItemOrderBeforePreparingPrintItems() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PrintScriptService service = printScriptService(repository("purchase-order"), jdbc);

        when(jdbc.queryForMap(anyString(), eq(1L))).thenReturn(Map.of(
                "id", 1L,
                "order_no", "PO-001",
                "deleted_flag", false
        ));
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(
                Map.of(
                        "id", 11L,
                        "line_no", 1,
                        "brand", "品牌1",
                        "quantity", 1,
                        "weight_ton", new BigDecimal("1.000")
                ),
                Map.of(
                        "id", 12L,
                        "line_no", 2,
                        "brand", "品牌2",
                        "quantity", 2,
                        "weight_ton", new BigDecimal("2.000")
                ),
                Map.of(
                        "id", 13L,
                        "line_no", 3,
                        "brand", "品牌3",
                        "quantity", 3,
                        "weight_ton", new BigDecimal("3.000")
                )
        ));

        Map<String, Object> result = service.generateFromRecord(
                "1",
                "purchase-order",
                1L,
                new PrintRenderOptions(false, false, "", Map.of(), Map.of(), List.of("13", "11"))
        );

        List<?> items = (List<?>) result.get("items");
        assertThat(((Map<?, ?>) items.get(0)).get("id")).isEqualTo("13");
        assertThat(((Map<?, ?>) items.get(0)).get("index")).isEqualTo("1");
        assertThat(((Map<?, ?>) items.get(1)).get("id")).isEqualTo("11");
        assertThat(((Map<?, ?>) items.get(1)).get("index")).isEqualTo("2");
        assertThat(((Map<?, ?>) items.get(2)).get("id")).isEqualTo("12");
        assertThat(((Map<?, ?>) items.get(2)).get("index")).isEqualTo("3");
    }

    @Test
    void shouldListBrandsFromSelectedRecords() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AttachmentRecordAccessService accessService = mock(AttachmentRecordAccessService.class);
        PrintScriptService service = printScriptService(repository("sales-order"), jdbc, accessService);

        when(jdbc.queryForList(anyString(), eq(String.class), eq(1L), eq(2L))).thenReturn(List.of(" 抚顺新钢 ", "沙钢"));

        List<String> brands = service.listBrands("sales-order", List.of(1L, 2L));

        assertThat(brands).containsExactly("抚顺新钢", "沙钢");
        verify(accessService).assertRecordAccessible(org.mockito.ArgumentMatchers.any(), eq("sales-order"), eq("read"), eq(1L));
        verify(accessService).assertRecordAccessible(org.mockito.ArgumentMatchers.any(), eq("sales-order"), eq("read"), eq(2L));
    }

    @Test
    void shouldListPrintItemsFromSelectedRecords() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AttachmentRecordAccessService accessService = mock(AttachmentRecordAccessService.class);
        PrintScriptService service = printScriptService(repository("sales-order"), jdbc, accessService);

        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(Map.ofEntries(
                Map.entry("id", 11L),
                Map.entry("record_id", 1L),
                Map.entry("brand", "中杭"),
                Map.entry("category", "螺纹钢"),
                Map.entry("material", "HRB400E"),
                Map.entry("spec", "Ф18"),
                Map.entry("quantity", 12),
                Map.entry("piece_weight_ton", new BigDecimal("0.123")),
                Map.entry("weight_ton", new BigDecimal("1.476")),
                Map.entry("unit_price", new BigDecimal("3300.00")),
                Map.entry("amount", new BigDecimal("4870.80"))
        )));

        List<PrintScriptService.PrintRecordItem> items = service.listPrintItems("sales-order", List.of(1L));

        assertThat(items).containsExactly(new PrintScriptService.PrintRecordItem(
                "11", "1", "中杭", "螺纹钢", "HRB400E", "Ф18", "12", "0.123", "1.476", "3300.00", "4870.80"
        ));
        verify(accessService).assertRecordAccessible(org.mockito.ArgumentMatchers.any(), eq("sales-order"), eq("read"), eq(1L));
    }

    @Test
    void shouldUseProjectNameLabelAndCenteredAdaptiveProjectNameInYingjieLayout() {
        String layout = yingjieA4RemarkLayout();

        assertThat(layout)
                .contains("\"text\": \"项目名称：\"")
                .doesNotContain("\"text\": \"工程名称：\"");
        assertThat(layout)
                .contains("\"projectName\"")
                .contains("\"minimumFontSize\": 7")
                .contains("\"vertical\": \"middle\"");
    }

    @Test
    void shouldCheckRecordAccessBeforeLoadingPrintData() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AttachmentRecordAccessService accessService = mock(AttachmentRecordAccessService.class);
        PrintScriptService service = printScriptService(repository("sales-order"), jdbc, accessService);
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无数据权限"))
                .when(accessService).assertRecordAccessible(org.mockito.ArgumentMatchers.any(), eq("sales-order"), eq("read"), eq(1L));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.generateFromRecord("1", "sales-order", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
        verify(jdbc, never()).queryForMap(anyString(), eq(1L));
    }

    @Test
    void shouldRejectDisabledTemplateBeforeLoadingPrintData() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PrintScriptService service = printScriptService(repository("sales-order", "COORD", "LODOP.PRINT_INIT('模板');", "DISABLED"), jdbc);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.generateFromRecord("1", "sales-order", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("打印模板已禁用");
        verify(jdbc, never()).queryForMap(anyString(), eq(1L));
    }

    private PrintTemplateRepository repository(String billType) {
        return repository(billType, "COORD", "LODOP.PRINT_INIT('模板');");
    }

    private PrintScriptService printScriptService(PrintTemplateRepository repository, JdbcTemplate jdbc) {
        return printScriptService(repository, jdbc, mock(AttachmentRecordAccessService.class));
    }

    private PrintScriptService printScriptService(
            PrintTemplateRepository repository,
            JdbcTemplate jdbc,
            AttachmentRecordAccessService accessService
    ) {
        return new PrintScriptService(repository, jdbc, new PrintLayoutLodopRenderer(new ObjectMapper()), accessService);
    }

    private PrintTemplateRepository repository(String billType, String templateType, String templateHtml) {
        return repository(billType, templateType, templateHtml, "ACTIVE");
    }

    private PrintTemplateRepository repositoryWithTemplateName(String billType, String templateName, String templateType, String templateHtml) {
        PrintTemplate template = new PrintTemplate();
        template.setId(1L);
        template.setBillType(billType);
        template.setTemplateName(templateName);
        template.setTemplateHtml(templateHtml);
        template.setTemplateType(templateType);
        template.setStatus("ACTIVE");

        PrintTemplateRepository repository = mock(PrintTemplateRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(template));
        return repository;
    }

    private PrintTemplateRepository repository(String billType, String templateType, String templateHtml, String status) {
        PrintTemplate template = new PrintTemplate();
        template.setId(1L);
        template.setBillType(billType);
        template.setTemplateName("模板");
        template.setTemplateHtml(templateHtml);
        template.setTemplateType(templateType);
        template.setStatus(status);

        PrintTemplateRepository repository = mock(PrintTemplateRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(template));
        return repository;
    }

    private String yingjieA4RemarkLayout() {
        try (InputStream input = getClass()
                .getClassLoader()
                .getResourceAsStream("print-forms/yingjie-a4-remark.layout.json")) {
            if (input == null) {
                throw new AssertionError("缺少 PDF JSON 模板资源");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new AssertionError("读取 PDF JSON 模板资源失败", ex);
        }
    }
}
