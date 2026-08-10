package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PrintPdfGroupPaginator 分组小计行极端情况测试。
 */
class PrintPdfGroupPaginatorTest {

    private static final String SOURCE = "source";
    private static final String PROJECT = "project";
    private static final String SUBTOTAL = "subtotal";

    private PrintPdfGroupPaginator paginator;
    private JsonNode tableConfig;

    @BeforeEach
    void setUp() throws Exception {
        paginator = new PrintPdfGroupPaginator(new PrintPdfDrawingSupport());
        ObjectMapper mapper = new ObjectMapper();
        tableConfig = mapper.readTree("""
                {
                  "rowHeight": 23,
                  "headerHeight": 26,
                  "detailHeaderPlacement": "afterProjectHeader",
                  "repeatGroupContextOnContinuation": false,
                  "groupHeader": {
                    "height": 18,
                    "source": {"height": 34},
                    "project": {"height": 26},
                    "subtotal": {"height": 22}
                  }
                }
                """);
    }

    private Map<String, String> row(String groupType) {
        Map<String, String> item = new LinkedHashMap<>();
        if (groupType != null) {
            item.put("isGroupHeader", groupType);
        }
        return item;
    }

    private Map<String, String> detail() {
        return row(null);
    }

    @Test
    void isSubtotalRow_shouldOnlyMatchSubtotal() {
        assertThat(paginator.isSubtotalRow(row(SUBTOTAL))).isTrue();
        assertThat(paginator.isSubtotalRow(row(SOURCE))).isFalse();
        assertThat(paginator.isSubtotalRow(row(PROJECT))).isFalse();
        assertThat(paginator.isSubtotalRow(detail())).isFalse();
    }

    @Test
    void isGroupHeader_shouldExcludeSubtotal() {
        // 小计行是组尾汇总，不应触发组头整组换页逻辑
        assertThat(paginator.isGroupHeader(row(SUBTOTAL))).isFalse();
        assertThat(paginator.isGroupHeader(row(SOURCE))).isTrue();
        assertThat(paginator.isGroupHeader(row(PROJECT))).isTrue();
    }

    @Test
    void subtotalHeight_shouldReadConfiguredHeight() {
        assertThat(paginator.subtotalHeight(tableConfig)).isEqualTo(22f);
    }

    @Test
    void subtotalHeight_shouldFallbackWhenMissing() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode noSubtotal = mapper.readTree("{\"groupHeader\":{\"height\":18}}");
        assertThat(paginator.subtotalHeight(noSubtotal)).isEqualTo(18f);
    }

    @Test
    void shouldStartGroupOnNextPage_shouldIncludeSubtotalHeight() {
        // 组头 + 明细 + 小计总高 131，页内剩余不足时应当整组换页（含小计）
        List<Map<String, String>> items = new ArrayList<>();
        items.add(row(SOURCE));
        items.add(row(PROJECT));
        items.add(detail());
        items.add(row(SUBTOTAL));

        boolean shouldBreak = paginator.shouldStartGroupOnNextPage(
                items, 0, 100f, 200f, 28f, tableConfig, 26f, 23f, true);
        assertThat(shouldBreak).isTrue();
    }

    @Test
    void shouldStartGroupOnNextPage_shouldStayWhenGroupFits() {
        List<Map<String, String>> items = new ArrayList<>();
        items.add(row(SOURCE));
        items.add(row(PROJECT));
        items.add(detail());
        items.add(row(SUBTOTAL));

        boolean shouldBreak = paginator.shouldStartGroupOnNextPage(
                items, 0, 100f, 320f, 28f, tableConfig, 26f, 23f, true);
        assertThat(shouldBreak).isFalse();
    }

    @Test
    void shouldStartGroupOnNextPage_shouldIgnorePlainDetailRows() {
        List<Map<String, String>> items = new ArrayList<>();
        items.add(detail());
        boolean shouldBreak = paginator.shouldStartGroupOnNextPage(
                items, 0, 400f, 200f, 28f, tableConfig, 26f, 23f, true);
        assertThat(shouldBreak).isFalse();
    }
}
