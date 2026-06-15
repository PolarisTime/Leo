package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

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
}
