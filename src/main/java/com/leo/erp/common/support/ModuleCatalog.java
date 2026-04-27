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
        modules.put("materials", "商品资料");
        modules.put("suppliers", "供应商");
        modules.put("customers", "客户");
        modules.put("carriers", "物流商");
        modules.put("warehouses", "仓库");
        modules.put("purchase-orders", "采购订单");
        modules.put("purchase-inbounds", "采购入库");
        modules.put("sales-orders", "销售订单");
        modules.put("sales-outbounds", "销售出库");
        modules.put("freight-bills", "物流单");
        modules.put("purchase-contracts", "采购合同");
        modules.put("sales-contracts", "销售合同");
        modules.put("supplier-statements", "供应商对账单");
        modules.put("customer-statements", "客户对账单");
        modules.put("freight-statements", "物流对账单");
        modules.put("receipts", "收款单");
        modules.put("payments", "付款单");
        modules.put("invoice-receipts", "收票单");
        modules.put("invoice-issues", "开票单");
        modules.put("pending-invoice-receipt-report", "未收票报表");
        modules.put("general-settings", "通用设置");
        modules.put("company-settings", "公司信息");
        modules.put("permission-management", "权限管理");
        modules.put("user-accounts", "用户账户");
        modules.put("role-settings", "角色权限配置");
        modules.put("role-action-editor", "角色权限配置");
        modules.put("print-templates", "打印模板");
        modules.put("operation-logs", "操作日志");
        modules.put("session-management", "会话管理");
        modules.put("api-key-management", "API Key 管理");
        modules.put("security-keys", "安全密钥管理");
        modules.put("database-management", "数据库管理");
        modules.put("io-report", "出入库报表");
        modules.put("inventory-report", "库存报表");
        return modules;
    }
}
