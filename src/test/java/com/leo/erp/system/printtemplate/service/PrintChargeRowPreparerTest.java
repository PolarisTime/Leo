package com.leo.erp.system.printtemplate.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 附加费用行只在模板声明了 {@code {{chargeName}}} 时才追加，
 * 避免影响 A4 / PDF 等不打印逐行费用的模板。
 */
class PrintChargeRowPreparerTest {

    private static final String A5_TEMPLATE = "LODOP.ADD_PRINT_TEXT({{rowTop}},345,51,24,\"{{chargeName}}\");";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PrintChargeRowPreparer preparer = new PrintChargeRowPreparer(jdbc);

    @Test
    void appendChargeRows_shouldAppendChargeRowWhenTemplateDeclaresChargeName() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "charge_name", "运费",
                "amount", new BigDecimal("1000.00")
        )));

        List<Map<String, String>> rows = preparer.appendChargeRows(
                "sales-order", 7L, A5_TEMPLATE, List.of(Map.of("brand", "新梅鹿")));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("brand", "新梅鹿");
        assertThat(rows.get(1))
                .containsEntry(PrintChargeRowPreparer.CHARGE_ROW_FIELD, "true")
                .containsEntry("chargeName", "运费")
                .containsEntry("chargeAmount", "1000.00");
    }

    @Test
    void appendChargeRows_shouldKeepItemsUntouchedWhenTemplateHasNoChargePlaceholder() {
        List<Map<String, String>> items = List.of(Map.of("brand", "新梅鹿"));

        List<Map<String, String>> rows = preparer.appendChargeRows(
                "sales-order", 7L, "LODOP.ADD_PRINT_TEXT({{rowTop}},45,46,24,\"{{brand}}\");", items);

        assertThat(rows).isSameAs(items);
        verifyNoInteractions(jdbc);
    }

    @Test
    void appendChargeRows_shouldNotAppendWhenDocumentHasNoChargeItems() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        List<Map<String, String>> rows = preparer.appendChargeRows(
                "sales-order", 7L, A5_TEMPLATE, List.of(Map.of("brand", "新梅鹿")));

        assertThat(rows).hasSize(1);
    }
}
