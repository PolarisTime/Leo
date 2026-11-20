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
     * 2026-11-20 用户要求（第二次确认）：**只把项目名称行上移 0.5cm，公司名称行保持原位**。
     *
     * <p>第一次改动把两行一起上移（customerName 45 → 26、projectNameTop 90 → 71），
     * 用户在实际纸张上确认后要求回退公司名称那一条 —— 套打纸上公司名称与表格的相对位置是对的，
     * 上移后反而压到了上方预印内容。因此这里锁：companyName 仍是 45（首页与续页），
     * 项目名称继续走 projectNameTop=71。</p>
     */
    @Test
    void a5Templates_keepCompanyNamerowAndMoveOnlyProjectName() throws Exception {
        for (String path : A5_TEMPLATES) {
            String template = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
            assertThat(template)
                    .as("%s 公司名称行保持原位 top=45", path)
                    .contains("ADD_PRINT_TEXT(45,110,400,20,\"{{customerName}}\")");
            assertThat(template)
                    .as("%s 公司名称行不应再被上移到 26", path)
                    .doesNotContain("ADD_PRINT_TEXT(26,110,400,20,\"{{customerName}}\")");
            assertThat(template)
                    .as("%s 项目名称行仍由 projectNameTop 驱动", path)
                    .contains("ADD_PRINT_TEXT({{projectNameTop}},110,420,{{projectNameHeight}},\"{{projectName}}\")");
        }
    }

    /**
     * 项目名称行水平居中（2026-11-20 用户要求：以两行为基准、名称不超长时单行居中）。
     *
     * <p>LODOP 里 {@code SET_PRINT_STYLEA(0,"Alignment",2)} 表示居中；缺了它套打纸上是左对齐，
     * 单行名称会贴着框左边缘，与两行名称的观感不一致。首页与续页、两个模板共 4 处都要有。</p>
     */
    @Test
    void a5Templates_centerProjectNameHorizontally() throws Exception {
        for (String path : A5_TEMPLATES) {
            String template = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
            assertThat(template.split("SET_PRINT_STYLEA\\(0,\"Alignment\",2\\);", -1).length - 1)
                    .as("%s 项目名称行必须水平居中（首页 + 续页各一处）", path)
                    .isGreaterThanOrEqualTo(2);
            assertThat(template)
                    .as("%s 居中样式必须紧跟项目名称行", path)
                    .contains("\"{{projectName}}\");\nLODOP.SET_PRINT_STYLEA(0,\"Alignment\",2);");
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
