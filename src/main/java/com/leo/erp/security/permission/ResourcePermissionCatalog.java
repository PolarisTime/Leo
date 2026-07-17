package com.leo.erp.security.permission;

import com.leo.erp.system.role.domain.entity.RolePermission;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
            List<String> pathPrefixes,
            List<ActionOption> actions,
            boolean businessResource
    ) {
        public List<String> menuCodes() {
            return menuCodesFor(this);
        }
    }

    private static final Set<String> NO_MENU_RESOURCES = Set.of("user-account", "permission", "role");
    private static final Map<String, List<String>> EXTRA_MENU_CODES = Map.of(
            "material", List.of("material-categories", "material-category")
    );

    private static List<String> menuCodesFor(Entry entry) {
        if (NO_MENU_RESOURCES.contains(entry.code())) {
            return List.of();
        }
        List<String> extra = EXTRA_MENU_CODES.getOrDefault(entry.code(), List.of());
        if (extra.isEmpty()) {
            return List.of(entry.code());
        }
        List<String> codes = new java.util.ArrayList<>();
        codes.add(entry.code());
        codes.addAll(extra);
        return Collections.unmodifiableList(codes);
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
    private static final List<ActionOption> CONTROLLED_AUDIT_ACTIONS = List.of(
            action(READ, "查看"),
            action(AUDIT, "审核"),
            action(EXPORT, "导出"),
            action(PRINT, "打印")
    );
    private static final List<ActionOption> READ_ONLY_ACTIONS = List.of(
            action(READ, "查看")
    );

    public static final List<Entry> ENTRIES = List.of(
            entry("dashboard", "工作台", "工作台", false, List.of("/dashboard"), READ_ONLY_ACTIONS),
            entry("material", "商品资料", "主数据", true, List.of("/material", "/materials", "/material-categories", "/material-category"), BUSINESS_ACTIONS),
            entry("supplier", "供应商资料", "主数据", true, List.of("/supplier", "/suppliers"), BUSINESS_ACTIONS),
            entry("customer", "客户资料", "主数据", true, List.of("/customer", "/customers"), BUSINESS_ACTIONS),
            entry("project", "项目", "主数据", true, List.of("/project", "/projects"), CRUD_ACTIONS),
            entry("carrier", "物流方资料", "主数据", true, List.of("/carrier", "/carriers"), BUSINESS_ACTIONS),
            entry("warehouse", "仓库资料", "主数据", true, List.of("/warehouse", "/warehouses"), BUSINESS_ACTIONS),
            entry("purchase-order", "采购订单", "采购", true, List.of("/purchase-order", "/purchase-orders"), BUSINESS_ACTIONS),
            entry("purchase-inbound", "采购入库", "采购", true, List.of("/purchase-inbound", "/purchase-inbounds"), BUSINESS_ACTIONS),
            entry("sales-order", "销售订单", "销售", true, List.of("/sales-order", "/sales-orders"), BUSINESS_ACTIONS),
            entry("sales-outbound", "销售出库", "销售", true, List.of("/sales-outbound", "/sales-outbounds"), BUSINESS_ACTIONS),
            entry("freight-bill", "物流单", "物流", true, List.of("/freight-bill", "/freight-bills"), BUSINESS_ACTIONS),
            entry("inventory-report", "商品库存报表", "报表", true, List.of("/inventory-report"), REPORT_ACTIONS),
            entry("io-report", "出入库报表", "报表", true, List.of("/io-report"), REPORT_ACTIONS),
            entry("customer-statement", "客户对账单", "对账", true, List.of("/customer-statement", "/customer-statements"), BUSINESS_ACTIONS),
            entry("freight-statement", "物流对账单", "对账", true, List.of("/freight-statement", "/freight-statements"), BUSINESS_ACTIONS),
            entry("receipt", "收款单", "财务", true, List.of("/receipt", "/receipts"), BUSINESS_ACTIONS),
            entry("payment", "付款单", "财务", true, List.of("/payment", "/payments"), BUSINESS_ACTIONS),
            entry("ledger-adjustment", "台账调整单", "财务", false,
                    List.of("/ledger-adjustment", "/ledger-adjustments"), READ_ONLY_ACTIONS),
            entry("cash-ledger", "资金流水", "财务", false, List.of("/cash-ledger"), REPORT_ACTIONS),
            entry("general-setting", "通用设置", "系统", false,
                    List.of("/general-setting", "/general-settings", "/general-setting/upload-rule", "/general-settings/upload-rule"), List.of(action(READ, "查看"), action(UPDATE, "编辑"))),
            entry("company-setting", "结算主体管理", "主数据", false, List.of("/company-setting", "/company-settings"), CRUD_ACTIONS),
            entry("operation-log", "操作日志", "系统", false, List.of("/operation-log", "/operation-logs"), READ_ONLY_ACTIONS),
            entry("department", "部门", "主数据", false, List.of("/department", "/departments"), CRUD_ACTIONS),
            entry("user-account", "用户账户", "系统", false, List.of("/user-account", "/user-accounts"), CRUD_ACTIONS),
            entry("permission", "权限管理", "系统", false, List.of("/permission", "/permissions"), READ_ONLY_ACTIONS),
            entry("role", "角色", "系统", false,
                    List.of("/role-setting", "/role-settings", "/role-action-editor"), List.of(
                            action(READ, "查看"),
                            action(CREATE, "新增"),
                            action(UPDATE, "编辑"),
                            action(DELETE, "删除"),
                            action(MANAGE_PERMISSIONS, "配置权限")
                    )),
            entry("access-control", "访问控制", "系统", false,
                    List.of("/access-control"), READ_ONLY_ACTIONS),
            entry("session", "会话管理", "系统", false, List.of("/auth/refresh-token", "/auth/refresh-tokens"), List.of(action(READ, "查看"), action(UPDATE, "编辑"))),
            entry("api-key", "API Key 管理", "系统", false, List.of("/auth/api-key", "/auth/api-keys"), List.of(action(READ, "查看"), action(CREATE, "新增"), action(UPDATE, "编辑"))),
            entry("security-key", "安全密钥管理", "系统", false, List.of("/system/security-key", "/system/security-keys"), List.of(action(READ, "查看"), action(UPDATE, "编辑"))),
            entry("print-template", "打印模板", "系统", false, List.of("/print-template", "/print-templates"), CRUD_ACTIONS)
    );

    private static final Map<String, Entry> ENTRIES_BY_CODE = entriesByCode();

    private static final Map<String, Entry> ENTRIES_BY_MENU_CODE = entriesByMenuCode();

    private static final List<ActionOption> ACTION_OPTIONS = actionOptionsFromEntries();

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

    private static Map<String, Entry> entriesByCode() {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            entries.putIfAbsent(entry.code(), entry);
        }
        return Collections.unmodifiableMap(entries);
    }

    private static Map<String, Entry> entriesByMenuCode() {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            for (String menuCode : entry.menuCodes()) {
                entries.putIfAbsent(normalizeKey(menuCode), entry);
            }
        }
        return Collections.unmodifiableMap(entries);
    }

    private static List<ActionOption> actionOptionsFromEntries() {
        Map<String, ActionOption> actions = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            for (ActionOption action : entry.actions()) {
                actions.putIfAbsent(action.code(), action);
            }
        }
        return List.copyOf(actions.values());
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
        if (path.matches("^/role-settings?/[^/]+/permission(?:/.*)?$")
                || path.matches("^/role-settings?/[^/]+/action(?:/.*)?$")) {
            return Optional.of("role");
        }
        Entry matched = null;
        int matchedPrefixLength = 0;
        for (Entry entry : ENTRIES) {
            int prefixLength = longestPrefixLength(entry.pathPrefixes(), path);
            if (prefixLength > matchedPrefixLength) {
                matched = entry;
                matchedPrefixLength = prefixLength;
            }
        }
        return Optional.ofNullable(matched).map(Entry::code);
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
                               List<String> pathPrefixes,
                               List<ActionOption> actions) {
        return new Entry(code, title, group, List.copyOf(pathPrefixes), List.copyOf(actions), businessResource);
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

    public static java.util.Map<String, String> getAllResourceLabels() {
        java.util.LinkedHashMap<String, String> labels = new java.util.LinkedHashMap<>();
        ENTRIES.forEach(entry -> labels.put(entry.code(), entry.title()));
        return labels;
    }

    public static java.util.Map<String, String> getAllActionLabels() {
        java.util.LinkedHashMap<String, String> labels = new java.util.LinkedHashMap<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        ENTRIES.forEach(entry -> entry.actions().forEach(action -> {
            if (seen.add(action.code())) {
                labels.put(action.code(), action.title());
            }
        }));
        return labels;
    }

    private static String normalizeKey(String value) {
        return String.valueOf(value == null ? "" : value)
                .trim()
                .replaceFirst("^/+", "")
                .toLowerCase(Locale.ROOT);
    }
}
