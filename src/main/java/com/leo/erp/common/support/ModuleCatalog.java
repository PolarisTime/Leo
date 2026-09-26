package com.leo.erp.common.support;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class ModuleCatalog {

    private static final Map<String, String> MODULE_NAME_MAP = buildModuleNameMap();
    private static final Map<String, String> MODULE_ALIAS_MAP = Map.of(
            "material-categories", ModuleKeys.MATERIAL_CATEGORY
    );

    public String resolveModuleName(String moduleKey) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        return MODULE_NAME_MAP.getOrDefault(normalizedModuleKey, moduleKey);
    }

    /** 按模块键返回中文显示名(静态入口, 供事件发布/日志等无需注入 catalog 的场景复用)。 */
    public static String moduleName(String moduleKey) {
        if (moduleKey == null) {
            return null;
        }
        String normalized = moduleKey.trim()
                .replaceFirst("^/+", "")
                .toLowerCase(Locale.ROOT);
        normalized = MODULE_ALIAS_MAP.getOrDefault(normalized, normalized);
        return MODULE_NAME_MAP.getOrDefault(normalized, moduleKey);
    }

    public boolean containsModule(String moduleKey) {
        return moduleKey != null && MODULE_NAME_MAP.containsKey(normalizeModuleKey(moduleKey));
    }

    public String normalizeModuleKey(String moduleKey) {
        if (moduleKey == null) {
            return null;
        }
        String normalized = moduleKey.trim()
                .replaceFirst("^/+", "")
                .toLowerCase(Locale.ROOT);
        return MODULE_ALIAS_MAP.getOrDefault(normalized, normalized);
    }

    private static Map<String, String> buildModuleNameMap() {
        Map<String, String> modules = new LinkedHashMap<>();
        modules.put(ModuleKeys.MATERIAL, "商品资料");
        modules.put(ModuleKeys.MATERIAL_CATEGORY, "商品类别");
        modules.put(ModuleKeys.SUPPLIER, "供应商");
        modules.put(ModuleKeys.CUSTOMER, "客户");
        modules.put(ModuleKeys.PROJECT, "项目");
        modules.put(ModuleKeys.CARRIER, "物流商");
        modules.put(ModuleKeys.WAREHOUSE, "仓库");
        modules.put(ModuleKeys.PURCHASE_ORDER, "采购订单");
        modules.put(ModuleKeys.PURCHASE_INBOUND, "采购入库");
        modules.put(ModuleKeys.SALES_ORDER, "销售订单");
        modules.put(ModuleKeys.SALES_CONTRACT, "销售合同");
        modules.put(ModuleKeys.SALES_OUTBOUND, "销售出库");
        modules.put(ModuleKeys.SALES_RETURN, "销售退货单");
        modules.put(ModuleKeys.FREIGHT_BILL, "物流单");
        modules.put(ModuleKeys.CUSTOMER_STATEMENT, "客户对账单");
        modules.put(ModuleKeys.FREIGHT_STATEMENT, "物流对账单");
        modules.put(ModuleKeys.RECEIPT, "收款单");
        modules.put(ModuleKeys.PAYMENT, "付款单");
        modules.put(ModuleKeys.LEDGER_ADJUSTMENT, "台账调整单");
        modules.put(ModuleKeys.FINANCE_OVERVIEW, "财务概览");
        modules.put(ModuleKeys.CASH_LEDGER, "资金流水");
        modules.put(ModuleKeys.COMPANY_SETTING, "结算主体管理");
        modules.put(ModuleKeys.ACCOUNT, "个人账号");
        modules.put(ModuleKeys.PRINT_TEMPLATE, "打印模板");
        modules.put(ModuleKeys.OPERATION_LOG, "操作日志");
        return modules;
    }
}
