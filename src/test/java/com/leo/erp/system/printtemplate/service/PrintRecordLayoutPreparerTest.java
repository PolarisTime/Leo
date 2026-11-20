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

    /**
     * 超长项目名称必须折行：A5 套打的项目名框宽 420px，12pt 中文每行约 26 字。
     *
     * <p>回归背景（2026-11-17 用户截图）：adaptiveFields 的 rules 为空时永远取默认档
     * （高度 20 / 字号 12 / WordBreak 0），30 个汉字的项目名只画一行、右侧被裁掉，
     * 打印出来是「…年产160万套智能家居用品建」这种半截。宽度 60（30 汉字）应落到
     * 「两行、字号 12、WordBreak 2」这一档。</p>
     */
    @Test
    void prepare_shouldWrapLongProjectNameIntoTwoLines() {
        Map<String, String> data = new HashMap<>();
        data.put("projectName", "海宁市潮圣智能家居有限公司年产160万套智能家居用品建设项目");

        preparer.prepare("sales-order", "A5销售单", null, data, new ArrayList<>());

        assertThat(data.get("projectNameWordBreak")).isEqualTo("2");
        assertThat(data.get("projectNameHeight")).isEqualTo("40");
        assertThat(data.get("projectNameFontSize")).isEqualTo("12");
        assertThat(data.get("projectNameMultiline")).isEqualTo("1");
    }

    @Test
    void prepare_shouldShrinkFontWhenProjectNameExceedsTwoLinesAt12() {
        Map<String, String> data = new HashMap<>();
        // 61 个汉字 = displayWidth 119：两行 12pt（容量 104）与两行 11pt（容量 112）都放不下，
        // 落到 10 号档（两行容量 124）
        data.put("projectName", "海宁市潮圣智能家居有限公司年产160万套智能家居用品建设项目（一期）施工总承包工程二标段项目经理部综合办公室及附属配套工程");

        preparer.prepare("sales-order", "A5销售单", null, data, new ArrayList<>());

        assertThat(data.get("projectNameWordBreak")).isEqualTo("2");
        assertThat(data.get("projectNameHeight")).isEqualTo("40");
        assertThat(data.get("projectNameFontSize")).isEqualTo("10");
    }

    /**
     * 2026-11-20 用户定稿：项目名称**统一以「两行」为基准**排版。
     *
     * <p>短名称仍然只画一行（内容放得下），但框高、折行开关与长名称一致（40 / WordBreak 2），
     * 这样单行名称与两行名称的第一行落在同一基线上，不会因档位不同而上下跳；水平方向由模板
     * 的 {@code SET_PRINT_STYLEA(0,"Alignment",2)} 居中。</p>
     */
    @Test
    void prepare_shouldUseTwoLineBaselineEvenForShortProjectName() {
        Map<String, String> data = new HashMap<>();
        data.put("projectName", "云潮筝鸣府");

        preparer.prepare("sales-order", "A5销售单", null, data, new ArrayList<>());

        assertThat(data.get("projectNameWordBreak")).isEqualTo("2");
        assertThat(data.get("projectNameHeight")).isEqualTo("40");
        assertThat(data.get("projectNameFontSize")).isEqualTo("12");
        assertThat(data.get("projectNameMultiline")).isEqualTo("1");
    }

    /**
     * 自适应规则的顺序是契约：读规则是「首个 displayWidth > 阈值」即命中，
     * 必须按 minDisplayWidth **降序**排列。升序会让所有长名称停在第 1 档（宽度永远 > 最小阈值），
     * 正是 2026-11-17 那次「长项目名不折行」的成因之一。
     */
    @Test
    void adaptiveFieldRules_mustBeOrderedByDescendingWidth() {
        PrintRuntimeProperties properties =
                new PrintRuntimeProperties(new com.fasterxml.jackson.databind.ObjectMapper());
        for (com.fasterxml.jackson.databind.JsonNode field
                : properties.childObjects(properties.topLevelFields().path("adaptiveFields"))) {
            int previous = Integer.MAX_VALUE;
            for (com.fasterxml.jackson.databind.JsonNode rule : properties.childObjects(field.path("rules"))) {
                int width = properties.integer(rule, "minDisplayWidth", Integer.MAX_VALUE);
                assertThat(width)
                        .as("adaptiveFields[%s] 的 rules 必须按 minDisplayWidth 降序",
                                field.path("sourceField").asText())
                        .isLessThan(previous);
                previous = width;
            }
        }
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

    /** A5 套打：附加费用紧跟在货物明细后面的第一条空行（表头 161 + 1 × 行高 41 = 202）。 */
    @Test
    void prepare_shouldPlaceChargeRowRightAfterLastItem() {
        Map<String, String> data = new HashMap<>();
        List<Map<String, String>> items = new ArrayList<>();
        items.add(item("1", "11.988"));
        items.add(chargeRow("运费", "1000.00"));

        List<Map<String, String>> result = preparer.prepare("sales-order", "A5销售单", "{{rowTop}}", data, items);

        assertThat(result).hasSize(2);
        assertThat(result.get(1))
                .containsEntry(PrintChargeRowPreparer.CHARGE_ROW_FIELD, "true")
                .containsEntry("rowTop", "202");
        // 货物行 + 费用行共占 2 行，之后的第一个空行在 243
        assertThat(data.get("hasEmptyRows")).isEqualTo("true");
        assertThat(data.get("emptyRowTop")).isEqualTo("243");
    }

    /** 多条费用按顺序占用后续空行，行号连续。 */
    @Test
    void prepare_shouldPlaceChargeRowsInFollowingBlankRows() {
        List<Map<String, String>> items = new ArrayList<>();
        items.add(item("1", "11.988"));
        items.add(item("2", "17.936"));
        items.add(chargeRow("运费", "200.00"));
        items.add(chargeRow("吊装费", "50.00"));

        List<Map<String, String>> result = preparer.prepare(
                "sales-order", "A5销售单", "{{rowTop}}", new HashMap<>(), items);

        assertThat(result).hasSize(4);
        assertThat(result.get(2))
                .containsEntry("chargeName", "运费")
                .containsEntry("rowTop", "243");
        assertThat(result.get(3))
                .containsEntry("chargeName", "吊装费")
                .containsEntry("rowTop", "284");
    }

    /** 明细占满整页时费用行自然落到下一页首行（既有分页逻辑）。 */
    @Test
    void prepare_shouldPageBreakChargeRowWhenPageIsFull() {
        List<Map<String, String>> items = new ArrayList<>();
        for (int index = 1; index <= 7; index += 1) {
            items.add(item(String.valueOf(index), "11.988"));
        }
        items.add(chargeRow("运费", "1000.00"));

        List<Map<String, String>> result = preparer.prepare(
                "sales-order", "A5销售单", "{{rowTop}}", new HashMap<>(), items);

        assertThat(result).hasSize(8);
        assertThat(result.get(7))
                .containsEntry("needsNewPage", "true")
                .containsEntry("rowTop", "161");
    }

    private Map<String, String> item(String quantity, String weightTon) {
        Map<String, String> item = new HashMap<>();
        item.put("quantity", quantity);
        item.put("weightTon", weightTon);
        return item;
    }

    private Map<String, String> chargeRow(String name, String amount) {
        Map<String, String> item = new HashMap<>();
        item.put(PrintChargeRowPreparer.CHARGE_ROW_FIELD, "true");
        item.put("chargeName", name);
        item.put("chargeAmount", amount);
        return item;
    }
}
