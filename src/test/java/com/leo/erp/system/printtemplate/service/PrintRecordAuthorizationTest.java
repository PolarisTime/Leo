package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.security.permission.PermissionChecker;
import com.leo.erp.security.permission.PermissionCodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 打印/预览模块级读取授权的单元测试。
 *
 * <p>回归背景：打印端点只校验全局 {@code print-exports:print} 与 {@code print-previews:read}，
 * 这两个权限码不区分业务模块。若服务层不追加模块读权限校验，任一持有全局打印权限的主体
 * 都可以传入任意 {@code moduleKey + recordId}，把无权模块的数据经打印/预览导出。</p>
 */
@ExtendWith(MockitoExtension.class)
class PrintRecordAuthorizationTest {

    @Mock
    private PermissionChecker permissionChecker;

    @InjectMocks
    private PrintRecordAuthorization authorization;

    @Test
    void requireReadable_shouldRequireModuleReadPermission() {
        assertThatCode(() -> authorization.requireReadable("sales-order")).doesNotThrowAnyException();
        verify(permissionChecker).require(PermissionCodes.SALES_ORDERS_READ);
    }

    @Test
    void requireReadable_shouldMapEachPrintableModuleToItsOwnReadPermission() {
        authorization.requireReadable("purchase-order");
        authorization.requireReadable("sales-order");
        authorization.requireReadable("customer-statement");

        verify(permissionChecker).require(PermissionCodes.PURCHASE_ORDERS_READ);
        verify(permissionChecker).require(PermissionCodes.SALES_ORDERS_READ);
        verify(permissionChecker).require(PermissionCodes.CUSTOMER_STATEMENTS_READ);
    }

    @Test
    void requireReadable_shouldRejectUnregisteredModuleInsteadOfSilentlyAllowing() {
        // 未登记模块必须失败闭合：新增可打印模块却忘记登记权限码时，不能默认放行。
        assertThatThrownBy(() -> authorization.requireReadable("not-a-real-module"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登记读取权限");
        verifyNoInteractions(permissionChecker);
    }

    @Test
    void requireReadable_shouldPropagateAccessDenied() {
        doThrow(new AccessDeniedException("缺少权限: sales-orders:read"))
                .when(permissionChecker).require(PermissionCodes.SALES_ORDERS_READ);

        assertThatThrownBy(() -> authorization.requireReadable("sales-order"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireReadable_shouldCoverEveryPrintableModule() {
        // 与 print-runtime.json 的 modules 键保持一致：10 个可打印模块都必须能解析出读权限码。
        String[] printableModules = {
                "purchase-order", "purchase-inbound", "sales-order", "sales-outbound", "sales-return",
                "freight-bill", "customer-statement", "freight-statement", "receipt", "payment"
        };
        for (String moduleKey : printableModules) {
            assertThatCode(() -> authorization.requireReadable(moduleKey))
                    .as("可打印模块 %s 必须登记读取权限", moduleKey)
                    .doesNotThrowAnyException();
        }
    }
}
