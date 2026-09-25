package com.leo.erp.common.support;

/**
 * 业务模块标识常量。
 *
 * <p>取值与前端路由/{@link ModuleCatalog} 的中文名映射、各服务内部 {@code MODULE_KEY} 一致;
 * 集中于此以消除跨类重复的模块键魔法字符串, 不改变任何对外契约。</p>
 */
public final class ModuleKeys {

    private ModuleKeys() {
    }

    public static final String MATERIAL = "material";
    public static final String MATERIAL_CATEGORY = "material-category";
    public static final String SUPPLIER = "supplier";
    public static final String CUSTOMER = "customer";
    public static final String PROJECT = "project";
    public static final String CARRIER = "carrier";
    public static final String WAREHOUSE = "warehouse";

    public static final String PURCHASE_ORDER = "purchase-order";
    public static final String PURCHASE_INBOUND = "purchase-inbound";

    public static final String SALES_ORDER = "sales-order";
    public static final String SALES_CONTRACT = "sales-contract";
    public static final String SALES_OUTBOUND = "sales-outbound";
    public static final String SALES_RETURN = "sales-return";

    public static final String FREIGHT_BILL = "freight-bill";
    public static final String CUSTOMER_STATEMENT = "customer-statement";
    public static final String FREIGHT_STATEMENT = "freight-statement";

    public static final String RECEIPT = "receipt";
    public static final String PAYMENT = "payment";
    public static final String LEDGER_ADJUSTMENT = "ledger-adjustment";
    public static final String FINANCE_OVERVIEW = "finance-overview";
    public static final String CASH_LEDGER = "cash-ledger";

    public static final String COMPANY_SETTING = "company-setting";
    public static final String ACCOUNT = "account";
    public static final String PRINT_TEMPLATE = "print-template";
    public static final String OPERATION_LOG = "operation-log";
}
