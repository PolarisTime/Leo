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
 *   <li>目标状态为「已确认」→ 要求 {@code 资源:confirm}；</li>
 *   <li>未登记受控动作权限的资源(如仅用 update 的普通状态)不加额外要求，保持既有行为。</li>
 * </ul>
 * 通配 {@code *} 与 {@code 资源:*} 语义由 {@link PermissionChecker} 统一处理；
 * 无已认证主体时(内部/系统调用或单元测试)跳过，交由上层 {@code @RequirePermission} 负责。</p>
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
            Map.entry(ModuleKeys.PAYMENT, PermissionCodes.PAYMENTS_AUDIT),
            Map.entry(ModuleKeys.LEDGER_ADJUSTMENT, PermissionCodes.LEDGER_ADJUSTMENTS_AUDIT)
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
        if (DocumentStatus.SALES_COMPLETED.label().equals(toStatus)) {
            return COMPLETE_PERMISSIONS.get(moduleKey);
        }
        return null;
    }

    private static boolean hasAuthenticatedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof SecurityPrincipal;
    }
}
