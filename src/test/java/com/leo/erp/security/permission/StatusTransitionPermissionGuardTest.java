package com.leo.erp.security.permission;

import com.leo.erp.common.support.DocumentStatus;
import com.leo.erp.common.support.ModuleKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 状态变更受控动作权限门禁测试: 审核/反审核/确认/完成销售 需具备对应动作权限, 不得由 update 绕过。
 */
class StatusTransitionPermissionGuardTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        var authentication = new UsernamePasswordAuthenticationToken(
                com.leo.erp.security.support.SecurityPrincipal.authenticated(1L, "tester", 0L),
                "n/a",
                granted);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void audit_withoutAuditPermission_shouldBeRejected() {
        authenticate(PermissionCodes.SALES_ORDERS_UPDATE);

        assertThatThrownBy(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.SALES_ORDER, DocumentStatus.DRAFT.code(), DocumentStatus.AUDITED.code()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(PermissionCodes.SALES_ORDERS_AUDIT);
    }

    @Test
    void audit_withAuditPermission_shouldPass() {
        authenticate(PermissionCodes.SALES_ORDERS_AUDIT);

        assertThatCode(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.SALES_ORDER, DocumentStatus.DRAFT.code(), DocumentStatus.AUDITED.code()))
                .doesNotThrowAnyException();
    }

    @Test
    void audit_withResourceWildcard_shouldPass() {
        authenticate(PermissionCodes.ofResourceWildcard("sales-orders"));

        assertThatCode(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.SALES_ORDER, DocumentStatus.DRAFT.code(), DocumentStatus.AUDITED.code()))
                .doesNotThrowAnyException();
    }

    @Test
    void unaudit_withoutUnauditPermission_shouldBeRejected() {
        authenticate(PermissionCodes.SALES_ORDERS_UPDATE);

        assertThatThrownBy(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.SALES_ORDER, DocumentStatus.AUDITED.code(), DocumentStatus.DRAFT.code()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(PermissionCodes.SALES_ORDERS_UNAUDIT);
    }

    @Test
    void confirm_withoutConfirmPermission_shouldBeRejectedOnCustomerStatement() {
        authenticate(PermissionCodes.CUSTOMER_STATEMENTS_UPDATE);

        assertThatThrownBy(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.CUSTOMER_STATEMENT,
                DocumentStatus.PENDING_CONFIRM.code(),
                DocumentStatus.CONFIRMED.code()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(PermissionCodes.CUSTOMER_STATEMENTS_CONFIRM);
    }

    @Test
    void completeSales_withoutCompletePermission_shouldBeRejected() {
        authenticate(PermissionCodes.SALES_ORDERS_UPDATE);

        assertThatThrownBy(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.SALES_ORDER,
                DocumentStatus.DELIVERY_VERIFICATION.code(),
                DocumentStatus.SALES_COMPLETED.code()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(PermissionCodes.SALES_ORDERS_COMPLETE);
    }

    @Test
    void nonControlledTransition_shouldNotRequireActionPermission() {
        authenticate(PermissionCodes.SALES_ORDERS_UPDATE);

        // 交付核定 (已审核 -> 交付核定) 非受控动作, 保持 update 口径, 不额外要求。
        assertThatCode(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.SALES_ORDER,
                DocumentStatus.AUDITED.code(),
                DocumentStatus.DELIVERY_VERIFICATION.code()))
                .doesNotThrowAnyException();
    }

    @Test
    void unregisteredResourceAudit_shouldNotRequireActionPermission() {
        // 未登记 audit 权限的资源(如 sales-contract)不额外要求, 保持既有行为。
        authenticate(PermissionCodes.SALES_CONTRACTS_UPDATE);

        assertThatCode(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.SALES_CONTRACT, DocumentStatus.DRAFT.code(), DocumentStatus.AUDITED.code()))
                .doesNotThrowAnyException();
    }

    @Test
    void noAuthentication_shouldSkipGuard() {
        // 无认证主体(内部/系统调用)时跳过, 交由上层 @RequirePermission 负责。
        assertThatCode(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.SALES_ORDER, DocumentStatus.DRAFT.code(), DocumentStatus.AUDITED.code()))
                .doesNotThrowAnyException();
    }

    @Test
    void resolveRequiredPermission_mapsControlledActions() {
        assertThat(StatusTransitionPermissionGuard.resolveRequiredPermission(
                ModuleKeys.SALES_ORDER, DocumentStatus.DRAFT.code(), DocumentStatus.AUDITED.code()))
                .isEqualTo(PermissionCodes.SALES_ORDERS_AUDIT);
        assertThat(StatusTransitionPermissionGuard.resolveRequiredPermission(
                ModuleKeys.SALES_ORDER, DocumentStatus.AUDITED.code(), DocumentStatus.DRAFT.code()))
                .isEqualTo(PermissionCodes.SALES_ORDERS_UNAUDIT);
        assertThat(StatusTransitionPermissionGuard.resolveRequiredPermission(
                ModuleKeys.PAYMENT, DocumentStatus.DRAFT.code(), DocumentStatus.AUDITED.code()))
                .isEqualTo(PermissionCodes.PAYMENTS_AUDIT);
        // 物流单无独立反审核码: 反审核保持 update 口径(不额外要求)。
        assertThat(StatusTransitionPermissionGuard.resolveRequiredPermission(
                ModuleKeys.FREIGHT_BILL, DocumentStatus.AUDITED.code(), DocumentStatus.DRAFT.code()))
                .isNull();
    }

    /** 撤销确认（已确认 → 待确认）必须要求与正向确认相同的动作权限。 */
    @Test
    void reverseConfirm_withoutConfirmPermission_shouldBeRejected() {
        authenticate(PermissionCodes.CUSTOMER_STATEMENTS_UPDATE);

        assertThatThrownBy(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.CUSTOMER_STATEMENT,
                DocumentStatus.CONFIRMED.code(),
                DocumentStatus.PENDING_CONFIRM.code()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(PermissionCodes.CUSTOMER_STATEMENTS_CONFIRM);
    }

    /** 撤销完成销售（完成销售 → 送货核验）必须要求 complete 权限。 */
    @Test
    void reverseComplete_withoutCompletePermission_shouldBeRejected() {
        authenticate(PermissionCodes.SALES_ORDERS_UPDATE);

        assertThatThrownBy(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.SALES_ORDER,
                DocumentStatus.SALES_COMPLETED.code(),
                DocumentStatus.DELIVERY_VERIFICATION.code()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(PermissionCodes.SALES_ORDERS_COMPLETE);
    }

    @Test
    void entry_auditWithoutAuditPermission_shouldBeRejected() {
        authenticate(PermissionCodes.SALES_ORDERS_UPDATE);

        assertThatThrownBy(() -> StatusTransitionPermissionGuard.requireForTransitionAtEntry(
                ModuleKeys.SALES_ORDER, DocumentStatus.AUDITED.code()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(PermissionCodes.SALES_ORDERS_AUDIT);
    }

    @Test
    void entry_auditWithAuditPermission_shouldPass() {
        authenticate(PermissionCodes.SALES_ORDERS_AUDIT);

        assertThatCode(() -> StatusTransitionPermissionGuard.requireForTransitionAtEntry(
                ModuleKeys.SALES_ORDER, DocumentStatus.AUDITED.code()))
                .doesNotThrowAnyException();
    }

    /** 入口 fail-closed：无已认证主体时不得像服务层那样跳过。 */
    @Test
    void entry_withoutAuthentication_shouldFailClosed() {
        assertThatThrownBy(() -> StatusTransitionPermissionGuard.requireForTransitionAtEntry(
                ModuleKeys.SALES_ORDER, DocumentStatus.AUDITED.code()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("已认证主体");
    }
}
