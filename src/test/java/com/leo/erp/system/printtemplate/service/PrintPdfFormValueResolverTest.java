package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrintPdfFormValueResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PrintPdfFormValueResolver resolver = new PrintPdfFormValueResolver();

    @Test
    void shouldResolveFieldValueFromSourceAndFormatChineseDate() throws Exception {
        var fieldConfig = objectMapper.readTree("{\"source\":\"deliveryDate\",\"format\":\"chineseDate\"}");

        String value = resolver.fieldValue(Map.of("deliveryDate", "2026-06-14T08:30:00"), fieldConfig, "fallback");

        assertThat(value).isEqualTo("2026年06月14日");
    }

    @Test
    void shouldResolveFirstNonBlankItemSourceAndCompactAsciiToken() throws Exception {
        var column = objectMapper.readTree("{\"source\":[\"material\",\"materialCode\"],\"normalize\":\"compactAscii\"}");

        String value = resolver.itemValue(
                Map.of("material", " ", "materialCode", "HRB 400 E"),
                column
        );

        assertThat(value).isEqualTo("HRB400E");
    }

    @Test
    void shouldBuildSummaryVariablesAndDefaultBlankRemark() {
        Map<String, String> variables = resolver.summaryVariables(
                Map.of("remark", ""),
                List.of(
                        Map.of("quantity", "2", "weightTon", "1.2344"),
                        Map.of("quantity", "3", "weightTon", "2.0004"),
                        Map.of("quantity", "-", "weightTon", "invalid")
                )
        );

        assertThat(variables).containsEntry("totalQuantity", "5");
        assertThat(variables).containsEntry("totalWeight", "3.235");
        assertThat(resolver.applyTemplate("合计${totalQuantity}件，备注${remark}", variables))
                .isEqualTo("合计5件，备注无");
    }
}
