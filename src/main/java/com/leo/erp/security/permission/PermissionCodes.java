package com.leo.erp.security.permission;

import java.util.Set;

/**
 * 权限码目录（单一来源）。
 *
 * <p>权限码规范：{@code <资源复数kebab>:<动作>[:<字段>]}，例如
 * {@code sales-returns:audit}、{@code inventory:read:cost}。</p>
 *
 * <ul>
 *   <li><b>资源</b>：与 REST 路径的复数 kebab-case 对齐，见 {@link Resources}；</li>
 *   <li><b>动作</b>：细粒度业务动作，见 {@link Actions}；</li>
 *   <li><b>字段</b>（可选）：字段级读/写收敛，示例见 {@link Fields}。</li>
 * </ul>
 *
 * <p>通配：{@link #WILDCARD}（{@code *}）表示全部权限；{@code <资源>:*}
 * 表示某资源下的全部动作，可用 {@link #ofResourceWildcard(String)} 构造。</p>
 *
 * <p>权限码常量必须由编译期常量拼接而成（{@code Resources.X + ":" + Actions.Y}），
 * 这样才可写入 {@link RequirePermission} 的注解参数；{@link #of(String, String)}
 * 仅用于运行期动态拼接。</p>
 *
 * <h2>三层粒度</h2>
 * <ol>
 *   <li><b>功能层</b>（本轮生效）：{@code resource:action}，与端点一一对应，由
 *       {@link RequirePermission} 校验；</li>
 *   <li><b>字段层</b>（本轮登记、暂不校验）：{@code resource:action:field}，
 *       以 {@link #SALES_ORDERS_READ_AMOUNT} 等示例常量为规范占位；</li>
 *   <li><b>数据层</b>（设计预留）：不进入权限码，由角色-数据范围
 *       （{@code sys_role_data_scope}）在查询侧收敛。</li>
 * </ol>
 *
 * <p>新增模块或动作时：先在本类补充资源/动作常量与权限码常量，再把权限码加入
 * {@link #all()}；{@code PermissionCodesTest} 会通过反射校验所有公开 String 常量均已纳入目录。</p>
 */
public final class PermissionCodes {

    /** 通配权限：拥有该权限即视为拥有全部权限，用于超级管理员角色。 */
    public static final String WILDCARD = "*";

    /**
     * 资源常量：API 路径的复数 kebab-case 形式。
     * 声明为嵌套类，避免与权限码常量混淆，也不影响 {@link #all()} 的反射完整性校验。
     */
    public static final class Resources {
        public static final String SALES_ORDERS = "sales-orders";
        public static final String SALES_CONTRACTS = "sales-contracts";
        public static final String SALES_RETURNS = "sales-returns";
        public static final String SALES_OUTBOUNDS = "sales-outbounds";
        public static final String MATERIALS = "materials";
        public static final String MATERIAL_IMPORTS = "material-imports";
        public static final String IMPORT_BATCHES = "import-batches";
        public static final String INVENTORY = "inventory";
        public static final String CUSTOMER_STATEMENTS = "customer-statements";
        public static final String CUSTOMERS = "customers";
        public static final String SUPPLIERS = "suppliers";
        public static final String WAREHOUSES = "warehouses";
        public static final String CARRIERS = "carriers";
        public static final String PROJECTS = "projects";
        public static final String QUOTE_SHEETS = "quote-sheets";
        public static final String STEEL_QUOTES = "steel-quotes";
        public static final String PURCHASE_ORDERS = "purchase-orders";
        public static final String PURCHASE_INBOUNDS = "purchase-inbounds";
        public static final String FREIGHT_BILLS = "freight-bills";
        public static final String FINANCE = "finance";
        public static final String RECEIPTS = "receipts";
        public static final String PAYMENTS = "payments";
        public static final String LEDGER_ADJUSTMENTS = "ledger-adjustments";
        public static final String ATTACHMENTS = "attachments";
        public static final String ATTACHMENT_UPLOAD_SESSIONS = "attachment-upload-sessions";
        public static final String ATTACHMENT_MANIFEST_EXPORTS = "attachment-manifest-exports";
        public static final String FREIGHT_STATEMENTS = "freight-statements";
        public static final String MATERIAL_CATEGORIES = "material-categories";
        public static final String MATERIAL_EXPORTS = "material-exports";
        public static final String STEEL_QUOTE_SYNCS = "steel-quote-syncs";
        public static final String STEEL_QUOTE_BACKFILLS = "steel-quote-backfills";
        public static final String STEEL_QUOTE_CALENDARS = "steel-quote-calendars";
        public static final String MATERIAL_PRICE_MATCHES = "material-price-matches";
        public static final String CASH_LEDGER = "cash-ledger";
        public static final String PRINT_TEMPLATES = "print-templates";
        public static final String PRINT_EXPORTS = "print-exports";
        public static final String PRINT_PREVIEWS = "print-previews";
        public static final String COMPANY_SETTINGS = "company-settings";
        public static final String DASHBOARD = "dashboard";
        public static final String OPERATION_LOGS = "operation-logs";
        public static final String GLOBAL_SEARCH = "global-search";
        public static final String META = "meta";
        public static final String CODE_ISSUANCES = "code-issuances";
        public static final String USER_ACCOUNTS = "user-accounts";
        public static final String ROLES = "roles";
        public static final String PERMISSIONS = "permissions";
        public static final String SYSTEM = "system";

        private Resources() {
        }
    }

    /** 动作常量：细粒度业务动作集，与 REST 端点语义对应。 */
    public static final class Actions {
        public static final String READ = "read";
        public static final String CREATE = "create";
        public static final String UPDATE = "update";
        public static final String DELETE = "delete";
        /** 写权限：用于以读写两档收敛角色/权限等管理资源。 */
        public static final String WRITE = "write";
        public static final String AUDIT = "audit";
        public static final String UNAUDIT = "unaudit";
        public static final String COMPLETE = "complete";
        public static final String CONFIRM = "confirm";
        public static final String PRINT = "print";
        public static final String EXPORT = "export";
        public static final String IMPORT = "import";
        public static final String PREVIEW = "preview";
        public static final String BACKFILL = "backfill";
        public static final String ROLLBACK = "rollback";
        public static final String REBUILD = "rebuild";
        public static final String SYNC = "sync";
        /** 资源级通配动作，配合 {@link #ofResourceWildcard(String)} 使用。 */
        public static final String WILDCARD = "*";

        private Actions() {
        }
    }

    /** 字段常量（示例）：字段级权限的第三段取值来源。 */
    public static final class Fields {
        public static final String AMOUNT = "amount";
        public static final String UNIT_PRICE = "unit-price";
        public static final String COST = "cost";

        private Fields() {
        }
    }

    // 销售订单
    public static final String SALES_ORDERS_READ = Resources.SALES_ORDERS + ":" + Actions.READ;
    public static final String SALES_ORDERS_CREATE = Resources.SALES_ORDERS + ":" + Actions.CREATE;
    public static final String SALES_ORDERS_UPDATE = Resources.SALES_ORDERS + ":" + Actions.UPDATE;
    public static final String SALES_ORDERS_DELETE = Resources.SALES_ORDERS + ":" + Actions.DELETE;
    public static final String SALES_ORDERS_AUDIT = Resources.SALES_ORDERS + ":" + Actions.AUDIT;
    public static final String SALES_ORDERS_UNAUDIT = Resources.SALES_ORDERS + ":" + Actions.UNAUDIT;
    public static final String SALES_ORDERS_CONFIRM = Resources.SALES_ORDERS + ":" + Actions.CONFIRM;
    public static final String SALES_ORDERS_COMPLETE = Resources.SALES_ORDERS + ":" + Actions.COMPLETE;
    public static final String SALES_ORDERS_PRINT = Resources.SALES_ORDERS + ":" + Actions.PRINT;
    public static final String SALES_ORDERS_EXPORT = Resources.SALES_ORDERS + ":" + Actions.EXPORT;

    // 销售合同
    public static final String SALES_CONTRACTS_READ = Resources.SALES_CONTRACTS + ":" + Actions.READ;
    public static final String SALES_CONTRACTS_CREATE = Resources.SALES_CONTRACTS + ":" + Actions.CREATE;
    public static final String SALES_CONTRACTS_UPDATE = Resources.SALES_CONTRACTS + ":" + Actions.UPDATE;
    public static final String SALES_CONTRACTS_DELETE = Resources.SALES_CONTRACTS + ":" + Actions.DELETE;

    // 销售退货单
    public static final String SALES_RETURNS_READ = Resources.SALES_RETURNS + ":" + Actions.READ;
    public static final String SALES_RETURNS_CREATE = Resources.SALES_RETURNS + ":" + Actions.CREATE;
    public static final String SALES_RETURNS_UPDATE = Resources.SALES_RETURNS + ":" + Actions.UPDATE;
    public static final String SALES_RETURNS_DELETE = Resources.SALES_RETURNS + ":" + Actions.DELETE;
    public static final String SALES_RETURNS_AUDIT = Resources.SALES_RETURNS + ":" + Actions.AUDIT;
    public static final String SALES_RETURNS_UNAUDIT = Resources.SALES_RETURNS + ":" + Actions.UNAUDIT;
    public static final String SALES_RETURNS_PRINT = Resources.SALES_RETURNS + ":" + Actions.PRINT;
    public static final String SALES_RETURNS_EXPORT = Resources.SALES_RETURNS + ":" + Actions.EXPORT;

    // 销售出库单
    public static final String SALES_OUTBOUNDS_READ = Resources.SALES_OUTBOUNDS + ":" + Actions.READ;
    public static final String SALES_OUTBOUNDS_CREATE = Resources.SALES_OUTBOUNDS + ":" + Actions.CREATE;
    public static final String SALES_OUTBOUNDS_UPDATE = Resources.SALES_OUTBOUNDS + ":" + Actions.UPDATE;
    public static final String SALES_OUTBOUNDS_DELETE = Resources.SALES_OUTBOUNDS + ":" + Actions.DELETE;
    public static final String SALES_OUTBOUNDS_AUDIT = Resources.SALES_OUTBOUNDS + ":" + Actions.AUDIT;
    public static final String SALES_OUTBOUNDS_UNAUDIT = Resources.SALES_OUTBOUNDS + ":" + Actions.UNAUDIT;
    public static final String SALES_OUTBOUNDS_PRINT = Resources.SALES_OUTBOUNDS + ":" + Actions.PRINT;
    public static final String SALES_OUTBOUNDS_EXPORT = Resources.SALES_OUTBOUNDS + ":" + Actions.EXPORT;

    // 商品资料
    public static final String MATERIALS_READ = Resources.MATERIALS + ":" + Actions.READ;
    public static final String MATERIALS_CREATE = Resources.MATERIALS + ":" + Actions.CREATE;
    public static final String MATERIALS_UPDATE = Resources.MATERIALS + ":" + Actions.UPDATE;
    public static final String MATERIALS_DELETE = Resources.MATERIALS + ":" + Actions.DELETE;

    // 商品分类
    public static final String MATERIAL_CATEGORIES_READ = Resources.MATERIAL_CATEGORIES + ":" + Actions.READ;
    public static final String MATERIAL_CATEGORIES_CREATE = Resources.MATERIAL_CATEGORIES + ":" + Actions.CREATE;
    public static final String MATERIAL_CATEGORIES_UPDATE = Resources.MATERIAL_CATEGORIES + ":" + Actions.UPDATE;
    public static final String MATERIAL_CATEGORIES_DELETE = Resources.MATERIAL_CATEGORIES + ":" + Actions.DELETE;

    // 商品资料导出
    public static final String MATERIAL_EXPORTS_EXPORT = Resources.MATERIAL_EXPORTS + ":" + Actions.EXPORT;

    // 商品资料导入
    public static final String MATERIAL_IMPORTS_READ = Resources.MATERIAL_IMPORTS + ":" + Actions.READ;
    public static final String MATERIAL_IMPORTS_IMPORT = Resources.MATERIAL_IMPORTS + ":" + Actions.IMPORT;
    public static final String MATERIAL_IMPORTS_PREVIEW = Resources.MATERIAL_IMPORTS + ":" + Actions.PREVIEW;

    // 商品资料导入批次
    public static final String IMPORT_BATCHES_READ = Resources.IMPORT_BATCHES + ":" + Actions.READ;
    public static final String IMPORT_BATCHES_ROLLBACK = Resources.IMPORT_BATCHES + ":" + Actions.ROLLBACK;

    // 库存
    public static final String INVENTORY_READ = Resources.INVENTORY + ":" + Actions.READ;
    public static final String INVENTORY_CREATE = Resources.INVENTORY + ":" + Actions.CREATE;
    public static final String INVENTORY_UPDATE = Resources.INVENTORY + ":" + Actions.UPDATE;
    public static final String INVENTORY_DELETE = Resources.INVENTORY + ":" + Actions.DELETE;
    public static final String INVENTORY_BACKFILL = Resources.INVENTORY + ":" + Actions.BACKFILL;
    public static final String INVENTORY_REBUILD = Resources.INVENTORY + ":" + Actions.REBUILD;

    // 客户对账单
    public static final String CUSTOMER_STATEMENTS_READ = Resources.CUSTOMER_STATEMENTS + ":" + Actions.READ;
    public static final String CUSTOMER_STATEMENTS_CREATE = Resources.CUSTOMER_STATEMENTS + ":" + Actions.CREATE;
    public static final String CUSTOMER_STATEMENTS_UPDATE = Resources.CUSTOMER_STATEMENTS + ":" + Actions.UPDATE;
    public static final String CUSTOMER_STATEMENTS_DELETE = Resources.CUSTOMER_STATEMENTS + ":" + Actions.DELETE;
    public static final String CUSTOMER_STATEMENTS_AUDIT = Resources.CUSTOMER_STATEMENTS + ":" + Actions.AUDIT;
    public static final String CUSTOMER_STATEMENTS_CONFIRM = Resources.CUSTOMER_STATEMENTS + ":" + Actions.CONFIRM;
    public static final String CUSTOMER_STATEMENTS_PRINT = Resources.CUSTOMER_STATEMENTS + ":" + Actions.PRINT;
    public static final String CUSTOMER_STATEMENTS_EXPORT = Resources.CUSTOMER_STATEMENTS + ":" + Actions.EXPORT;

    // 客户
    public static final String CUSTOMERS_READ = Resources.CUSTOMERS + ":" + Actions.READ;
    public static final String CUSTOMERS_CREATE = Resources.CUSTOMERS + ":" + Actions.CREATE;
    public static final String CUSTOMERS_UPDATE = Resources.CUSTOMERS + ":" + Actions.UPDATE;
    public static final String CUSTOMERS_DELETE = Resources.CUSTOMERS + ":" + Actions.DELETE;

    // 供应商
    public static final String SUPPLIERS_READ = Resources.SUPPLIERS + ":" + Actions.READ;
    public static final String SUPPLIERS_CREATE = Resources.SUPPLIERS + ":" + Actions.CREATE;
    public static final String SUPPLIERS_UPDATE = Resources.SUPPLIERS + ":" + Actions.UPDATE;
    public static final String SUPPLIERS_DELETE = Resources.SUPPLIERS + ":" + Actions.DELETE;

    // 仓库
    public static final String WAREHOUSES_READ = Resources.WAREHOUSES + ":" + Actions.READ;
    public static final String WAREHOUSES_CREATE = Resources.WAREHOUSES + ":" + Actions.CREATE;
    public static final String WAREHOUSES_UPDATE = Resources.WAREHOUSES + ":" + Actions.UPDATE;
    public static final String WAREHOUSES_DELETE = Resources.WAREHOUSES + ":" + Actions.DELETE;

    // 承运商
    public static final String CARRIERS_READ = Resources.CARRIERS + ":" + Actions.READ;
    public static final String CARRIERS_CREATE = Resources.CARRIERS + ":" + Actions.CREATE;
    public static final String CARRIERS_UPDATE = Resources.CARRIERS + ":" + Actions.UPDATE;
    public static final String CARRIERS_DELETE = Resources.CARRIERS + ":" + Actions.DELETE;

    // 项目
    public static final String PROJECTS_READ = Resources.PROJECTS + ":" + Actions.READ;
    public static final String PROJECTS_CREATE = Resources.PROJECTS + ":" + Actions.CREATE;
    public static final String PROJECTS_UPDATE = Resources.PROJECTS + ":" + Actions.UPDATE;
    public static final String PROJECTS_DELETE = Resources.PROJECTS + ":" + Actions.DELETE;

    // 报价单
    public static final String QUOTE_SHEETS_READ = Resources.QUOTE_SHEETS + ":" + Actions.READ;
    public static final String QUOTE_SHEETS_CREATE = Resources.QUOTE_SHEETS + ":" + Actions.CREATE;
    public static final String QUOTE_SHEETS_UPDATE = Resources.QUOTE_SHEETS + ":" + Actions.UPDATE;
    public static final String QUOTE_SHEETS_DELETE = Resources.QUOTE_SHEETS + ":" + Actions.DELETE;
    public static final String QUOTE_SHEETS_PRINT = Resources.QUOTE_SHEETS + ":" + Actions.PRINT;
    public static final String QUOTE_SHEETS_EXPORT = Resources.QUOTE_SHEETS + ":" + Actions.EXPORT;

    // 钢材报价
    public static final String STEEL_QUOTES_READ = Resources.STEEL_QUOTES + ":" + Actions.READ;
    public static final String STEEL_QUOTES_CREATE = Resources.STEEL_QUOTES + ":" + Actions.CREATE;
    public static final String STEEL_QUOTES_UPDATE = Resources.STEEL_QUOTES + ":" + Actions.UPDATE;
    public static final String STEEL_QUOTES_DELETE = Resources.STEEL_QUOTES + ":" + Actions.DELETE;
    public static final String STEEL_QUOTES_PRINT = Resources.STEEL_QUOTES + ":" + Actions.PRINT;
    public static final String STEEL_QUOTES_EXPORT = Resources.STEEL_QUOTES + ":" + Actions.EXPORT;

    // 采购订单
    public static final String PURCHASE_ORDERS_READ = Resources.PURCHASE_ORDERS + ":" + Actions.READ;
    public static final String PURCHASE_ORDERS_CREATE = Resources.PURCHASE_ORDERS + ":" + Actions.CREATE;
    public static final String PURCHASE_ORDERS_UPDATE = Resources.PURCHASE_ORDERS + ":" + Actions.UPDATE;
    public static final String PURCHASE_ORDERS_DELETE = Resources.PURCHASE_ORDERS + ":" + Actions.DELETE;
    public static final String PURCHASE_ORDERS_AUDIT = Resources.PURCHASE_ORDERS + ":" + Actions.AUDIT;
    public static final String PURCHASE_ORDERS_UNAUDIT = Resources.PURCHASE_ORDERS + ":" + Actions.UNAUDIT;
    public static final String PURCHASE_ORDERS_PRINT = Resources.PURCHASE_ORDERS + ":" + Actions.PRINT;
    public static final String PURCHASE_ORDERS_EXPORT = Resources.PURCHASE_ORDERS + ":" + Actions.EXPORT;

    // 采购入库
    public static final String PURCHASE_INBOUNDS_READ = Resources.PURCHASE_INBOUNDS + ":" + Actions.READ;
    public static final String PURCHASE_INBOUNDS_CREATE = Resources.PURCHASE_INBOUNDS + ":" + Actions.CREATE;
    public static final String PURCHASE_INBOUNDS_UPDATE = Resources.PURCHASE_INBOUNDS + ":" + Actions.UPDATE;
    public static final String PURCHASE_INBOUNDS_DELETE = Resources.PURCHASE_INBOUNDS + ":" + Actions.DELETE;
    public static final String PURCHASE_INBOUNDS_AUDIT = Resources.PURCHASE_INBOUNDS + ":" + Actions.AUDIT;
    public static final String PURCHASE_INBOUNDS_UNAUDIT = Resources.PURCHASE_INBOUNDS + ":" + Actions.UNAUDIT;
    public static final String PURCHASE_INBOUNDS_PRINT = Resources.PURCHASE_INBOUNDS + ":" + Actions.PRINT;
    public static final String PURCHASE_INBOUNDS_EXPORT = Resources.PURCHASE_INBOUNDS + ":" + Actions.EXPORT;

    // 运费单
    public static final String FREIGHT_BILLS_READ = Resources.FREIGHT_BILLS + ":" + Actions.READ;
    public static final String FREIGHT_BILLS_CREATE = Resources.FREIGHT_BILLS + ":" + Actions.CREATE;
    public static final String FREIGHT_BILLS_UPDATE = Resources.FREIGHT_BILLS + ":" + Actions.UPDATE;
    public static final String FREIGHT_BILLS_DELETE = Resources.FREIGHT_BILLS + ":" + Actions.DELETE;
    public static final String FREIGHT_BILLS_AUDIT = Resources.FREIGHT_BILLS + ":" + Actions.AUDIT;
    public static final String FREIGHT_BILLS_PRINT = Resources.FREIGHT_BILLS + ":" + Actions.PRINT;

    // 物流对账单
    public static final String FREIGHT_STATEMENTS_READ = Resources.FREIGHT_STATEMENTS + ":" + Actions.READ;
    public static final String FREIGHT_STATEMENTS_CREATE = Resources.FREIGHT_STATEMENTS + ":" + Actions.CREATE;
    public static final String FREIGHT_STATEMENTS_UPDATE = Resources.FREIGHT_STATEMENTS + ":" + Actions.UPDATE;
    public static final String FREIGHT_STATEMENTS_DELETE = Resources.FREIGHT_STATEMENTS + ":" + Actions.DELETE;
    public static final String FREIGHT_STATEMENTS_AUDIT = Resources.FREIGHT_STATEMENTS + ":" + Actions.AUDIT;

    // 财务
    public static final String FINANCE_READ = Resources.FINANCE + ":" + Actions.READ;
    public static final String FINANCE_CREATE = Resources.FINANCE + ":" + Actions.CREATE;
    public static final String FINANCE_UPDATE = Resources.FINANCE + ":" + Actions.UPDATE;
    public static final String FINANCE_DELETE = Resources.FINANCE + ":" + Actions.DELETE;
    public static final String FINANCE_COMPLETE = Resources.FINANCE + ":" + Actions.COMPLETE;
    public static final String FINANCE_REBUILD = Resources.FINANCE + ":" + Actions.REBUILD;

    // 收款
    public static final String RECEIPTS_READ = Resources.RECEIPTS + ":" + Actions.READ;
    public static final String RECEIPTS_CREATE = Resources.RECEIPTS + ":" + Actions.CREATE;
    public static final String RECEIPTS_UPDATE = Resources.RECEIPTS + ":" + Actions.UPDATE;
    public static final String RECEIPTS_DELETE = Resources.RECEIPTS + ":" + Actions.DELETE;
    public static final String RECEIPTS_AUDIT = Resources.RECEIPTS + ":" + Actions.AUDIT;
    public static final String RECEIPTS_PRINT = Resources.RECEIPTS + ":" + Actions.PRINT;

    // 付款
    public static final String PAYMENTS_READ = Resources.PAYMENTS + ":" + Actions.READ;
    public static final String PAYMENTS_CREATE = Resources.PAYMENTS + ":" + Actions.CREATE;
    public static final String PAYMENTS_UPDATE = Resources.PAYMENTS + ":" + Actions.UPDATE;
    public static final String PAYMENTS_DELETE = Resources.PAYMENTS + ":" + Actions.DELETE;
    public static final String PAYMENTS_AUDIT = Resources.PAYMENTS + ":" + Actions.AUDIT;
    public static final String PAYMENTS_PRINT = Resources.PAYMENTS + ":" + Actions.PRINT;

    // 台账调整
    public static final String LEDGER_ADJUSTMENTS_READ = Resources.LEDGER_ADJUSTMENTS + ":" + Actions.READ;
    public static final String LEDGER_ADJUSTMENTS_CREATE = Resources.LEDGER_ADJUSTMENTS + ":" + Actions.CREATE;
    public static final String LEDGER_ADJUSTMENTS_UPDATE = Resources.LEDGER_ADJUSTMENTS + ":" + Actions.UPDATE;
    public static final String LEDGER_ADJUSTMENTS_DELETE = Resources.LEDGER_ADJUSTMENTS + ":" + Actions.DELETE;
    public static final String LEDGER_ADJUSTMENTS_AUDIT = Resources.LEDGER_ADJUSTMENTS + ":" + Actions.AUDIT;

    // 附件
    public static final String ATTACHMENTS_READ = Resources.ATTACHMENTS + ":" + Actions.READ;
    public static final String ATTACHMENTS_CREATE = Resources.ATTACHMENTS + ":" + Actions.CREATE;
    public static final String ATTACHMENTS_UPDATE = Resources.ATTACHMENTS + ":" + Actions.UPDATE;
    public static final String ATTACHMENTS_DELETE = Resources.ATTACHMENTS + ":" + Actions.DELETE;
    public static final String ATTACHMENTS_PREVIEW = Resources.ATTACHMENTS + ":" + Actions.PREVIEW;

    // 附件直传会话
    public static final String ATTACHMENT_UPLOAD_SESSIONS_CREATE =
            Resources.ATTACHMENT_UPLOAD_SESSIONS + ":" + Actions.CREATE;
    public static final String ATTACHMENT_UPLOAD_SESSIONS_COMPLETE =
            Resources.ATTACHMENT_UPLOAD_SESSIONS + ":" + Actions.COMPLETE;

    // 附件恢复清单导出
    public static final String ATTACHMENT_MANIFEST_EXPORTS_EXPORT =
            Resources.ATTACHMENT_MANIFEST_EXPORTS + ":" + Actions.EXPORT;

    // 资金流水
    public static final String CASH_LEDGER_READ = Resources.CASH_LEDGER + ":" + Actions.READ;
    public static final String CASH_LEDGER_EXPORT = Resources.CASH_LEDGER + ":" + Actions.EXPORT;

    // 钢材行情同步
    public static final String STEEL_QUOTE_SYNCS_READ = Resources.STEEL_QUOTE_SYNCS + ":" + Actions.READ;
    public static final String STEEL_QUOTE_SYNCS_SYNC = Resources.STEEL_QUOTE_SYNCS + ":" + Actions.SYNC;

    // 钢材行情补数
    public static final String STEEL_QUOTE_BACKFILLS_READ = Resources.STEEL_QUOTE_BACKFILLS + ":" + Actions.READ;
    public static final String STEEL_QUOTE_BACKFILLS_BACKFILL =
            Resources.STEEL_QUOTE_BACKFILLS + ":" + Actions.BACKFILL;

    // 钢材行情日历
    public static final String STEEL_QUOTE_CALENDARS_READ = Resources.STEEL_QUOTE_CALENDARS + ":" + Actions.READ;

    // 商品行情匹配
    public static final String MATERIAL_PRICE_MATCHES_READ = Resources.MATERIAL_PRICE_MATCHES + ":" + Actions.READ;

    // 打印模板
    public static final String PRINT_TEMPLATES_READ = Resources.PRINT_TEMPLATES + ":" + Actions.READ;
    public static final String PRINT_TEMPLATES_CREATE = Resources.PRINT_TEMPLATES + ":" + Actions.CREATE;
    public static final String PRINT_TEMPLATES_UPDATE = Resources.PRINT_TEMPLATES + ":" + Actions.UPDATE;
    public static final String PRINT_TEMPLATES_DELETE = Resources.PRINT_TEMPLATES + ":" + Actions.DELETE;

    // 打印导出
    public static final String PRINT_EXPORTS_PRINT = Resources.PRINT_EXPORTS + ":" + Actions.PRINT;

    // 打印预览
    public static final String PRINT_PREVIEWS_READ = Resources.PRINT_PREVIEWS + ":" + Actions.READ;

    // 结算主体
    public static final String COMPANY_SETTINGS_READ = Resources.COMPANY_SETTINGS + ":" + Actions.READ;
    public static final String COMPANY_SETTINGS_CREATE = Resources.COMPANY_SETTINGS + ":" + Actions.CREATE;
    public static final String COMPANY_SETTINGS_UPDATE = Resources.COMPANY_SETTINGS + ":" + Actions.UPDATE;
    public static final String COMPANY_SETTINGS_DELETE = Resources.COMPANY_SETTINGS + ":" + Actions.DELETE;

    // 首页看板
    public static final String DASHBOARD_READ = Resources.DASHBOARD + ":" + Actions.READ;

    // 操作日志
    public static final String OPERATION_LOGS_READ = Resources.OPERATION_LOGS + ":" + Actions.READ;

    // 全局搜索
    public static final String GLOBAL_SEARCH_READ = Resources.GLOBAL_SEARCH + ":" + Actions.READ;

    // 元数据
    public static final String META_READ = Resources.META + ":" + Actions.READ;

    // 基础资料编码签发
    public static final String CODE_ISSUANCES_READ = Resources.CODE_ISSUANCES + ":" + Actions.READ;
    public static final String CODE_ISSUANCES_CREATE = Resources.CODE_ISSUANCES + ":" + Actions.CREATE;

    // 用户账号
    public static final String USER_ACCOUNTS_READ = Resources.USER_ACCOUNTS + ":" + Actions.READ;
    /** 个人账号资料/密码维护（/account）。 */
    public static final String USER_ACCOUNTS_UPDATE = Resources.USER_ACCOUNTS + ":" + Actions.UPDATE;
    /** 管理员用户管理写权限（建号/编辑/停用/重置密码/删除）。 */
    public static final String USER_ACCOUNTS_WRITE = Resources.USER_ACCOUNTS + ":" + Actions.WRITE;

    // 角色管理（RBAC0）
    public static final String ROLES_READ = Resources.ROLES + ":" + Actions.READ;
    public static final String ROLES_WRITE = Resources.ROLES + ":" + Actions.WRITE;

    // 权限目录（RBAC0）
    public static final String PERMISSIONS_READ = Resources.PERMISSIONS + ":" + Actions.READ;

    // 系统
    /** 系统管理总权限（非标准动作，作为系统级伞形权限保留）。 */
    public static final String SYSTEM_ADMIN = "system:admin";
    public static final String SYSTEM_READ = Resources.SYSTEM + ":" + Actions.READ;
    public static final String SYSTEM_CREATE = Resources.SYSTEM + ":" + Actions.CREATE;
    public static final String SYSTEM_DELETE = Resources.SYSTEM + ":" + Actions.DELETE;
    public static final String SYSTEM_REBUILD = Resources.SYSTEM + ":" + Actions.REBUILD;

    // 字段级示例（本轮仅登记，不强制校验）
    public static final String SALES_ORDERS_READ_AMOUNT =
            Resources.SALES_ORDERS + ":" + Actions.READ + ":" + Fields.AMOUNT;
    public static final String SALES_ORDERS_UPDATE_UNIT_PRICE =
            Resources.SALES_ORDERS + ":" + Actions.UPDATE + ":" + Fields.UNIT_PRICE;
    public static final String INVENTORY_READ_COST =
            Resources.INVENTORY + ":" + Actions.READ + ":" + Fields.COST;

    private static final Set<String> ALL = Set.of(
            WILDCARD,
            SALES_ORDERS_READ, SALES_ORDERS_CREATE, SALES_ORDERS_UPDATE, SALES_ORDERS_DELETE,
            SALES_ORDERS_AUDIT, SALES_ORDERS_UNAUDIT, SALES_ORDERS_CONFIRM, SALES_ORDERS_COMPLETE,
            SALES_ORDERS_PRINT, SALES_ORDERS_EXPORT,
            SALES_CONTRACTS_READ, SALES_CONTRACTS_CREATE, SALES_CONTRACTS_UPDATE, SALES_CONTRACTS_DELETE,
            SALES_RETURNS_READ, SALES_RETURNS_CREATE, SALES_RETURNS_UPDATE, SALES_RETURNS_DELETE,
            SALES_RETURNS_AUDIT, SALES_RETURNS_UNAUDIT, SALES_RETURNS_PRINT, SALES_RETURNS_EXPORT,
            SALES_OUTBOUNDS_READ, SALES_OUTBOUNDS_CREATE, SALES_OUTBOUNDS_UPDATE, SALES_OUTBOUNDS_DELETE,
            SALES_OUTBOUNDS_AUDIT, SALES_OUTBOUNDS_UNAUDIT, SALES_OUTBOUNDS_PRINT, SALES_OUTBOUNDS_EXPORT,
            MATERIALS_READ, MATERIALS_CREATE, MATERIALS_UPDATE, MATERIALS_DELETE,
            MATERIAL_CATEGORIES_READ, MATERIAL_CATEGORIES_CREATE, MATERIAL_CATEGORIES_UPDATE,
            MATERIAL_CATEGORIES_DELETE, MATERIAL_EXPORTS_EXPORT,
            MATERIAL_IMPORTS_READ, MATERIAL_IMPORTS_IMPORT, MATERIAL_IMPORTS_PREVIEW,
            IMPORT_BATCHES_READ, IMPORT_BATCHES_ROLLBACK,
            INVENTORY_READ, INVENTORY_CREATE, INVENTORY_UPDATE, INVENTORY_DELETE,
            INVENTORY_BACKFILL, INVENTORY_REBUILD,
            CUSTOMER_STATEMENTS_READ, CUSTOMER_STATEMENTS_CREATE, CUSTOMER_STATEMENTS_UPDATE,
            CUSTOMER_STATEMENTS_DELETE, CUSTOMER_STATEMENTS_AUDIT, CUSTOMER_STATEMENTS_CONFIRM,
            CUSTOMER_STATEMENTS_PRINT, CUSTOMER_STATEMENTS_EXPORT,
            CUSTOMERS_READ, CUSTOMERS_CREATE, CUSTOMERS_UPDATE, CUSTOMERS_DELETE,
            SUPPLIERS_READ, SUPPLIERS_CREATE, SUPPLIERS_UPDATE, SUPPLIERS_DELETE,
            WAREHOUSES_READ, WAREHOUSES_CREATE, WAREHOUSES_UPDATE, WAREHOUSES_DELETE,
            CARRIERS_READ, CARRIERS_CREATE, CARRIERS_UPDATE, CARRIERS_DELETE,
            PROJECTS_READ, PROJECTS_CREATE, PROJECTS_UPDATE, PROJECTS_DELETE,
            QUOTE_SHEETS_READ, QUOTE_SHEETS_CREATE, QUOTE_SHEETS_UPDATE, QUOTE_SHEETS_DELETE,
            QUOTE_SHEETS_PRINT, QUOTE_SHEETS_EXPORT,
            STEEL_QUOTES_READ, STEEL_QUOTES_CREATE, STEEL_QUOTES_UPDATE, STEEL_QUOTES_DELETE,
            STEEL_QUOTES_PRINT, STEEL_QUOTES_EXPORT,
            PURCHASE_ORDERS_READ, PURCHASE_ORDERS_CREATE, PURCHASE_ORDERS_UPDATE, PURCHASE_ORDERS_DELETE,
            PURCHASE_ORDERS_AUDIT, PURCHASE_ORDERS_UNAUDIT, PURCHASE_ORDERS_PRINT, PURCHASE_ORDERS_EXPORT,
            PURCHASE_INBOUNDS_READ, PURCHASE_INBOUNDS_CREATE, PURCHASE_INBOUNDS_UPDATE, PURCHASE_INBOUNDS_DELETE,
            PURCHASE_INBOUNDS_AUDIT, PURCHASE_INBOUNDS_UNAUDIT, PURCHASE_INBOUNDS_PRINT, PURCHASE_INBOUNDS_EXPORT,
            FREIGHT_BILLS_READ, FREIGHT_BILLS_CREATE, FREIGHT_BILLS_UPDATE, FREIGHT_BILLS_DELETE,
            FREIGHT_BILLS_AUDIT, FREIGHT_BILLS_PRINT,
            FREIGHT_STATEMENTS_READ, FREIGHT_STATEMENTS_CREATE, FREIGHT_STATEMENTS_UPDATE,
            FREIGHT_STATEMENTS_DELETE, FREIGHT_STATEMENTS_AUDIT,
            FINANCE_READ, FINANCE_CREATE, FINANCE_UPDATE, FINANCE_DELETE,
            FINANCE_COMPLETE, FINANCE_REBUILD,
            RECEIPTS_READ, RECEIPTS_CREATE, RECEIPTS_UPDATE, RECEIPTS_DELETE, RECEIPTS_AUDIT, RECEIPTS_PRINT,
            PAYMENTS_READ, PAYMENTS_CREATE, PAYMENTS_UPDATE, PAYMENTS_DELETE, PAYMENTS_AUDIT, PAYMENTS_PRINT,
            LEDGER_ADJUSTMENTS_READ, LEDGER_ADJUSTMENTS_CREATE, LEDGER_ADJUSTMENTS_UPDATE,
            LEDGER_ADJUSTMENTS_DELETE, LEDGER_ADJUSTMENTS_AUDIT,
            ATTACHMENTS_READ, ATTACHMENTS_CREATE, ATTACHMENTS_UPDATE, ATTACHMENTS_DELETE, ATTACHMENTS_PREVIEW,
            ATTACHMENT_UPLOAD_SESSIONS_CREATE, ATTACHMENT_UPLOAD_SESSIONS_COMPLETE,
            ATTACHMENT_MANIFEST_EXPORTS_EXPORT,
            CASH_LEDGER_READ, CASH_LEDGER_EXPORT,
            STEEL_QUOTE_SYNCS_READ, STEEL_QUOTE_SYNCS_SYNC,
            STEEL_QUOTE_BACKFILLS_READ, STEEL_QUOTE_BACKFILLS_BACKFILL,
            STEEL_QUOTE_CALENDARS_READ, MATERIAL_PRICE_MATCHES_READ,
            PRINT_TEMPLATES_READ, PRINT_TEMPLATES_CREATE, PRINT_TEMPLATES_UPDATE, PRINT_TEMPLATES_DELETE,
            PRINT_EXPORTS_PRINT, PRINT_PREVIEWS_READ,
            COMPANY_SETTINGS_READ, COMPANY_SETTINGS_CREATE, COMPANY_SETTINGS_UPDATE, COMPANY_SETTINGS_DELETE,
            DASHBOARD_READ, OPERATION_LOGS_READ, GLOBAL_SEARCH_READ, META_READ,
            CODE_ISSUANCES_READ, CODE_ISSUANCES_CREATE,
            USER_ACCOUNTS_READ, USER_ACCOUNTS_UPDATE, USER_ACCOUNTS_WRITE,
            ROLES_READ, ROLES_WRITE, PERMISSIONS_READ,
            SYSTEM_ADMIN, SYSTEM_READ, SYSTEM_CREATE, SYSTEM_DELETE, SYSTEM_REBUILD,
            SALES_ORDERS_READ_AMOUNT, SALES_ORDERS_UPDATE_UNIT_PRICE, INVENTORY_READ_COST
    );

    private PermissionCodes() {
    }

    /** 运行期拼接 {@code 资源:动作} 权限码，避免手写拼接导致拼写漂移。 */
    public static String of(String resource, String action) {
        requireText(resource, "资源");
        requireText(action, "动作");
        return resource + ":" + action;
    }

    /** 运行期拼接 {@code 资源:动作:字段} 字段级权限码（本轮仅登记，不强制校验）。 */
    public static String of(String resource, String action, String field) {
        requireText(field, "字段");
        return of(resource, action) + ":" + field;
    }

    /** 构造 {@code 资源:*} 资源级通配权限码。 */
    public static String ofResourceWildcard(String resource) {
        requireText(resource, "资源");
        return resource + ":" + Actions.WILDCARD;
    }

    /** 返回目录内全部权限码，供默认 {@link AuthorityProvider} 与测试使用。 */
    public static Set<String> all() {
        return ALL;
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("权限码的" + label + "不能为空");
        }
    }
}
