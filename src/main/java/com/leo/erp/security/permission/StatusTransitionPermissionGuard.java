package com.leo.erp.security.permission;

import com.leo.erp.common.support.DocumentStatus;
import com.leo.erp.common.support.ModuleKeys;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.leo.erp.security.support.SecurityPrincipal;

import java.util.Map;

/**
 * 状态变更的权限门禁：当通用 {@code PATCH /{id}/status} 执行「审核 / 反审核 / 确认」这类
 * <b>受控业务动作</b>时，强制要求对应动作权限，而不仅是 {@code 资源:update}。
 *
 * <p><b>背景：</b>受控动作本应具备独立权限码（如 {@code sales-orders:audit}），但通用状态端点
 * 过去只校验 {@code 资源:update}，导致仅持 {@code 资源:update} 的主体可绕过 {@code :audit}
 * 完成审核。本门禁在服务层状态变更的唯一收口处补齐校验，使权限目录中登记的
 * {@code :audit}/{@code :unaudit}/{@code :confirm} 真正生效。</p>
 *
 * <p><b>口径：</b>
 * <ul>
 *   <li>目标状态为「已审核」→ 要求 {@code 资源:audit}；</li>
 *   <li>自「已审核」退回「草稿」(反审核) → 要求 {@code 资源:unaudit}；</li>
 *   <li>目标状态为「已确认」/ 自「已确认」退回「待确认」(撤销确认) → 要求 {@code 资源:confirm}；</li>
 *   <li>目标状态为「完成销售」/ 自「完成销售」退回「送货核验」(撤销完成) → 要求 {@code 资源:complete}；</li>
 *   <li>未登记受控动作权限的资源(如仅用 update 的普通状态)不加额外要求，保持既有行为。</li>
 * </ul>
 * 通配 {@code *} 与 {@code 资源:*} 语义由 {@link PermissionChecker} 统一处理；
 * 服务层调用（内部/系统调用、单元测试）无已认证主体时跳过，交由上层 {@code @RequirePermission} 负责；
 * HTTP 入口调用必须走 {@link #requireForTransitionAtEntry}，无已认证主体直接 403（fail-closed）。</p>
 */
public final class StatusTransitionPermissionGuard {

    /** 目标状态为「已审核」时所需的动作权限。 */
    private static final Map<String, String> AUDIT_PERMISSIONS = Map.ofEntries(
            Map.entry(ModuleKeys.SALES_ORDER, PermissionCodes.SALES_ORDERS_AUDIT),
            Map.entry(ModuleKeys.SALES_OUTBOUND, PermissionCodes.SALES_OUTBOUNDS_AUDIT),
            Map.entry(ModuleKeys.SALES_RETURN, PermissionCodes.SALES_RETURNS_AUDIT),
            Map.entry(ModuleKeys.PURCHASE_ORDER, PermissionCodes.PURCHASE_ORDERS_AUDIT),
            Map.entry(ModuleKeys.PURCHASE_INBOUND, PermissionCodes.PURCHASE_INBOUNDS_AUDIT),
            Map.entry(ModuleKeys.FREIGHT_BILL, PermissionCodes.FREIGHT_BILLS_AUDIT),
            Map.entry(ModuleKeys.FREIGHT_STATEMENT, PermissionCodes.FREIGHT_STATEMENTS_AUDIT),
            Map.entry(ModuleKeys.RECEIPT, PermissionCodes.RECEIPTS_AUDIT),
            Map.entry(ModuleKeys.PAYMENT, PermissionCodes.PAYMENTS_AUDIT)
    );

    /** 自「已审核」退回「草稿」(反审核) 时所需的动作权限。 */
    private static final Map<String, String> UNAUDIT_PERMISSIONS = Map.ofEntries(
            Map.entry(ModuleKeys.SALES_ORDER, PermissionCodes.SALES_ORDERS_UNAUDIT),
            Map.entry(ModuleKeys.SALES_OUTBOUND, PermissionCodes.SALES_OUTBOUNDS_UNAUDIT),
            Map.entry(ModuleKeys.SALES_RETURN, PermissionCodes.SALES_RETURNS_UNAUDIT),
            Map.entry(ModuleKeys.PURCHASE_ORDER, PermissionCodes.PURCHASE_ORDERS_UNAUDIT),
            Map.entry(ModuleKeys.PURCHASE_INBOUND, PermissionCodes.PURCHASE_INBOUNDS_UNAUDIT)
    );

    /** 目标状态为「已确认」时所需的动作权限。 */
    private static final Map<String, String> CONFIRM_PERMISSIONS = Map.of(
            ModuleKeys.CUSTOMER_STATEMENT, PermissionCodes.CUSTOMER_STATEMENTS_CONFIRM
    );

    /** 目标状态为「完成销售」时所需的动作权限(仅有独立权限码的资源)。 */
    private static final Map<String, String> COMPLETE_PERMISSIONS = Map.of(
            ModuleKeys.SALES_ORDER, PermissionCodes.SALES_ORDERS_COMPLETE
    );

    /**
     * 「撤销确认」反向迁移（已确认 → 待确认）所需的动作权限。
     *
     * <p>同一状态迁移存在两条写路径时，反向路径必须要求与正向相同的动作权限，
     * 否则只持 {@code :update} 的主体可以经 PATCH 把已确认对账单退回待确认。</p>
     */
    private static final Map<String, String> REVERSE_CONFIRM_PERMISSIONS = Map.of(
            ModuleKeys.CUSTOMER_STATEMENT, PermissionCodes.CUSTOMER_STATEMENTS_CONFIRM
    );

    /** 「撤销完成销售」反向迁移（完成销售 → 送货核验）所需的动作权限，口径同正向完成。 */
    private static final Map<String, String> REVERSE_COMPLETE_PERMISSIONS = Map.of(
            ModuleKeys.SALES_ORDER, PermissionCodes.SALES_ORDERS_COMPLETE
    );

    private StatusTransitionPermissionGuard() {
    }

    /**
     * 校验状态变更所需的受控动作权限；不满足时抛出 403。
     *
     * @param moduleKey  单据模块键(如 {@code sales-order})
     * @param fromStatus 当前状态(未知可传 null, 仅影响反审核判定)
     * @param toStatus   目标状态
     */
    public static void requireForTransition(String moduleKey, String fromStatus, String toStatus) {
        if (!hasAuthenticatedPrincipal()) {
            return;
        }
        String required = resolveRequiredPermission(moduleKey, fromStatus, toStatus);
        if (required != null && !PermissionChecker.hasCurrent(required)) {
            throw new org.springframework.security.access.AccessDeniedException("缺少权限: " + required);
        }
    }

    static String resolveRequiredPermission(String moduleKey, String fromStatus, String toStatus) {
        if (moduleKey == null || toStatus == null) {
            return null;
        }
        if (DocumentStatus.AUDITED.label().equals(toStatus)) {
            return AUDIT_PERMISSIONS.get(moduleKey);
        }
        if (DocumentStatus.AUDITED.label().equals(fromStatus)
                && DocumentStatus.DRAFT.label().equals(toStatus)) {
            return UNAUDIT_PERMISSIONS.get(moduleKey);
        }
        if (DocumentStatus.CONFIRMED.label().equals(toStatus)) {
            return CONFIRM_PERMISSIONS.get(moduleKey);
        }
        if (DocumentStatus.CONFIRMED.label().equals(fromStatus)
                && DocumentStatus.PENDING_CONFIRM.label().equals(toStatus)) {
            return REVERSE_CONFIRM_PERMISSIONS.get(moduleKey);
        }
        if (DocumentStatus.SALES_COMPLETED.label().equals(toStatus)) {
            return COMPLETE_PERMISSIONS.get(moduleKey);
        }
        if (DocumentStatus.SALES_COMPLETED.label().equals(fromStatus)
                && DocumentStatus.DELIVERY_VERIFICATION.label().equals(toStatus)) {
            return REVERSE_COMPLETE_PERMISSIONS.get(moduleKey);
        }
        return null;
    }

    /**
     * HTTP 入口（{@code PATCH /{id}/status}）的 fail-closed 门禁。
     *
     * <p>与 {@link #requireForTransition} 的区别只有两点：</p>
     * <ol>
     *   <li><b>无已认证主体直接 403</b>：服务层门禁对内部/系统调用选择跳过，入口不能沿用该语义，
     *       否则「入口放过 + 服务层跳过」会叠加成双重失效；</li>
     *   <li>入口只能拿到目标状态（当前状态需查库），因此仅覆盖只依赖目标状态的规则
     *       （目标已审核/已确认/完成销售）；依赖起始状态的反审核、撤销确认等规则仍由服务层
     *       拿到当前状态后校验 —— 两层使用同一张迁移表，不重复维护口径。</li>
     * </ol>
     *
     * @param moduleKey 单据模块键
     * @param toStatus  目标状态
     */
    public static void requireForTransitionAtEntry(String moduleKey, String toStatus) {
        if (!hasAuthenticatedPrincipal()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "状态变更需要已认证主体，已拒绝未认证的入口调用");
        }
        String required = resolveRequiredPermission(moduleKey, null, toStatus);
        if (required != null && !PermissionChecker.hasCurrent(required)) {
            throw new org.springframework.security.access.AccessDeniedException("缺少权限: " + required);
        }
    }

    private static boolean hasAuthenticatedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof SecurityPrincipal;
    }
}
