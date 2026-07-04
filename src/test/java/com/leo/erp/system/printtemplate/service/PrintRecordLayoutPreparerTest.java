package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrintRecordLayoutPreparerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PrintRuntimeProperties runtimeProperties = new PrintRuntimeProperties(objectMapper);
    private final PrintRecordFieldFormatter formatter = new PrintRecordFieldFormatter(runtimeProperties);
    private final PrintRecordLayoutPreparer preparer = new PrintRecordLayoutPreparer(formatter, runtimeProperties);

    @Test
    void shouldLimitSalesOrderRowsAndFillLayoutTotals() {
        Map<String, String> data = new HashMap<>();
        data.put("orderDate", "2026-7-3T08:00:00");
        data.put("orderNo", "SO-001");
        data.put("projectName", "短项目");

        List<Map<String, String>> rows = preparer.prepare(
                "sales-order",
                "普通销售模板",
                "{{rowTop}} {{sumTop}} {{hasEmptyRows}}",
                data,
                List.of(
                        item("1", "1.1114", "10", "SO-001", "项目A"),
                        item("2", "2.2225", "20", "SO-001", "项目A"),
                        item("3", "3.3336", "30", "SO-001", "项目A"),
                        item("4", "4.4444", "40", "SO-001", "项目A"),
                        item("5", "5.5555", "50", "SO-001", "项目A"),
                        item("6", "6.6666", "60", "SO-001", "项目A"),
                        item("7", "7.7777", "70", "SO-001", "项目A"),
                        item("8", "8.8888", "80", "SO-001", "项目A"),
                        item("9", "9.9999", "90", "SO-001", "项目A")
                )
        );

        assertThat(rows).hasSize(8);
        assertThat(rows.get(0)).containsEntry("index", "1").containsEntry("rowTop", "161");
        assertThat(rows.get(7)).containsEntry("index", "8").containsEntry("rowTop", "448");
        assertThat(data)
                .containsEntry("billNo", "SO-001")
                .containsEntry("dateYear", "2026")
                .containsEntry("dateMonth", "07")
                .containsEntry("dateDay", "03")
                .containsEntry("sumTop", "453")
                .containsEntry("hasEmptyRows", "");
        assertThat(data.get("totalQuantity")).isEqualTo("36");
        assertThat(data.get("totalWeight")).isEqualTo("40.002");
        assertThat(data.get("totalAmount")).isEqualTo("360.00");
        assertThat(rows.get(0)).containsEntry("totalWeight", "40.002");
    }

    @Test
    void shouldStartNewPageForA5TemplateAfterConfiguredRows() {
        Map<String, String> data = new HashMap<>();
        data.put("projectName", "短项目");

        List<Map<String, String>> rows = preparer.prepare(
                "sales-order",
                "销售A5模板",
                "{{rowTop}} {{needsNewPage}}",
                data,
                List.of(
                        item("1", "1", "1", "SO-001", "项目A"),
                        item("1", "1", "1", "SO-001", "项目A"),
                        item("1", "1", "1", "SO-001", "项目A"),
                        item("1", "1", "1", "SO-001", "项目A"),
                        item("1", "1", "1", "SO-001", "项目A"),
                        item("1", "1", "1", "SO-001", "项目A"),
                        item("1", "1", "1", "SO-001", "项目A"),
                        item("1", "1", "1", "SO-001", "项目A")
                )
        );

        assertThat(rows.get(6)).doesNotContainKey("needsNewPage");
        assertThat(rows.get(7)).containsEntry("needsNewPage", "true").containsEntry("rowTop", "161");
    }

    @Test
    void shouldApplyRemarkA4RuleWhenTemplateNameContainsAllKeywords() {
        Map<String, String> data = new HashMap<>();
        data.put("projectName", "短项目");

        List<Map<String, String>> rows = preparer.prepare(
                "sales-order",
                "销售A4备注模板",
                "{{rowTop}} {{sumTop}}",
                data,
                items(11, "SO-001", "项目A")
        );

        assertThat(rows).hasSize(10);
        assertThat(rows.get(0)).containsEntry("index", "1").containsEntry("rowTop", "204");
        assertThat(rows.get(9)).containsEntry("index", "10").containsEntry("rowTop", "420");
        assertThat(data).containsEntry("sumTop", "444").containsEntry("hasEmptyRows", "");
    }

    @Test
    void shouldUseModuleRuleWhenTemplateNameIsNull() {
        Map<String, String> data = new HashMap<>();
        data.put("projectName", "短项目");

        List<Map<String, String>> rows = preparer.prepare(
                "sales-order",
                null,
                "{{rowTop}} {{sumTop}}",
                data,
                List.of(item("1", "1", "1", "SO-001", "项目A"))
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("rowTop", "161");
        assertThat(data).containsEntry("sumTop", "453");
    }

    @Test
    void shouldStartNewPageWhenCustomerStatementExceedsPageHeight() {
        Map<String, String> data = new HashMap<>();
        data.put("projectName", "短项目");

        List<Map<String, String>> rows = preparer.prepare(
                "customer-statement",
                "对账模板",
                "{{rowTop}} {{needsNewPage}}",
                data,
                items(46, "SO-001", "项目A")
        );

        assertThat(rows).hasSize(46);
        assertThat(rows.get(44)).doesNotContainKey("needsNewPage");
        assertThat(rows.get(45))
                .containsEntry("index", "46")
                .containsEntry("needsNewPage", "true")
                .containsEntry("rowTop", "20");
        assertThat(data).containsEntry("sumTop", "1130").containsEntry("emptyRowTop", "1050");
    }

    @Test
    void shouldGroupFreightBillItemsAndAddSeparatorRows() {
        Map<String, String> data = new HashMap<>();
        data.put("projectName", "这是一个非常长非常长非常长非常长非常长非常长非常长非常长非常长非常长的项目名称");

        List<Map<String, String>> rows = preparer.prepare(
                "freight-bill",
                "物流模板",
                "{{rowTop}} {{groupName}} {{isSeparator}}",
                data,
                List.of(
                        item("2", "1.5", "0", "FB-001", "项目A"),
                        item("3", "2.5", "0", "FB-001", "项目A"),
                        item("4", "3.5", "0", "FB-002", "项目B")
                )
        );

        assertThat(rows).hasSize(5);
        assertThat(rows.get(2))
                .containsEntry("isSeparator", "true")
                .containsEntry("groupName", "项目A")
                .containsEntry("rowTop", "204");
        assertThat(rows.get(4))
                .containsEntry("isSeparator", "true")
                .containsEntry("groupName", "项目B");
        assertThat(rows.get(0)).containsEntry("index", "1");
        assertThat(rows.get(2)).doesNotContainKey("index");
        assertThat(data)
                .containsEntry("sumTop", "264")
                .containsEntry("projectNameMultiline", "true")
                .containsEntry("projectNameWordBreak", "1");
    }

    @Test
    void shouldNotAddSeparatorRowForBlankFreightGroup() {
        Map<String, String> data = new HashMap<>();
        data.put("projectName", "短项目");

        List<Map<String, String>> rows = preparer.prepare(
                "freight-bill",
                "物流模板",
                "{{rowTop}} {{groupName}} {{isSeparator}}",
                data,
                List.of(
                        item("1", "1", "0", "FB-001", ""),
                        item("1", "1", "0", "FB-002", "项目B")
                )
        );

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsEntry("index", "1").doesNotContainKey("isSeparator");
        assertThat(rows.get(1)).containsEntry("index", "2").doesNotContainKey("isSeparator");
        assertThat(rows.get(2))
                .containsEntry("isSeparator", "true")
                .containsEntry("groupName", "项目B")
                .doesNotContainKey("index");
        assertThat(data).containsEntry("sumTop", "224");
    }

    @Test
    void shouldMarkSeparatorsWhenSourceNoChangesWithoutGrouping() {
        Map<String, String> data = new HashMap<>();

        List<Map<String, String>> rows = preparer.prepare(
                "customer-statement",
                "对账模板",
                "{{rowTop}} {{needsSeparator}}",
                data,
                List.of(
                        item("1", "1", "1", "SO-001", "项目A"),
                        item("1", "1", "1", "SO-002", "项目A"),
                        item("1", "1", "1", "", "项目A")
                )
        );

        assertThat(rows.get(0)).doesNotContainKey("needsSeparator");
        assertThat(rows.get(1)).containsEntry("needsSeparator", "true");
        assertThat(rows.get(2)).doesNotContainKey("needsSeparator");
        assertThat(data).containsEntry("hasEmptyRows", "true").containsEntry("emptyRowTop", "190");
    }

    @Test
    void shouldSkipLayoutWhenTemplateHasNoDynamicFields() {
        Map<String, String> data = new HashMap<>();
        List<Map<String, String>> rows = preparer.prepare(
                "sales-order",
                "普通销售模板",
                "静态模板",
                data,
                List.of(item("2", "6", "12", "SO-001", "项目A"))
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).doesNotContainKey("rowTop");
        assertThat(rows.get(0)).containsEntry("pieceWeightTon", "3.000");
        assertThat(data).doesNotContainKey("sumTop");
    }

    @Test
    void shouldSkipLayoutWhenTemplateHtmlIsNull() {
        Map<String, String> data = new HashMap<>();

        List<Map<String, String>> rows = preparer.prepare(
                "sales-order",
                "普通销售模板",
                null,
                data,
                List.of(item("2", "6", "12", "SO-001", "项目A"))
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).doesNotContainKey("rowTop");
        assertThat(rows.get(0)).containsEntry("pieceWeightTon", "3.000");
        assertThat(data).doesNotContainKey("sumTop");
    }

    @Test
    void shouldHandleNoLayoutWithMutableRawItemList() {
        Map<String, String> data = new HashMap<>();
        List<Map<String, String>> rawItems = new ArrayList<>();
        rawItems.add(new HashMap<>(item("2", "6", "12", "SO-001", "项目A")));

        List<Map<String, String>> rows = preparer.prepare(
                "sales-order",
                "普通销售模板",
                "静态模板",
                data,
                rawItems
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("pieceWeightTon", "3.000");
        assertThat(rawItems.get(0)).doesNotContainKey("pieceWeightTon");
    }

    @Test
    void shouldHandleEmptyDynamicFieldsInvalidDateAndBlankTotalOutput() throws Exception {
        PrintRecordLayoutPreparer customPreparer = preparerWithRuntime(
                """
                        {
                          "dateParts": {
                            "sourceKeys": ["blankDate", "badDate"],
                            "targets": {"year": "dateYear", "month": "dateMonth", "day": "dateDay"}
                          },
                          "decimalFields": [],
                          "headerBusinessNo": {"target": ""},
                          "adaptiveFields": []
                        }
                        """,
                """
                        {
                          "amount": {"itemField": "amount", "outputField": "", "format": "price"}
                        }
                        """,
                """
                        {
                          "dynamicFields": [],
                          "defaultLayout": {
                            "tableTop": 10,
                            "rowHeight": 5,
                            "maxRows": 2,
                            "pageHeight": 0,
                            "limitToMaxRows": false,
                            "pageResetTop": 10
                          },
                          "rules": []
                        }
                        """
        );
        Map<String, String> data = new HashMap<>();
        data.put("blankDate", " ");
        data.put("badDate", "2026");

        List<Map<String, String>> rows = customPreparer.prepare(
                "custom",
                "任意模板",
                "{{rowTop}} {{sumTop}}",
                data,
                List.of(item("1", "1", "9", "SO-001", "项目A"))
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).doesNotContainKeys("rowTop", "totalAmount", "sumTop");
        assertThat(data).doesNotContainKeys("dateYear", "dateMonth", "dateDay", "billNo", "sumTop", "totalAmount");
    }

    @Test
    void shouldIgnoreMissingDatePartsSource() throws Exception {
        PrintRecordLayoutPreparer customPreparer = preparerWithRuntime(
                """
                        {
                          "dateParts": {
                            "sourceKeys": ["missingDate"],
                            "targets": {"year": "dateYear", "month": "dateMonth", "day": "dateDay"}
                          },
                          "decimalFields": [],
                          "headerBusinessNo": {"target": ""},
                          "adaptiveFields": []
                        }
                        """,
                "{}",
                """
                        {
                          "dynamicFields": ["rowTop"],
                          "defaultLayout": {
                            "tableTop": 10,
                            "rowHeight": 5,
                            "maxRows": 2,
                            "pageHeight": 0,
                            "limitToMaxRows": false,
                            "pageResetTop": 10
                          },
                          "rules": []
                        }
                        """
        );
        Map<String, String> data = new HashMap<>();

        customPreparer.prepare("custom", "任意模板", "{{rowTop}}", data, List.of(item("1", "1", "1", "SO-001", "项目A")));

        assertThat(data).doesNotContainKeys("dateYear", "dateMonth", "dateDay");
    }

    @Test
    void shouldTreatIncompleteGroupingConfigAsPlainLayout() throws Exception {
        PrintRecordLayoutPreparer customPreparer = preparerWithRuntime(
                """
                        {
                          "dateParts": {"sourceKeys": []},
                          "decimalFields": [],
                          "headerBusinessNo": {"target": ""},
                          "adaptiveFields": []
                        }
                        """,
                "{}",
                """
                        {
                          "dynamicFields": ["rowTop"],
                          "defaultLayout": {
                            "tableTop": 10,
                            "rowHeight": 5,
                            "maxRows": 5,
                            "pageHeight": 0,
                            "limitToMaxRows": false,
                            "pageResetTop": 10
                          },
                          "rules": [
                            {
                              "when": {"module": "custom"},
                              "layout": {
                                "tableTop": 10,
                                "rowHeight": 5,
                                "maxRows": 5,
                                "pageHeight": 0,
                                "limitToMaxRows": false,
                                "pageResetTop": 10,
                                "groupByField": "projectName",
                                "separatorField": ""
                              }
                            }
                          ]
                        }
                        """
        );

        List<Map<String, String>> rows = customPreparer.prepare(
                "custom",
                "普通模板",
                "{{rowTop}}",
                new HashMap<>(),
                List.of(
                        item("1", "1", "1", "SO-001", "项目A"),
                        item("1", "1", "1", "SO-002", "项目A")
                )
        );

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> assertThat(row).doesNotContainKey("isSeparator"));
        assertThat(rows.get(0)).containsEntry("rowTop", "10");
        assertThat(rows.get(1)).containsEntry("rowTop", "15");
    }

    @Test
    void shouldHandleEmptyItemsWhenGroupingLayoutIsEnabled() throws Exception {
        PrintRecordLayoutPreparer customPreparer = preparerWithRuntime(
                """
                        {
                          "dateParts": {"sourceKeys": []},
                          "decimalFields": [],
                          "headerBusinessNo": {"target": ""},
                          "adaptiveFields": []
                        }
                        """,
                "{}",
                """
                        {
                          "dynamicFields": ["rowTop"],
                          "defaultLayout": {
                            "tableTop": 10,
                            "rowHeight": 5,
                            "maxRows": 2,
                            "pageHeight": 0,
                            "limitToMaxRows": false,
                            "pageResetTop": 10,
                            "groupByField": "projectName",
                            "separatorField": "groupName"
                          },
                          "rules": []
                        }
                        """
        );
        Map<String, String> data = new HashMap<>();

        List<Map<String, String>> rows = customPreparer.prepare("custom", "任意模板", "{{rowTop}}", data, List.of());

        assertThat(rows).isEmpty();
        assertThat(data).containsEntry("sumTop", "10").containsEntry("hasEmptyRows", "");
    }

    @Test
    void shouldNotLimitRowsWhenGroupingLayoutIsEnabled() throws Exception {
        PrintRecordLayoutPreparer customPreparer = preparerWithRuntime(
                """
                        {
                          "dateParts": {"sourceKeys": []},
                          "decimalFields": [],
                          "headerBusinessNo": {"target": ""},
                          "adaptiveFields": []
                        }
                        """,
                "{}",
                """
                        {
                          "dynamicFields": ["rowTop", "groupName"],
                          "defaultLayout": {
                            "tableTop": 10,
                            "rowHeight": 5,
                            "maxRows": 1,
                            "pageHeight": 0,
                            "limitToMaxRows": true,
                            "pageResetTop": 10,
                            "groupByField": "projectName",
                            "separatorField": "groupName"
                          },
                          "rules": []
                        }
                        """
        );

        List<Map<String, String>> rows = customPreparer.prepare(
                "custom",
                "任意模板",
                "{{rowTop}} {{groupName}}",
                new HashMap<>(),
                List.of(
                        item("1", "1", "1", "SO-001", "项目A"),
                        item("1", "1", "1", "SO-002", "项目A")
                )
        );

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsEntry("index", "1");
        assertThat(rows.get(1)).containsEntry("index", "2");
        assertThat(rows.get(2)).containsEntry("isSeparator", "true").containsEntry("groupName", "项目A");
    }

    @Test
    void shouldIgnoreNullDatePartsAndNullDisplayWidth() throws Exception {
        var putDateParts = PrintRecordLayoutPreparer.class.getDeclaredMethod("putDateParts", Map.class, String.class);
        putDateParts.setAccessible(true);
        Map<String, String> data = new HashMap<>();

        putDateParts.invoke(preparer, data, (String) null);

        assertThat(data).doesNotContainKeys("dateYear", "dateMonth", "dateDay");

        var displayWidth = PrintRecordLayoutPreparer.class.getDeclaredMethod("displayWidth", String.class);
        displayWidth.setAccessible(true);
        assertThat(displayWidth.invoke(preparer, (String) null)).isEqualTo(0);
    }

    private Map<String, String> item(
            String quantity,
            String weightTon,
            String amount,
            String sourceNo,
            String projectName
    ) {
        return Map.of(
                "quantity", quantity,
                "weightTon", weightTon,
                "amount", amount,
                "sourceNo", sourceNo,
                "projectName", projectName
        );
    }

    private List<Map<String, String>> items(int count, String sourceNo, String projectName) {
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(item("1", "1", "1", sourceNo, projectName));
        }
        return result;
    }

    private PrintRecordLayoutPreparer preparerWithRuntime(
            String topLevelJson,
            String totalsJson,
            String legacyLayoutJson
    ) throws Exception {
        PrintRuntimeProperties properties = new ConfigurableRuntimeProperties(
                objectMapper,
                objectMapper.readTree(topLevelJson),
                objectMapper.readTree(totalsJson),
                objectMapper.readTree(legacyLayoutJson)
        );
        return new PrintRecordLayoutPreparer(new PrintRecordFieldFormatter(properties), properties);
    }

    private static final class ConfigurableRuntimeProperties extends PrintRuntimeProperties {

        private final JsonNode topLevelFields;
        private final JsonNode totals;
        private final JsonNode legacyLayout;

        private ConfigurableRuntimeProperties(
                ObjectMapper objectMapper,
                JsonNode topLevelFields,
                JsonNode totals,
                JsonNode legacyLayout
        ) {
            super(objectMapper);
            this.topLevelFields = topLevelFields;
            this.totals = totals;
            this.legacyLayout = legacyLayout;
        }

        @Override
        JsonNode topLevelFields() {
            return topLevelFields;
        }

        @Override
        JsonNode totals() {
            return totals;
        }

        @Override
        JsonNode legacyLayout() {
            return legacyLayout;
        }
    }
}
