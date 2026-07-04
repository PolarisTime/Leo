package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrintLayoutLodopRendererTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PrintRuntimeProperties runtimeProperties = new PrintRuntimeProperties(objectMapper);
    private final PrintLayoutLodopRenderer renderer = new PrintLayoutLodopRenderer(objectMapper, runtimeProperties);

    @Test
    void shouldDetectSupportedLayoutJsonOnlyWhenTableIsObject() {
        assertThat(renderer.supports(null)).isFalse();
        assertThat(renderer.supports("LODOP.PRINT_INIT('x');")).isFalse();
        assertThat(renderer.supports("{broken")).isFalse();
        assertThat(renderer.supports("[]")).isFalse();
        assertThat(renderer.supports("{}")).isFalse();
        assertThat(renderer.supports("{\"table\":null}")).isFalse();
        assertThat(renderer.supports("{\"table\":[]}")).isFalse();
        assertThat(renderer.supports("{\"table\":{\"columns\":[]}}")).isTrue();
    }

    @Test
    void shouldRejectSupportedLayoutWhenJsonReaderReturnsNullRoot() {
        ObjectMapper nullRootMapper = new ObjectMapper() {
            @Override
            public JsonNode readTree(String content) throws JsonProcessingException {
                return null;
            }
        };
        PrintLayoutLodopRenderer renderer = new PrintLayoutLodopRenderer(nullRootMapper, runtimeProperties);

        assertThat(renderer.supports("{\"table\":{}}")).isFalse();
    }

    @Test
    void shouldRenderFieldsTableSummaryAndClauses() {
        String script = renderer.render(
                "销售\"模板",
                """
                        {
                          "fields": {
                            "billDate": {
                              "source": ["missingDate", "orderDate"],
                              "format": "chineseDate",
                              "left": 10,
                              "top": 20,
                              "width": 120,
                              "height": 18,
                              "fontSize": 11
                            },
                            "blankField": {
                              "source": "missing",
                              "left": 1,
                              "top": 1,
                              "width": 1,
                              "height": 1
                            },
                            "noBox": {"source": "customerName"}
                          },
                          "table": {
                            "left": 30,
                            "top": 100,
                            "headerHeight": 20,
                            "rowHeight": 16,
                            "columns": [
                              {"label": "规格", "key": "spec", "width": 70, "normalize": "compactAscii"},
                              {"label": "数量", "source": "quantity", "width": 40}
                            ]
                          },
                          "summary": {
                            "height": 24,
                            "border": false,
                            "paddingTop": 5,
                            "paddingLeft": 4,
                            "template": "件数 ${totalQuantity} 重量 ${totalWeight} 备注 ${remark} 公司 ${settlementCompanyName}"
                          },
                          "clauses": {
                            "paddingTop": 6,
                            "lineHeightPx": 12,
                            "lines": ["第一条", "", "第二条"]
                          }
                        }
                        """,
                Map.of(
                        "orderDate", "2026-7-3 10:20:30",
                        "customerName", "客户A"
                ),
                List.of(
                        Map.of("spec", "HRB 400 E", "quantity", "2", "weightTon", "1.2344"),
                        Map.of("spec", "Q 235 B", "quantity", "3.50", "weightTon", "-"),
                        Map.of("spec", "坏数据", "quantity", "bad", "weightTon", "bad")
                )
        );

        assertThat(script).contains("LODOP.PRINT_INIT(\"销售\\\"模板\")");
        assertThat(script).contains("2026年07月03日");
        assertThat(script).doesNotContain("blankField").doesNotContain("客户A");
        assertThat(script).contains("HRB400E").contains("Q235B");
        assertThat(script).contains("件数 5.5 重量 1.234 备注 无 公司 嘉兴颖捷建材有限公司");
        assertThat(script).contains("第一条").contains("第二条");
    }

    @Test
    void shouldUseExplicitSummaryWidthAndKeepExistingPlaceholderValues() {
        String script = renderer.render(
                "模板",
                """
                        {
                          "table": {
                            "left": 10,
                            "top": 20,
                            "headerHeight": 8,
                            "rowHeight": 10,
                            "width": 180,
                            "columns": [{"label": "名称", "source": "name", "width": 60}]
                          },
                          "summary": {
                            "template": "备注 ${remark}",
                            "height": 18
                          }
                        }
                        """,
                Map.of("remark", "已备注"),
                List.of(Map.of("name", "商品", "quantity", "0", "weightTon", "0"))
        );

        assertThat(script).contains("LODOP.ADD_PRINT_RECT(38,10,180,18,0,1)");
        assertThat(script).contains("备注 已备注");
    }

    @Test
    void shouldRenderBlankSummaryWhenTemplateIsNull() {
        String script = renderer.render(
                "模板",
                """
                        {
                          "table": {
                            "left": 10,
                            "top": 20,
                            "headerHeight": 8,
                            "rowHeight": 10,
                            "columns": [{"label": "名称", "source": "name", "width": 60}]
                          },
                          "summary": {
                            "template": null,
                            "height": 18
                          }
                        }
                        """,
                Map.of(),
                List.of()
        );

        assertThat(script).contains("LODOP.ADD_PRINT_TEXT(35,16,48,12,\"\")");
    }

    @Test
    void shouldRenderWithoutSummaryAndHandleDateFallbackBranches() {
        String script = renderer.render(
                "模板",
                """
                        {
                          "fields": {
                            "alreadyChineseDate": {
                              "source": "already",
                              "format": "chineseDate",
                              "left": 1,
                              "top": 2,
                              "width": 80,
                              "height": 12
                            },
                            "invalidDate": {
                              "source": "invalidDate",
                              "format": "chineseDate",
                              "left": 1,
                              "top": 16,
                              "width": 80,
                              "height": 12
                            },
                            "missingArray": {
                              "source": ["missingOne", "missingTwo"],
                              "left": 1,
                              "top": 30,
                              "width": 80,
                              "height": 12
                            }
                          },
                          "table": {"columns": []}
                        }
                        """,
                Map.of(
                        "already", "2026年7月3日",
                        "invalidDate", "20260703"
                ),
                List.of()
        );

        assertThat(script).contains("2026年7月3日");
        assertThat(script).contains("20260703");
        assertThat(script).doesNotContain("missingArray");
    }

    @Test
    void shouldHandleFallbackBranchesForFieldBoxesValuesAndCollections() {
        Map<String, String> data = new HashMap<>();
        data.put("blankCandidate", " ");
        data.put("fallbackValue", "后备值");
        data.put("twoDigitDate", "2026-07-13");
        data.put("nullableDate", null);
        data.put("remark", " ");

        Map<String, String> firstItem = new HashMap<>();
        firstItem.put("name", "货物A");
        firstItem.put("weightTon", " ");
        Map<String, String> secondItem = new HashMap<>();
        secondItem.put("quantity", " ");

        String script = renderer.render(
                null,
                """
                        {
                          "fields": {
                            "nullableDate": {
                              "source": "nullableDate",
                              "format": "chineseDate",
                              "left": 1,
                              "top": 2,
                              "width": 80,
                              "height": 12
                            },
                            "blankChineseDate": {
                              "source": "missing",
                              "format": "chineseDate",
                              "left": 1,
                              "top": 16,
                              "width": 80,
                              "height": 12
                            },
                            "twoDigitDate": {
                              "source": "twoDigitDate",
                              "format": "chineseDate",
                              "left": 1,
                              "top": 30,
                              "width": 80,
                              "height": 12
                            },
                            "arraySource": {
                              "source": ["blankCandidate", "fallbackValue"],
                              "format": null,
                              "left": 1,
                              "top": 44,
                              "width": 80,
                              "height": 12
                            },
                            "missingTop": {
                              "source": "fallbackValue",
                              "left": 1,
                              "width": 80,
                              "height": 12
                            },
                            "missingWidth": {
                              "source": "fallbackValue",
                              "left": 1,
                              "top": 58,
                              "height": 12
                            },
                            "missingHeight": {
                              "source": "fallbackValue",
                              "left": 1,
                              "top": 72,
                              "width": 80
                            }
                          },
                          "table": {
                            "columns": [
                              {"label": null, "source": "name", "width": 40},
                              "skip"
                            ]
                          },
                          "summary": {
                            "template": "备注 ${remark} 件数 ${totalQuantity} 重量 ${totalWeight}"
                          },
                          "clauses": {"lines": {"ignored": true}}
                        }
                        """,
                data,
                List.of(firstItem, secondItem)
        );

        assertThat(script).startsWith("LODOP.PRINT_INIT(\"\");");
        assertThat(script).contains("2026年07月13日");
        assertThat(script).contains("后备值");
        assertThat(script).contains("货物A");
        assertThat(script).contains("备注 无 件数 0 重量 0");
    }

    @Test
    void shouldRenderWhenColumnsNodeIsNotArray() {
        String script = renderer.render(
                "模板",
                """
                        {
                          "table": {"columns": {"name": "ignored"}},
                          "summary": {"template": "空表"}
                        }
                        """,
                Map.of(),
                List.of()
        );

        assertThat(script).contains("空表");
    }

    @Test
    void shouldFallbackToEmptyJsStringWhenMapperCannotSerialize() {
        ObjectMapper throwingMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw JsonMappingException.fromUnexpectedIOE(new IOException("boom"));
            }
        };
        PrintLayoutLodopRenderer renderer = new PrintLayoutLodopRenderer(throwingMapper, runtimeProperties);

        String script = renderer.render("模板", "{\"table\":{\"columns\":[]},\"summary\":{\"template\":\"\"}}", Map.of(), List.of());

        assertThat(script).startsWith("LODOP.PRINT_INIT(\"\");");
    }

    @Test
    void shouldRejectInvalidLayoutJsonOnRender() {
        assertThatThrownBy(() -> renderer.render("模板", "{broken", Map.of(), List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("套打布局配置不是合法 JSON");
    }
}
