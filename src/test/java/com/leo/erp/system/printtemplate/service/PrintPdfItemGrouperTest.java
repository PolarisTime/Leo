package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class PrintPdfItemGrouperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PrintPdfItemGrouper grouper = new PrintPdfItemGrouper();

    @Test
    void groupsRowsByConfiguredFieldAndBuildsOrderedSummaryHeader() throws Exception {
        JsonNode table = objectMapper.readTree("""
                {
                  "grouping": {
                    "by": "sourceNo",
                    "sortBy": "billTime",
                    "sortDirection": "asc",
                    "headerType": "source",
                    "indexField": "groupIndex",
                    "headerFields": ["sourceNo", "billTime"],
                    "separator": true,
                    "totals": {
                      "totalQuantity": {"source": "quantity", "scale": 0},
                      "totalWeightTon": {"source": "weightTon", "scale": 3},
                      "totalAmount": {"source": "amount", "scale": 2}
                    }
                  }
                }
                """).path("grouping");

        List<Map<String, String>> items = List.of(
                row("SO-002", "2026-08-02", "3", "0.600", "1200.00"),
                row("SO-001", "2026-08-01", "2", "0.400", "800.00"),
                row("SO-002", "2026-08-02", "1", "0.200", "400.00")
        );

        List<Map<String, String>> grouped = grouper.group(items, table);

        assertThat(grouped).hasSize(6);
        assertThat(grouped.get(0))
                .containsEntry("isGroupHeader", "source")
                .containsEntry("groupIndex", "1")
                .containsEntry("sourceNo", "SO-001")
                .containsEntry("billTime", "2026-08-01")
                .containsEntry("totalQuantity", "2")
                .containsEntry("totalWeightTon", "0.400")
                .containsEntry("totalAmount", "800.00");
        assertThat(grouped.get(1))
                .containsEntry("groupIndex", "1")
                .containsEntry("sourceNo", "SO-001");
        assertThat(grouped.get(2)).containsEntry("isBlankRow", "true");
        assertThat(grouped.get(3))
                .containsEntry("groupIndex", "2")
                .containsEntry("sourceNo", "SO-002")
                .containsEntry("totalQuantity", "4")
                .containsEntry("totalWeightTon", "0.800")
                .containsEntry("totalAmount", "1600.00");
    }

    @Test
    void customerStatementTemplateDeclaresSalesOrderGroupingAndRequiredColumns() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "print-forms/default-customer-statement.layout.json")) {
            assertThat(stream).isNotNull();
            JsonNode root = objectMapper.readTree(stream);
            JsonNode grouping = root.path("table").path("grouping");
            assertThat(grouping.path("by").asText()).isEqualTo("sourceNo");
            assertThat(grouping.path("sortBy").asText()).isEqualTo("billTime");
            assertThat(grouping.path("sortDirection").asText()).isEqualTo("asc");
            JsonNode lineAligns = root.path("table").path("groupHeader").path("source").path("lineAligns");
            assertThat(lineAligns.isArray()).isTrue();
            assertThat(lineAligns.get(0).asText()).isEqualTo("left");
            assertThat(lineAligns.get(1).asText()).isEqualTo("right");

            List<String> labels = StreamSupport.stream(
                            root.path("table").path("columns").spliterator(), false)
                    .map(column -> column.path("label").asText())
                    .toList();
            assertThat(labels).containsExactly(
                    "分组序号", "销售单号", "交货日期", "品牌", "类别", "材质", "规格", "长度",
                    "数量", "数量单位", "件重/吨", "重量(吨)", "单价", "金额"
            );
        }
    }

    private static Map<String, String> row(
            String sourceNo,
            String billTime,
            String quantity,
            String weightTon,
            String amount
    ) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("sourceNo", sourceNo);
        row.put("billTime", billTime);
        row.put("quantity", quantity);
        row.put("weightTon", weightTon);
        row.put("amount", amount);
        return row;
    }
}
