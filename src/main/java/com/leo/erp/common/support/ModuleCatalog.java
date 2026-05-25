package com.leo.erp.common.support;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ModuleCatalog {

    private static final Map<String, String> MODULE_NAME_MAP = buildModuleNameMap();

    public List<String> orderedModuleKeys() {
        return List.copyOf(MODULE_NAME_MAP.keySet());
    }

    public String resolveModuleName(String moduleKey) {
        return MODULE_NAME_MAP.getOrDefault(moduleKey, moduleKey);
    }

    public boolean containsModule(String moduleKey) {
        return moduleKey != null && MODULE_NAME_MAP.containsKey(moduleKey);
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
        modules.put("department", "部门");
        modules.put("purchase-order", "采购订单");
        modules.put("purchase-inbound", "采购入库");
        modules.put("sales-order", "销售订单");
        modules.put("sales-outbound", "销售出库");
        modules.put("freight-bill", "物流单");
        modules.put("purchase-contract", "采购合同");
        modules.put("sales-contract", "销售合同");
        modules.put("supplier-statement", "供应商对账单");
        modules.put("customer-statement", "客户对账单");
        modules.put("freight-statement", "物流对账单");
        modules.put("receipt", "收款单");
        modules.put("payment", "付款单");
        modules.put("invoice-receipt", "收票单");
        modules.put("invoice-issue", "开票单");
        modules.put("pending-invoice-receipt-report", "未收票报表");
        modules.put("receivable-payable", "应收应付");
        modules.put("project-ar", "项目应收");
        modules.put("general-setting", "通用设置");
        modules.put("company-setting", "公司信息");
        modules.put("permission", "权限管理");
        modules.put("user-account", "用户账户");
        modules.put("role-setting", "角色权限配置");
        modules.put("role-action-editor", "角色权限配置");
        modules.put("print-template", "打印模板");
        modules.put("operation-log", "操作日志");
        modules.put("session", "会话管理");
        modules.put("api-key", "API Key 管理");
        modules.put("security-key", "安全密钥管理");
        modules.put("database", "数据库管理");
        modules.put("io-report", "出入库报表");
        modules.put("inventory-report", "库存报表");
        return modules;
    }
}
