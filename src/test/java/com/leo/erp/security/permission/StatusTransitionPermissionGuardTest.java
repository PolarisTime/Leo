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
                ModuleKeys.SALES_ORDER, DocumentStatus.DRAFT.label(), DocumentStatus.AUDITED.label()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(PermissionCodes.SALES_ORDERS_AUDIT);
    }

    @Test
    void audit_withAuditPermission_shouldPass() {
        authenticate(PermissionCodes.SALES_ORDERS_AUDIT);

        assertThatCode(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.SALES_ORDER, DocumentStatus.DRAFT.label(), DocumentStatus.AUDITED.label()))
                .doesNotThrowAnyException();
    }

    @Test
    void audit_withResourceWildcard_shouldPass() {
        authenticate(PermissionCodes.ofResourceWildcard("sales-orders"));

        assertThatCode(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.SALES_ORDER, DocumentStatus.DRAFT.label(), DocumentStatus.AUDITED.label()))
                .doesNotThrowAnyException();
    }

    @Test
    void unaudit_withoutUnauditPermission_shouldBeRejected() {
        authenticate(PermissionCodes.SALES_ORDERS_UPDATE);

        assertThatThrownBy(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.SALES_ORDER, DocumentStatus.AUDITED.label(), DocumentStatus.DRAFT.label()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(PermissionCodes.SALES_ORDERS_UNAUDIT);
    }

    @Test
    void confirm_withoutConfirmPermission_shouldBeRejectedOnCustomerStatement() {
        authenticate(PermissionCodes.CUSTOMER_STATEMENTS_UPDATE);

        assertThatThrownBy(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.CUSTOMER_STATEMENT,
                DocumentStatus.PENDING_CONFIRM.label(),
                DocumentStatus.CONFIRMED.label()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(PermissionCodes.CUSTOMER_STATEMENTS_CONFIRM);
    }

    @Test
    void completeSales_withoutCompletePermission_shouldBeRejected() {
        authenticate(PermissionCodes.SALES_ORDERS_UPDATE);

        assertThatThrownBy(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.SALES_ORDER,
                DocumentStatus.DELIVERY_VERIFICATION.label(),
                DocumentStatus.SALES_COMPLETED.label()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(PermissionCodes.SALES_ORDERS_COMPLETE);
    }

    @Test
    void nonControlledTransition_shouldNotRequireActionPermission() {
        authenticate(PermissionCodes.SALES_ORDERS_UPDATE);

        // 交付核定 (已审核 -> 交付核定) 非受控动作, 保持 update 口径, 不额外要求。
        assertThatCode(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.SALES_ORDER,
                DocumentStatus.AUDITED.label(),
                DocumentStatus.DELIVERY_VERIFICATION.label()))
                .doesNotThrowAnyException();
    }

    @Test
    void unregisteredResourceAudit_shouldNotRequireActionPermission() {
        // 未登记 audit 权限的资源(如 sales-contract)不额外要求, 保持既有行为。
        authenticate(PermissionCodes.SALES_CONTRACTS_UPDATE);

        assertThatCode(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.SALES_CONTRACT, DocumentStatus.DRAFT.label(), DocumentStatus.AUDITED.label()))
                .doesNotThrowAnyException();
    }

    @Test
    void noAuthentication_shouldSkipGuard() {
        // 无认证主体(内部/系统调用)时跳过, 交由上层 @RequirePermission 负责。
        assertThatCode(() -> StatusTransitionPermissionGuard.requireForTransition(
                ModuleKeys.SALES_ORDER, DocumentStatus.DRAFT.label(), DocumentStatus.AUDITED.label()))
                .doesNotThrowAnyException();
    }

    @Test
    void resolveRequiredPermission_mapsControlledActions() {
        assertThat(StatusTransitionPermissionGuard.resolveRequiredPermission(
                ModuleKeys.SALES_ORDER, DocumentStatus.DRAFT.label(), DocumentStatus.AUDITED.label()))
                .isEqualTo(PermissionCodes.SALES_ORDERS_AUDIT);
        assertThat(StatusTransitionPermissionGuard.resolveRequiredPermission(
                ModuleKeys.SALES_ORDER, DocumentStatus.AUDITED.label(), DocumentStatus.DRAFT.label()))
                .isEqualTo(PermissionCodes.SALES_ORDERS_UNAUDIT);
        assertThat(StatusTransitionPermissionGuard.resolveRequiredPermission(
                ModuleKeys.PAYMENT, DocumentStatus.DRAFT.label(), DocumentStatus.AUDITED.label()))
                .isEqualTo(PermissionCodes.PAYMENTS_AUDIT);
        // 物流单无独立反审核码: 反审核保持 update 口径(不额外要求)。
        assertThat(StatusTransitionPermissionGuard.resolveRequiredPermission(
                ModuleKeys.FREIGHT_BILL, DocumentStatus.AUDITED.label(), DocumentStatus.DRAFT.label()))
                .isNull();
    }
}
