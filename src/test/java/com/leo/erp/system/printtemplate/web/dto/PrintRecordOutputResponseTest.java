package com.leo.erp.system.printtemplate.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrintRecordOutputResponseTest {

    @Test
    void omitsUnusedNullableFieldsFromPdfOutput() throws Exception {
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
                .doesNotContain("\"templateHtml\"")
                .doesNotContain("\"data\"")
                .doesNotContain("\"items\"");
    }
}
