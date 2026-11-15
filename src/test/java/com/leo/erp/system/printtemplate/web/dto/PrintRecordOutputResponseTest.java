package com.leo.erp.system.printtemplate.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrintRecordOutputResponseTest {

    @Test
    void includesNullableFieldsForConsistentNullPolicy() throws Exception {
        PrintRecordOutputResponse response = new PrintRecordOutputResponse(
                "PDF",
                "销售订单 PDF",
                "PDF_FORM",
                "application/pdf",
                "SO-001.pdf",
                "JVBERi0=",
                "SO-001",
                123L,
                "sales-order",
                null,
                null,
                null
        );

        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json)
                .contains("\"kind\":\"PDF\"")
                .contains("\"pdfBase64\":\"JVBERi0=\"")
                // 全局默认包含 null；该 DTO 不再局部启用 NON_NULL，避免同一个 API 两套空值策略。
                .contains("\"templateHtml\":null")
                .contains("\"data\":null")
                .contains("\"items\":null");
    }

    @Test
    void keepsDynamicStringMapsAsIs() throws Exception {
        PrintRecordOutputResponse response = new PrintRecordOutputResponse(
                "LODOP_SCRIPT",
                null,
                "COORD",
                null,
                null,
                null,
                "SO-001",
                123L,
                "sales-order",
                "<html></html>",
                java.util.Map.of("customerName", "客户A"),
                java.util.List.of(java.util.Map.of("material", "HRB400E"))
        );

        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json)
                .contains("\"data\":{\"customerName\":\"客户A\"}")
                .contains("\"items\":[{\"material\":\"HRB400E\"}]");
    }
}
