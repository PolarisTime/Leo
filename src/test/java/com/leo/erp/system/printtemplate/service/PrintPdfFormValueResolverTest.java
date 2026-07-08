package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrintPdfFormValueResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PrintPdfFormValueResolver resolver = new PrintPdfFormValueResolver(
            new PrintRuntimeProperties(objectMapper)
    );

    @Test
    void shouldResolveFieldValueFromSourceAndFormatChineseDate() throws Exception {
        var fieldConfig = objectMapper.readTree("{\"source\":\"deliveryDate\",\"format\":\"chineseDate\"}");

        String value = resolver.fieldValue(Map.of("deliveryDate", "2026-06-14T08:30:00"), fieldConfig, "fallback");

        assertThat(value).isEqualTo("2026年06月14日");
    }

    @Test
    void shouldHandleChineseDateBoundaryValues() throws Exception {
        var fieldConfig = objectMapper.readTree("{\"source\":\"deliveryDate\",\"format\":\"chineseDate\"}");
        Map<String, String> nullDate = new HashMap<>();
        nullDate.put("deliveryDate", null);

        assertThat(resolver.fieldValue(nullDate, fieldConfig, "fallback")).isEmpty();
        assertThat(resolver.fieldValue(Map.of("deliveryDate", " "), fieldConfig, "fallback")).isEmpty();
        assertThat(resolver.fieldValue(Map.of("deliveryDate", "2026年6月4日 08:30"), fieldConfig, "fallback"))
                .isEqualTo("2026年6月4日");
        assertThat(resolver.fieldValue(Map.of("deliveryDate", "2026/06"), fieldConfig, "fallback"))
                .isEqualTo("2026/06");
        assertThat(resolver.fieldValue(Map.of("deliveryDate", "2026-6-4"), fieldConfig, "fallback"))
                .isEqualTo("2026年06月04日");
    }

    @Test
    void shouldUseFormatFallbackWhenFormatNodeIsNull() throws Exception {
        var fieldConfig = objectMapper.readTree("{\"source\":\"deliveryDate\",\"format\":null}");

        String value = resolver.fieldValue(Map.of("deliveryDate", "2026-6-4"), fieldConfig, "fallback");

        assertThat(value).isEqualTo("2026-6-4");
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
    void shouldCompactNullItemValueToBlank() throws Exception {
        var column = objectMapper.readTree("{\"source\":\"materialCode\",\"normalize\":\"compactAscii\"}");
        Map<String, String> item = new HashMap<>();
        item.put("materialCode", null);

        String value = resolver.itemValue(item, column);

        assertThat(value).isEmpty();
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

    @Test
    void shouldBuildPurchaseChargeSummaryWithPayableDirectionOnly() {
        Map<String, String> variables = resolver.summaryVariables(
                Map.of("moduleKey", "purchase-order", "totalAmount", "100.00"),
                List.of(
                        Map.of("quantity", "2", "weightTon", "2.000", "amount", "100.00"),
                        Map.of("quantity", "0", "weightTon", "0", "amount", "0")
                ),
                Map.of("chargeItems", List.of(
                        Map.of("chargeDirection", "PAYABLE", "amount", "12.34", "billable", "true"),
                        Map.of("chargeDirection", "RECEIVABLE", "amount", "1.11", "billable", "true"),
                        Map.of("chargeDirection", "PAYABLE", "amount", "9.99", "billable", "false"),
                        Map.of("chargeDirection", "INTERNAL", "amount", "50.00", "billable", "true")
                ))
        );

        assertThat(variables)
                .containsEntry("totalAmount", "100.00")
                .containsEntry("totalWeight", "2.000")
                .containsEntry("totalChargeAmount", "12.34")
                .containsEntry("payableAmount", "112.34")
                .containsEntry("receivableAmount", "100.00");
    }

    @Test
    void shouldBuildSalesChargeSummaryWithReceivableDirectionOnly() {
        Map<String, String> variables = resolver.summaryVariables(
                Map.of("moduleKey", "sales-order", "totalAmount", "100.00"),
                List.of(Map.of("quantity", "2", "weightTon", "2.000", "amount", "100.00")),
                Map.of("chargeItems", List.of(
                        Map.of("chargeDirection", "PAYABLE", "amount", "12.34", "billable", "true"),
                        Map.of("chargeDirection", "RECEIVABLE", "amount", "1.11", "billable", "true"),
                        Map.of("chargeDirection", "RECEIVABLE", "amount", "9.99", "billable", "false"),
                        Map.of("chargeDirection", "INTERNAL", "amount", "50.00", "billable", "true")
                ))
        );

        assertThat(variables)
                .containsEntry("totalAmount", "100.00")
                .containsEntry("totalWeight", "2.000")
                .containsEntry("totalChargeAmount", "1.11")
                .containsEntry("payableAmount", "100.00")
                .containsEntry("receivableAmount", "101.11");
    }

    @Test
    void shouldTreatNullNumericValuesAsZeroInSummary() {
        Map<String, String> item = new HashMap<>();
        item.put("quantity", null);
        item.put("weightTon", null);
        Map<String, String> blankItem = Map.of("quantity", " ", "weightTon", " ");

        Map<String, String> variables = resolver.summaryVariables(Map.of(), List.of(item, blankItem));

        assertThat(variables).containsEntry("totalQuantity", "0");
        assertThat(variables).containsEntry("totalWeight", "0");
    }

    @Test
    void shouldReturnBlankForNullOrEmptyTemplate() {
        assertThat(resolver.applyTemplate(null, Map.of())).isEmpty();
        assertThat(resolver.applyTemplate("", Map.of())).isEmpty();
    }
}
