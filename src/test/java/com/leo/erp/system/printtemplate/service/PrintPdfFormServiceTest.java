package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PrintPdfFormServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generateFromPayloadShouldThrowWhenTemplateTypeNotPdfForm() {
        PrintScriptService printScriptService = mock(PrintScriptService.class);
        PrintPdfFormService service = new PrintPdfFormService(
                printScriptService,
                objectMapper,
                List.of(new YingjieA4RemarkPdfFormRenderer())
        );

        Map<String, Object> payload = Map.of("templateType", "HTML");

        assertThatThrownBy(() -> service.generateFromPayload(payload))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void generateFromPayloadShouldThrowWhenTemplateConfigInvalid() {
        PrintScriptService printScriptService = mock(PrintScriptService.class);
        PrintPdfFormService service = new PrintPdfFormService(
                printScriptService,
                objectMapper,
                List.of(new YingjieA4RemarkPdfFormRenderer())
        );

        Map<String, Object> payload = Map.of(
                "templateType", "PDF_FORM",
                "templateHtml", "not-valid-json",
                "data", Collections.emptyMap(),
                "items", Collections.emptyList()
        );

        assertThatThrownBy(() -> service.generateFromPayload(payload))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void generateFromPayloadShouldThrowWhenFormTypeUnsupported() {
        PrintScriptService printScriptService = mock(PrintScriptService.class);
        PrintPdfFormService service = new PrintPdfFormService(
                printScriptService,
                objectMapper,
                List.of(new YingjieA4RemarkPdfFormRenderer())
        );

        Map<String, Object> payload = Map.of(
                "templateType", "PDF_FORM",
                "templateHtml", "{\"form\":\"UNKNOWN\",\"template\":\"test.pdf\"}",
                "data", Collections.emptyMap(),
                "items", Collections.emptyList()
        );

        assertThatThrownBy(() -> service.generateFromPayload(payload))
                .isInstanceOf(BusinessException.class);
    }
}
