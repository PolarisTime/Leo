package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 套打数据准备测试：顶层 {@code printDate}/{@code printTime} 必须输出中文展示口径，
 * 且 {@code dateYear}/{@code dateMonth}/{@code dateDay} 分列字段（sales-order-a5-* 模板依赖）保持纯数字。
 */
class PrintRecordLayoutPreparerTest {

    private PrintRecordLayoutPreparer preparer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        PrintRuntimeProperties runtimeProperties = new PrintRuntimeProperties(objectMapper);
        preparer = new PrintRecordLayoutPreparer(
                new PrintRecordFieldFormatter(runtimeProperties), runtimeProperties);
    }

    @Test
    void prepare_shouldWriteChinesePrintDateAndPrintTime() {
        Map<String, String> data = new HashMap<>();
        data.put("deliveryDate", "2026年08月01日");

        preparer.prepare("sales-order", null, null, data, new ArrayList<>());

        assertThat(data.get("printDate")).matches("\\d{4}年\\d{2}月\\d{2}日");
        assertThat(data.get("printTime")).matches("\\d{4}年\\d{2}月\\d{2}日 \\d{2}:\\d{2}:\\d{2}");
        // 打印时间必须与打印日期同源，且不含 ISO 的 T 分隔符
        assertThat(data.get("printTime")).startsWith(data.get("printDate") + " ");
        assertThat(data.get("printTime")).doesNotContain("T");
    }

    @Test
    void prepare_shouldKeepExistingPrintDateUntouched() {
        Map<String, String> data = new HashMap<>();
        data.put("printDate", "2026年01月05日");
        data.put("printTime", "2026年01月05日 08:00:00");

        preparer.prepare("sales-order", null, null, data, new ArrayList<>());

        assertThat(data.get("printDate")).isEqualTo("2026年01月05日");
        assertThat(data.get("printTime")).isEqualTo("2026年01月05日 08:00:00");
    }

    @Test
    void prepare_shouldKeepDatePartsAsPlainNumbersForChineseSource() {
        Map<String, String> data = new HashMap<>();
        data.put("deliveryDate", "2026年8月1日");

        preparer.prepare("sales-order", null, null, data, new ArrayList<>());

        // sales-order-a5-* 模板依赖 dateYear/dateMonth/dateDay 分列字段，必须是纯数字且月/日补零
        assertThat(data.get("dateYear")).isEqualTo("2026");
        assertThat(data.get("dateMonth")).isEqualTo("08");
        assertThat(data.get("dateDay")).isEqualTo("01");
    }

    @Test
    void prepare_shouldKeepDatePartsAsPlainNumbersForIsoSource() {
        Map<String, String> data = new HashMap<>();
        data.put("deliveryDate", "2026-08-01");

        preparer.prepare("sales-order", null, null, data, new ArrayList<>());

        assertThat(data.get("dateYear")).isEqualTo("2026");
        assertThat(data.get("dateMonth")).isEqualTo("08");
        assertThat(data.get("dateDay")).isEqualTo("01");
    }

    @Test
    void prepare_shouldShowDateOnlyWhenSourceTimestampFallsOnMidnight() {
        Map<String, String> data = new HashMap<>();
        Map<String, Object> row = new HashMap<>();
        row.put("bill_time", java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 9, 12, 0, 0)));
        PrintRecordFieldFormatter formatter = new PrintRecordFieldFormatter(new PrintRuntimeProperties(new ObjectMapper()));
        data.putAll(formatter.toCamelStringMap(row));

        preparer.prepare("freight-bill", null, null, data, new ArrayList<>());

        assertThat(data.get("billTime")).isEqualTo("2026年09月12日");
    }

    @Test
    void prepare_shouldReturnItemsUnchangedWhenTemplateHasNoLayoutFields() {
        List<Map<String, String>> items = new ArrayList<>();
        items.add(new HashMap<>(Map.of("quantity", "1", "weightTon", "2.5")));

        List<Map<String, String>> result = preparer.prepare("sales-order", null, null, new HashMap<>(), items);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("quantity", "1");
    }
}
