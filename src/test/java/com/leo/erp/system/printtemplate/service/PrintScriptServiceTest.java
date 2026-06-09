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
        PrintTemplate template = new PrintTemplate();
        template.setId(1L);
        template.setBillType(billType);
        template.setTemplateName("模板");
        template.setTemplateHtml(templateHtml);
        template.setTemplateType(templateType);

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
