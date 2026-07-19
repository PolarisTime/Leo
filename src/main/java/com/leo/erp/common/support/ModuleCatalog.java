package com.leo.erp.common.support;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ModuleCatalog {

    private static final Map<String, String> MODULE_NAME_MAP = buildModuleNameMap();
    private static final Map<String, String> MODULE_ALIAS_MAP = Map.of(
            "material-categories", "material-category"
    );

    public List<String> orderedModuleKeys() {
        return List.copyOf(MODULE_NAME_MAP.keySet());
    }

    public String resolveModuleName(String moduleKey) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        return MODULE_NAME_MAP.getOrDefault(normalizedModuleKey, moduleKey);
    }

    public boolean containsModule(String moduleKey) {
        return moduleKey != null && MODULE_NAME_MAP.containsKey(normalizeModuleKey(moduleKey));
    }

    public String normalizeModuleKey(String moduleKey) {
        if (moduleKey == null) {
            return null;
        }
        String normalized = moduleKey.trim();
        return MODULE_ALIAS_MAP.getOrDefault(normalized, normalized);
    }

    private static Map<String, String> buildModuleNameMap() {
        Map<String, String> modules = new LinkedHashMap<>();
        modules.put("material", "商品资料");
        modules.put("material-category", "商品类别");
        modules.put("supplier", "供应商");
        modules.put("customer", "客户");
        modules.put("project", "项目");
        modules.put("carrier", "物流商");
        modules.put("warehouse", "仓库");
        modules.put("purchase-order", "采购订单");
        modules.put("purchase-inbound", "采购入库");
        modules.put("sales-order", "销售订单");
        modules.put("sales-outbound", "销售出库");
        modules.put("freight-bill", "物流单");
        modules.put("customer-statement", "客户对账单");
        modules.put("freight-statement", "物流对账单");
        modules.put("receipt", "收款单");
        modules.put("payment", "付款单");
        modules.put("ledger-adjustment", "台账调整单");
        modules.put("finance-overview", "财务概览");
        modules.put("cash-ledger", "资金流水");
        modules.put("general-setting", "通用设置");
        modules.put("company-setting", "结算主体管理");
        modules.put("user-account", "用户账户");
        modules.put("print-template", "打印模板");
        modules.put("operation-log", "操作日志");
        modules.put("database", "数据库管理");
        return modules;
    }
}
