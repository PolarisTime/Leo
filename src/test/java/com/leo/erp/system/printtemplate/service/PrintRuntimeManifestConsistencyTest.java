package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.security.permission.PermissionCodes;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `print-runtime.json` 的 printable 声明与模板清单（`print-template-manifest.json`）的一致性。
 *
 * <p>两者分别管"数据面/可打印开关"与"模板文件"，历史上出现过「模块有打印入口、
 * 但没有模板」的空悬状态。本用例要求两边集合一一对应，且每个可打印模块至少有一个
 * isDefault 模板——新增模块时漏登记任一侧都会在单元测试阶段失败。</p>
 */
class PrintRuntimeManifestConsistencyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PrintRuntimeProperties properties = new PrintRuntimeProperties(objectMapper);
    private final PrintTemplateManifest manifest = new PrintTemplateManifest(objectMapper);

    @Test
    void printableModulesShouldMatchManifestBillTypes() {
        Set<String> printable = new HashSet<>(properties.printableModules());
        Set<String> billTypes = manifest.getTemplates().stream()
                .map(PrintTemplateManifest.Item::getBillType)
                .collect(Collectors.toSet());

        assertThat(printable)
                .as("print-runtime.json 的 printable 模块必须与模板清单的 billType 一一对应")
                .containsExactlyInAnyOrderElementsOf(billTypes);
    }

    @Test
    void everyPrintableModuleShouldHaveDefaultTemplate() {
        for (String moduleKey : properties.printableModules()) {
            boolean hasDefaultTemplate = manifest.getTemplates().stream()
                    .anyMatch(item -> moduleKey.equals(item.getBillType()) && item.isDefault());
            assertThat(hasDefaultTemplate)
                    .as("可打印模块缺少默认模板: " + moduleKey)
                    .isTrue();
        }
    }

    @Test
    void readPermissionShouldMatchPermissionCodes() {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("purchase-order", PermissionCodes.PURCHASE_ORDERS_READ),
                Map.entry("purchase-inbound", PermissionCodes.PURCHASE_INBOUNDS_READ),
                Map.entry("sales-order", PermissionCodes.SALES_ORDERS_READ),
                Map.entry("sales-outbound", PermissionCodes.SALES_OUTBOUNDS_READ),
                Map.entry("sales-return", PermissionCodes.SALES_RETURNS_READ),
                Map.entry("freight-bill", PermissionCodes.FREIGHT_BILLS_READ),
                Map.entry("customer-statement", PermissionCodes.CUSTOMER_STATEMENTS_READ),
                Map.entry("freight-statement", PermissionCodes.FREIGHT_STATEMENTS_READ),
                Map.entry("receipt", PermissionCodes.RECEIPTS_READ),
                Map.entry("payment", PermissionCodes.PAYMENTS_READ)
        );

        assertThat(properties.printableModules())
                .as("可打印模块必须与权限码清单一一对应")
                .containsExactlyInAnyOrderElementsOf(expected.keySet());
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertThat(properties.readPermission(entry.getKey()))
                    .as("模块 %s 的 readPermission 必须与 PermissionCodes 一致", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }
}
