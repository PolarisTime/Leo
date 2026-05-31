package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrintScriptServiceTest {

    @Test
    void shouldEnrichSalesOutboundPrintDataWithCurrentFields() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PrintScriptService service = new PrintScriptService(repository("sales-outbound"), jdbc);

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
        PrintScriptService service = new PrintScriptService(repository("customer-statement"), jdbc);

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
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
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
        PrintScriptService service = new PrintScriptService(repository("freight-statement"), jdbc);

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
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
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
    void shouldFillAndFlattenYingjieA4RemarkPdfForm() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PrintScriptService scriptService = new PrintScriptService(repository(
                "sales-order",
                "PDF_FORM",
                "{\"form\":\"YINGJIE_A4_REMARK\",\"template\":\"print-forms/yingjie-a4-remark.pdf\"}"
        ), jdbc);
        PrintPdfFormService pdfFormService = new PrintPdfFormService(scriptService, new ObjectMapper());

        when(jdbc.queryForMap(anyString(), eq(1L))).thenReturn(Map.of(
                "id", 1L,
                "order_no", "SO-001",
                "customer_name", "客户A",
                "project_name", "项目A",
                "delivery_date", Date.valueOf("2026-05-31"),
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
        when(jdbc.queryForList(anyString(), eq(String.class), eq("项目A"))).thenReturn(List.of("项目地址A"));

        byte[] pdf = pdfFormService.generateFromRecord("1", "sales-order", 1L);

        assertThat(pdf).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(pdf.length).isGreaterThan(1000);
        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            assertThat(PdfAcroForm.getAcroForm(document, false)).isNull();
            String text = PdfTextExtractor.getTextFromPage(document.getFirstPage());
            assertThat(text).contains("客户A", "SO-001", "抚顺新钢", "螺纹钢");
        }
    }

    private PrintTemplateRepository repository(String billType) {
        return repository(billType, "COORD", "LODOP.PRINT_INIT('模板');");
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
}
