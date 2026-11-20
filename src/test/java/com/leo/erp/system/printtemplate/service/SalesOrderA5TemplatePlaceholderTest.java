package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A5 套打模板的单据号口径：**打印展示号（8 位纯数字 displayNo），不是内部 orderNo**。
 *
 * <p>业务背景（2026-11-17 用户要求）：A5 套打是给客户/司机看的纸质单据，单号要短、能口头报，
 * 因此用 id 派生的 8 位展示号；内部 orderNo（如 SO202611170012）仍用于系统检索与文件名。
 * 这里同时锁两件事，缺一个就会"打印出来空白或又变回长单号"：</p>
 * <ol>
 *   <li>两个 A5 模板（颖捷 / 熠祺）首页与续页的单号位置都必须是 {@code {{displayNo}}}；</li>
 *   <li>{@code print-runtime.json} 的 sales-order 打印列里必须有 {@code display_no}，
 *       否则占位符拿不到值（数据源只 SELECT 配置里列出的列）。</li>
 * </ol>
 */
class SalesOrderA5TemplatePlaceholderTest {

    /** A5 套打模板（与 print-template-manifest.json 的 sourceRef 一致）。 */
    private static final List<String> A5_TEMPLATES = List.of(
            "print-forms/sales-order-a5-yingjie.lodop.txt",
            "print-forms/sales-order-a5-yiqi.lodop.txt"
    );

    @Test
    void a5Templates_printDisplayNoInsteadOfInternalOrderNo() throws Exception {
        for (String path : A5_TEMPLATES) {
            String template = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
            assertThat(template)
                    .as("%s 必须打印展示号", path)
                    .contains("{{displayNo}}");
            assertThat(template)
                    .as("%s 不应再打印内部 orderNo", path)
                    .doesNotContain("{{orderNo}}");
        }
    }

    /**
     * 2026-11-20 用户要求：公司名称行（customerName）与项目名称行整体**上移 0.5cm**。
     *
     * <p>LODOP 的 ADD_PRINT_TEXT 坐标单位是 px（96dpi），0.5cm ≈ 19px：
     * customerName 行 45 → 26；项目名行由 `projectNameTop` 驱动（print-runtime.json，
     * 默认档 + 各字号档）90 → 71。两处必须同时改，否则套打纸上两行会错位。</p>
     */
    @Test
    void a5Templates_moveCompanyAndProjectNameRowsUpByHalfCentimeter() throws Exception {
        for (String path : A5_TEMPLATES) {
            String template = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
            assertThat(template)
                    .as("%s 公司名称行必须上移到 top=26（原 45）", path)
                    .contains("ADD_PRINT_TEXT(26,110,400,20,\"{{customerName}}\")");
            assertThat(template)
                    .as("%s 不应再使用旧坐标 45", path)
                    .doesNotContain("ADD_PRINT_TEXT(45,110,400,20,\"{{customerName}}\")");
        }
    }

    @Test
    void printRuntime_projectNameTopIsMovedUpByHalfCentimeter() {
        PrintRuntimeProperties properties = new PrintRuntimeProperties(new ObjectMapper());
        var adaptiveFields = properties.childObjects(
                properties.topLevelFields().path("adaptiveFields"));
        var projectName = adaptiveFields.stream()
                .filter(field -> "projectName".equals(field.path("sourceField").asText()))
                .findFirst()
                .orElseThrow();

        assertThat(properties.text(projectName.path("defaultValues"), "projectNameTop", ""))
                .as("默认档项目名 top")
                .isEqualTo("71");
        for (var rule : properties.childObjects(projectName.path("rules"))) {
            assertThat(properties.text(rule.path("values"), "projectNameTop", ""))
                    .as("字号档项目名 top 必须同步上移")
                    .isEqualTo("71");
        }
    }

    @Test
    void salesOrderPrintColumns_exposeDisplayNo() {
        PrintRuntimeProperties properties = new PrintRuntimeProperties(new ObjectMapper());
        PrintRecordSource source = properties.source("sales-order");

        // 数据源按 printColumns 生成 SELECT：少了这一列，模板里的 {{displayNo}} 渲染成空
        assertThat(source.printColumns()).contains("display_no");
    }
}
