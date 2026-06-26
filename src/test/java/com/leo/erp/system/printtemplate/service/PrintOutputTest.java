package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrintOutputTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldOmitPdfFieldsForLodopScriptOutput() throws Exception {
        PrintOutput output = new PrintOutput(
                PrintOutput.Kind.LODOP_SCRIPT,
                "坐标模板",
                "COORD",
                null,
                null,
                null,
                "SO-001",
                1L,
                "sales-order",
                "LODOP.PRINT_INIT(\"test\");",
                Map.of("customerName", "客户甲"),
                List.of(Map.of("brand", "中杭"))
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(output));

        assertThat(json.get("kind").asText()).isEqualTo("LODOP_SCRIPT");
        assertThat(json.get("templateHtml").asText()).isEqualTo("LODOP.PRINT_INIT(\"test\");");
        assertThat(json.has("contentType")).isFalse();
        assertThat(json.has("fileName")).isFalse();
        assertThat(json.has("pdfBase64")).isFalse();
    }

    @Test
    void shouldOmitLodopFieldsForPdfOutput() throws Exception {
        PrintOutput output = new PrintOutput(
                PrintOutput.Kind.PDF,
                "PDF 模板",
                "PDF_FORM",
                "application/pdf",
                "print.pdf",
                "cGRmLWNvbnRlbnQ=",
                "SO-001",
                1L,
                "sales-order",
                null,
                null,
                null
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(output));

        assertThat(json.get("kind").asText()).isEqualTo("PDF");
        assertThat(json.get("pdfBase64").asText()).isEqualTo("cGRmLWNvbnRlbnQ=");
        assertThat(json.has("templateHtml")).isFalse();
        assertThat(json.has("data")).isFalse();
        assertThat(json.has("items")).isFalse();
    }

    @Test
    void shouldNormalizeInvalidPayloadFields() {
        PrintOutput output = PrintOutput.fromPayload(Map.of(
                "recordId", "not-a-number",
                "data", "not-a-map",
                "items", "not-a-list"
        ));

        assertThat(output.kind()).isEqualTo(PrintOutput.Kind.LODOP_SCRIPT);
        assertThat(output.templateType()).isEqualTo("COORD");
        assertThat(output.recordId()).isNull();
        assertThat(output.data()).isNull();
        assertThat(output.items()).isNull();
    }

    @Test
    void shouldNormalizeStringAndMissingRecordIds() {
        PrintOutput stringRecordIdOutput = PrintOutput.fromPayload(Map.of(
                "recordId", "42"
        ));
        PrintOutput missingRecordIdOutput = PrintOutput.fromPayload(Map.of());

        assertThat(stringRecordIdOutput.recordId()).isEqualTo(42L);
        assertThat(missingRecordIdOutput.recordId()).isNull();
    }

    @Test
    void shouldCopyOnlyStringEntriesFromPayloadMaps() {
        PrintOutput output = PrintOutput.fromPayload(Map.of(
                "data", Map.of(
                        "customerName", "客户甲",
                        "invalidNumber", 12
                ),
                "items", List.of(
                        Map.of("brand", "中杭", "ignored", 1),
                        "not-a-map"
                )
        ));

        assertThat(output.data())
                .containsExactlyEntriesOf(Map.of("customerName", "客户甲"));
        assertThat(output.items()).containsExactly(Map.of("brand", "中杭"));
    }
}
