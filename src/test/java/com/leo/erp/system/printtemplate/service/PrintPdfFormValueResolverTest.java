package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrintPdfFormValueResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PrintPdfFormValueResolver resolver = new PrintPdfFormValueResolver(
            new PrintRuntimeProperties(objectMapper)
    );

    @Test
    void appliesConfiguredFormatToTableCell() throws Exception {
        var column = objectMapper.readTree("""
                {"key": "billTime", "format": "chineseDate"}
                """);

        assertThat(resolver.itemValue(Map.of("billTime", "2026-08-25"), column))
                .isEqualTo("2026年08月25日");
    }

    @Test
    void formatsMonthDayForNarrowDetailDateColumn() throws Exception {
        JsonNode column = objectMapper.readTree("""
                {"key": "billTime", "format": "monthDay"}
                """);

        assertThat(resolver.itemValue(Map.of("billTime", "2026-08-25"), column))
                .isEqualTo("08月25日");
        assertThat(resolver.itemValue(Map.of("billTime", "2026年10月9日"), column))
                .isEqualTo("10月09日");
        assertThat(resolver.itemValue(Map.of("billTime", "2026-08-25T10:30:00"), column))
                .isEqualTo("08月25日");
        assertThat(resolver.itemValue(Map.of("billTime", ""), column)).isEmpty();
    }

    @Test
    void rendersConfiguredFieldTemplateWithoutBusinessSpecificCode() throws Exception {
        var field = objectMapper.readTree("""
                {
                  "template": "${startDate} 至 ${endDate}",
                  "formats": {
                    "startDate": "chineseDate",
                    "endDate": "chineseDate"
                  }
                }
                """);

        assertThat(resolver.fieldValue(
                Map.of("startDate", "2026-08-01", "endDate", "2026-08-25"),
                field,
                "period"
        )).isEqualTo("2026年08月01日 至 2026年08月25日");
    }
}
