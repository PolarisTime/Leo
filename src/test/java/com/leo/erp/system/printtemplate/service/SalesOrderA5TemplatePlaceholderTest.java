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

    @Test
    void salesOrderPrintColumns_exposeDisplayNo() {
        PrintRuntimeProperties properties = new PrintRuntimeProperties(new ObjectMapper());
        PrintRecordSource source = properties.source("sales-order");

        // 数据源按 printColumns 生成 SELECT：少了这一列，模板里的 {{displayNo}} 渲染成空
        assertThat(source.printColumns()).contains("display_no");
    }
}
