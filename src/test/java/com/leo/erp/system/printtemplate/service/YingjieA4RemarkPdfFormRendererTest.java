package com.leo.erp.system.printtemplate.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class YingjieA4RemarkPdfFormRendererTest {

    private final YingjieA4RemarkPdfFormRenderer renderer = new YingjieA4RemarkPdfFormRenderer();

    @Test
    void buildFieldsShouldFormatDateAndTotalsForYingjieA4RemarkForm() {
        Map<String, String> fields = renderer.buildFields(
                Map.of(
                        "orderNo", "SO-001",
                        "deliveryDate", "2026-06-08T00:00",
                        "customerName", "客户A"
                ),
                List.of(
                        Map.of("quantity", "2", "weightTon", "2.345"),
                        Map.of("quantity", "3", "weightTon", "1.005")
                )
        );

        assertThat(fields.get("billNo")).isEqualTo("SO-001");
        assertThat(fields.get("billDate")).isEqualTo("2026年06月08日");
        assertThat(fields.get("totalSummary")).isEqualTo("合计件数：5    合计重量：3.350 吨");
    }
}
