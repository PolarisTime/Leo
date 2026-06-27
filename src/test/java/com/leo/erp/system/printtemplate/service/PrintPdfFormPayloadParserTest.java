package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrintPdfFormPayloadParserTest {

    private final PrintPdfFormPayloadParser parser = new PrintPdfFormPayloadParser(
            new PrintPdfFormTemplateValidator(new ObjectMapper())
    );

    @Test
    void shouldRejectNonPdfFormPayload() {
        assertThatThrownBy(() -> parser.parse(Map.of("templateType", "HTML")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是 PDF_FORM");
    }

    @Test
    void shouldParseTemplateDataAndItems() {
        PrintPdfFormPayload payload = parser.parse(Map.of(
                "templateType", "PDF_FORM",
                "templateHtml", "{\"static\":[]}",
                "data", Map.of("billNo", "SO-001"),
                "items", List.of(Map.of("material", "HRB400E"))
        ));

        assertThat(payload.root().path("static").isArray()).isTrue();
        assertThat(payload.data()).containsEntry("billNo", "SO-001");
        assertThat(payload.items()).containsExactly(Map.of("material", "HRB400E"));
    }

    @Test
    void shouldIgnoreNonStringPayloadFields() {
        Map<String, Object> data = new HashMap<>();
        data.put("billNo", "SO-001");
        data.put("totalWeight", BigDecimal.ONE);
        Map<String, Object> payload = new HashMap<>();
        payload.put("templateType", "PDF_FORM");
        payload.put("templateHtml", "{\"static\":[]}");
        payload.put("data", data);
        payload.put("items", List.of(
                Map.of("material", "HRB400E", "quantity", BigDecimal.ONE),
                "invalid",
                Map.of("spec", "Ф18")
        ));

        PrintPdfFormPayload result = parser.parse(payload);

        assertThat(result.data()).containsExactly(Map.entry("billNo", "SO-001"));
        assertThat(result.items()).containsExactly(
                Map.of("material", "HRB400E"),
                Map.of("spec", "Ф18")
        );
    }
}
