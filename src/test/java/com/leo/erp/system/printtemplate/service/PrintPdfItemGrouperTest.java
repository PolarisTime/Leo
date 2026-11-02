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
    void customerStatementTemplateKeepsReferenceStyleInlineDateGrouping() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "print-forms/default-customer-statement.layout.json")) {
            assertThat(stream).isNotNull();
            JsonNode root = objectMapper.readTree(stream);
            JsonNode table = root.path("table");
            JsonNode grouping = table.path("grouping");
            assertThat(grouping.path("by").asText()).isEqualTo("billTime");
            assertThat(grouping.path("inline").asBoolean()).isTrue();
            assertThat(grouping.path("sortBy").asText()).isEqualTo("billTime");
            assertThat(grouping.path("sortDirection").asText()).isEqualTo("asc");
            assertThat(grouping.path("separator").asBoolean(false)).isFalse();
            assertThat(StreamSupport.stream(
                            grouping.path("blankRepeatedFields").spliterator(), false)
                    .map(JsonNode::asText)
                    .toList())
                    .contains("groupIndex", "billTime");

            List<String> labels = StreamSupport.stream(
                            table.path("columns").spliterator(), false)
                    .map(column -> column.path("label").asText())
                    .toList();
            assertThat(labels).containsExactly(
                    "序号", "进货日期", "产品名称", "产地", "牌号商标", "规格型号", "长度",
                    "件重/吨", "件数", "重量小计/吨", "单价", "金额", "备注"
            );

            assertThat(StreamSupport.stream(
                            grouping.path("continuationFillFields").spliterator(), false)
                    .map(JsonNode::asText)
                    .toList())
                    .contains("groupIndex", "billTime");
            assertThat(table.path("repeatHeaderPerPage").asBoolean()).isTrue();
            assertThat(StreamSupport.stream(
                            table.path("mergeColumns").spliterator(), false)
                    .map(JsonNode::asText)
                    .toList())
                    .as("序号/进货日期用真正的跨行合并单元格")
                    .contains("groupIndex", "billTime");
            assertThat(root.path("summary").path("grid").path("rows").size())
                    .as("底部累计信息块按参考样式两行")
                    .isEqualTo(2);
            assertThat(table.path("fillPage").asBoolean())
                    .as("连续表应尽量画满页面，只在末页预留汇总/条款高度")
                    .isTrue();
            assertThat(table.path("maxRowsPerPage").asInt()).isGreaterThanOrEqualTo(40);
            assertThat(table.path("repeatHeaderPerSourceGroup").asBoolean(false)).isFalse();
        }
    }

    @Test
    void inlineGroupingKeepsGroupValuesOnFirstRowAndBlanksRepeats() throws Exception {
        JsonNode grouping = objectMapper.readTree("""
                {
                  "by": "billTime",
                  "inline": true,
                  "indexField": "groupIndex",
                  "blankRepeatedFields": ["groupIndex", "billTime"],
                  "sortBy": "billTime",
                  "sortDirection": "asc"
                }
                """);

        List<Map<String, String>> items = List.of(
                row("SO-A", "2026-08-25", "2", "0.400", "800.00"),
                row("SO-B", "2026-08-20", "3", "0.600", "1200.00"),
                row("SO-C", "2026-08-20", "4", "0.800", "1600.00")
        );

        List<Map<String, String>> grouped = grouper.group(items, grouping);

        // 不插入分组头行：行数与原明细一致，日期升序后同组聚在一起
        assertThat(grouped).hasSize(3);
        assertThat(grouped.get(0))
                .containsEntry("groupIndex", "1")
                .containsEntry("billTime", "2026-08-20");
        // 同组第二行留空，视觉上等价于合并单元格
        assertThat(grouped.get(1))
                .containsEntry("groupIndex", "")
                .containsEntry("billTime", "");
        assertThat(grouped.get(2))
                .containsEntry("groupIndex", "2")
                .containsEntry("billTime", "2026-08-25");
        assertThat(grouped).noneMatch(item -> item.containsKey("isGroupHeader"));
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
