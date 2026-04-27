package com.leo.erp.security.permission;

import com.leo.erp.system.role.domain.entity.RolePermission;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class ResourcePermissionCatalog {

    public static final String READ = "read";
    public static final String CREATE = "create";
    public static final String UPDATE = "update";
    public static final String DELETE = "delete";
    public static final String AUDIT = "audit";
    public static final String EXPORT = "export";
    public static final String PRINT = "print";
    public static final String MANAGE_PERMISSIONS = "manage_permissions";

    public record ActionOption(
            String code,
            String title
    ) {
    }

    public record Entry(
            String code,
            String title,
            String group,
            List<String> menuCodes,
            List<String> pathPrefixes,
            List<ActionOption> actions,
            boolean businessResource
    ) {
    }

    private static final List<ActionOption> CRUD_ACTIONS = List.of(
            action(READ, "查看"),
            action(CREATE, "新增"),
            action(UPDATE, "编辑"),
            action(DELETE, "删除")
    );
    private static final List<ActionOption> BUSINESS_ACTIONS = List.of(
            action(READ, "查看"),
            action(CREATE, "新增"),
            action(UPDATE, "编辑"),
            action(DELETE, "删除"),
            action(AUDIT, "审核"),
            action(EXPORT, "导出"),
            action(PRINT, "打印")
    );
    private static final List<ActionOption> REPORT_ACTIONS = List.of(
            action(READ, "查看"),
            action(EXPORT, "导出"),
            action(PRINT, "打印")
    );
    private static final List<ActionOption> READ_ONLY_ACTIONS = List.of(
            action(READ, "查看")
    );

    private static final List<Entry> ENTRIES = List.of(
            entry("dashboard", "工作台", "工作台", false, List.of("dashboard"), List.of("/dashboard"), READ_ONLY_ACTIONS),
            entry("material", "商品资料", "主数据", true, List.of("materials"), List.of("/materials"), BUSINESS_ACTIONS),
            entry("supplier", "供应商资料", "主数据", true, List.of("suppliers"), List.of("/suppliers"), BUSINESS_ACTIONS),
            entry("customer", "客户资料", "主数据", true, List.of("customers"), List.of("/customers"), BUSINESS_ACTIONS),
            entry("carrier", "物流方资料", "主数据", true, List.of("carriers"), List.of("/carriers"), BUSINESS_ACTIONS),
            entry("warehouse", "仓库资料", "主数据", true, List.of("warehouses"), List.of("/warehouses"), BUSINESS_ACTIONS),
            entry("purchase-order", "采购订单", "采购", true, List.of("purchase-orders"), List.of("/purchase-orders"), BUSINESS_ACTIONS),
            entry("purchase-inbound", "采购入库", "采购", true, List.of("purchase-inbounds"), List.of("/purchase-inbounds"), BUSINESS_ACTIONS),
            entry("sales-order", "销售订单", "销售", true, List.of("sales-orders"), List.of("/sales-orders"), BUSINESS_ACTIONS),
            entry("sales-outbound", "销售出库", "销售", true, List.of("sales-outbounds"), List.of("/sales-outbounds"), BUSINESS_ACTIONS),
            entry("freight-bill", "物流单", "物流", true, List.of("freight-bills"), List.of("/freight-bills"), BUSINESS_ACTIONS),
            entry("purchase-contract", "采购合同", "合同", true, List.of("purchase-contracts"), List.of("/purchase-contracts"), BUSINESS_ACTIONS),
            entry("sales-contract", "销售合同", "合同", true, List.of("sales-contracts"), List.of("/sales-contracts"), BUSINESS_ACTIONS),
            entry("inventory-report", "商品库存报表", "报表", true, List.of("inventory-report"), List.of("/inventory-report"), REPORT_ACTIONS),
            entry("io-report", "出入库报表", "报表", true, List.of("io-report"), List.of("/io-report"), REPORT_ACTIONS),
            entry("pending-invoice-receipt-report", "未收票报表", "报表", true,
                    List.of("pending-invoice-receipt-report"), List.of("/pending-invoice-receipt-report"), REPORT_ACTIONS),
            entry("supplier-statement", "供应商对账单", "对账", true, List.of("supplier-statements"), List.of("/supplier-statements"), BUSINESS_ACTIONS),
            entry("customer-statement", "客户对账单", "对账", true, List.of("customer-statements"), List.of("/customer-statements"), BUSINESS_ACTIONS),
            entry("freight-statement", "物流对账单", "对账", true, List.of("freight-statements"), List.of("/freight-statements"), BUSINESS_ACTIONS),
            entry("receipt", "收款单", "财务", true, List.of("receipts"), List.of("/receipts"), BUSINESS_ACTIONS),
            entry("payment", "付款单", "财务", true, List.of("payments"), List.of("/payments"), BUSINESS_ACTIONS),
            entry("invoice-receipt", "收票单", "财务", true, List.of("invoice-receipts"), List.of("/invoice-receipts"), BUSINESS_ACTIONS),
            entry("invoice-issue", "开票单", "财务", true, List.of("invoice-issues"), List.of("/invoice-issues"), BUSINESS_ACTIONS),
            entry("receivable-payable", "应收应付", "财务", true, List.of("receivables-payables"), List.of("/receivables-payables"), READ_ONLY_ACTIONS),
            entry("general-setting", "通用设置", "系统", false, List.of("general-settings"),
                    List.of("/general-settings", "/general-settings/upload-rule", "/upload-rules/page"), List.of(action(READ, "查看"), action(UPDATE, "编辑"))),
            entry("company-setting", "公司信息", "系统", false, List.of("company-settings"), List.of("/company-settings"), CRUD_ACTIONS),
            entry("operation-log", "操作日志", "系统", false, List.of("operation-logs"), List.of("/operation-logs"), READ_ONLY_ACTIONS),
            entry("department", "部门", "系统", false, List.of("departments"), List.of("/departments"), CRUD_ACTIONS),
            entry("user-account", "用户账户", "系统", false, List.of("user-accounts"), List.of("/user-accounts"), CRUD_ACTIONS),
            entry("permission", "权限管理", "系统", false, List.of("permission-management"), List.of("/permission-management"), READ_ONLY_ACTIONS),
            entry("role", "角色", "系统", false, List.of("role-settings", "role-action-editor"),
                    List.of("/role-settings", "/role-action-editor"), List.of(
                            action(READ, "查看"),
                            action(CREATE, "新增"),
                            action(UPDATE, "编辑"),
                            action(DELETE, "删除"),
                            action(MANAGE_PERMISSIONS, "配置权限")
                    )),
            entry("database", "数据库管理", "系统", false, List.of("database-management"),
                    List.of("/database-management", "/system/database"), List.of(action(READ, "查看"), action(UPDATE, "编辑"), action(EXPORT, "导出"))),
            entry("session", "会话管理", "系统", false, List.of("session-management"), List.of("/auth/refresh-tokens"), List.of(action(READ, "查看"), action(UPDATE, "编辑"))),
            entry("api-key", "API Key 管理", "系统", false, List.of("api-key-management"), List.of("/auth/api-keys"), List.of(action(READ, "查看"), action(CREATE, "新增"), action(UPDATE, "编辑"))),
            entry("security-key", "安全密钥管理", "系统", false, List.of("security-keys"), List.of("/system/security-keys"), List.of(action(READ, "查看"), action(UPDATE, "编辑"))),
            entry("print-template", "打印模板", "系统", false, List.of("print-templates"), List.of("/print-templates"), CRUD_ACTIONS)
    );

    private static final Map<String, Entry> ENTRIES_BY_CODE = ENTRIES.stream()
            .collect(Collectors.toMap(Entry::code, entry -> entry, (left, right) -> left, LinkedHashMap::new));

    private static final Map<String, Entry> ENTRIES_BY_MENU_CODE = ENTRIES.stream()
            .flatMap(entry -> entry.menuCodes().stream().map(menuCode -> Map.entry(normalizeKey(menuCode), entry)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));

    private static final List<ActionOption> ACTION_OPTIONS = ENTRIES.stream()
            .flatMap(entry -> entry.actions().stream())
            .collect(Collectors.toMap(ActionOption::code, option -> option, (left, right) -> left, LinkedHashMap::new))
            .values()
            .stream()
            .toList();

    private ResourcePermissionCatalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Optional<Entry> find(String resourceCode) {
        return Optional.ofNullable(ENTRIES_BY_CODE.get(normalizeKey(resourceCode)));
    }

    public static Set<String> allowedResourceCodes() {
        return ENTRIES_BY_CODE.keySet();
    }

    public static List<ActionOption> actionOptions() {
        return ACTION_OPTIONS;
    }

    public static boolean isKnownResource(String resourceCode) {
        return ENTRIES_BY_CODE.containsKey(normalizeKey(resourceCode));
    }

    public static boolean isBusinessResource(String resourceCode) {
        return find(resourceCode).map(Entry::businessResource).orElse(false);
    }

    public static boolean isKnownAction(String actionCode) {
        String normalized = normalizeAction(actionCode);
        return ACTION_OPTIONS.stream().anyMatch(action -> action.code().equals(normalized));
    }

    public static boolean isAllowed(String resourceCode, String actionCode) {
        String normalizedAction = normalizeAction(actionCode);
        return find(resourceCode)
                .map(entry -> entry.actions().stream().anyMatch(action -> action.code().equals(normalizedAction)))
                .orElse(false);
    }

    public static String resourceTitle(String resourceCode) {
        return find(resourceCode).map(Entry::title).orElse(resourceCode);
    }

    public static String actionTitle(String resourceCode, String actionCode) {
        String normalizedAction = normalizeAction(actionCode);
        return find(resourceCode)
                .flatMap(entry -> entry.actions().stream()
                        .filter(action -> action.code().equals(normalizedAction))
                        .findFirst())
                .map(ActionOption::title)
                .orElse(normalizedAction);
    }

    public static Optional<String> resolveResourceByMenuCode(String rawMenuCode) {
        return Optional.ofNullable(ENTRIES_BY_MENU_CODE.get(normalizeKey(rawMenuCode))).map(Entry::code);
    }

    public static Optional<String> resolveResourceByPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return Optional.empty();
        }
        String path = rawPath.trim();
        if (path.matches("^/role-settings/[^/]+/permissions(?:/.*)?$")
                || path.matches("^/role-settings/[^/]+/actions(?:/.*)?$")) {
            return Optional.of("role");
        }
        return ENTRIES.stream()
                .filter(entry -> entry.pathPrefixes().stream().anyMatch(prefix ->
                        path.equals(prefix) || path.startsWith(prefix + "/")))
                .max(Comparator.comparingInt(entry -> longestPrefixLength(entry.pathPrefixes(), path)))
                .map(Entry::code);
    }

    public static Set<String> resolveVisibleMenuCodes(Map<String, Set<String>> permissionMap) {
        Set<String> menuCodes = new LinkedHashSet<>();
        if (permissionMap == null || permissionMap.isEmpty()) {
            return menuCodes;
        }
        permissionMap.forEach((resource, actions) -> {
            if (actions == null || !actions.contains(READ)) {
                return;
            }
            find(resource).ifPresent(entry -> menuCodes.addAll(entry.menuCodes()));
        });
        return menuCodes;
    }

    public static List<String> actionsForResource(String resourceCode) {
        return find(resourceCode)
                .map(entry -> entry.actions().stream().map(ActionOption::code).toList())
                .orElse(List.of());
    }

    public static String normalizeResource(String resourceCode) {
        return normalizeKey(resourceCode);
    }

    public static final String SCOPE_ALL = "all";
    public static final String SCOPE_DEPARTMENT = "department";
    public static final String SCOPE_CUSTOM = "custom";
    public static final String SCOPE_SELF = "self";

    public static String normalizeDataScope(String value) {
        if (value == null || value.isBlank()) {
            return SCOPE_SELF;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "全部数据", "全部", "all" -> SCOPE_ALL;
            case "本部门", "department" -> SCOPE_DEPARTMENT;
            case "自定义范围", "custom" -> SCOPE_CUSTOM;
            case "本人", "self" -> SCOPE_SELF;
            default -> SCOPE_SELF;
        };
    }

    public static String broaderDataScope(String left, String right) {
        return dataScopeRank(left) >= dataScopeRank(right) ? left : right;
    }

    private static int dataScopeRank(String value) {
        return switch (value) {
            case SCOPE_ALL -> 4;
            case SCOPE_DEPARTMENT -> 2;
            default -> 1;
        };
    }

    public static String normalizeAction(String actionCode) {
        String normalized = normalizeKey(actionCode);
        return switch (normalized) {
            case "view" -> READ;
            case "edit" -> UPDATE;
            default -> normalized;
        };
    }

    private static int longestPrefixLength(List<String> prefixes, String path) {
        return prefixes.stream()
                .filter(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"))
                .mapToInt(String::length)
                .max()
                .orElse(0);
    }

    private static ActionOption action(String code, String title) {
        return new ActionOption(code, title);
    }

    private static Entry entry(String code,
                               String title,
                               String group,
                               boolean businessResource,
                               List<String> menuCodes,
                               List<String> pathPrefixes,
                               List<ActionOption> actions) {
        return new Entry(code, title, group, List.copyOf(menuCodes), List.copyOf(pathPrefixes), List.copyOf(actions), businessResource);
    }

    public static String buildPermissionSummary(Collection<RolePermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return "";
        }
        List<String> labels = permissions.stream()
                .sorted(Comparator.comparing(RolePermission::getResourceCode).thenComparing(RolePermission::getActionCode))
                .map(permission -> {
                    String resource = normalizeResource(permission.getResourceCode());
                    String action = normalizeAction(permission.getActionCode());
                    return resourceTitle(resource) + "-" + actionTitle(resource, action);
                })
                .distinct()
                .toList();
        if (labels.size() <= 6) {
            return String.join("、", labels);
        }
        return String.join("、", labels.subList(0, 6)) + " 等" + labels.size() + "项";
    }

    private static String normalizeKey(String value) {
        return String.valueOf(value == null ? "" : value)
                .trim()
                .replaceFirst("^/+", "")
                .toLowerCase(Locale.ROOT);
    }
}
