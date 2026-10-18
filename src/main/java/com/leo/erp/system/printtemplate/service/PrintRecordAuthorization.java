package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.security.permission.PermissionChecker;
import com.leo.erp.security.permission.PermissionCodes;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 打印/预览的模块级读取授权。
 *
 * <p>打印端点只校验全局的 {@code print-exports:print} / {@code print-previews:read}，
 * 这两个权限码不区分业务模块：任一持有它们的主体可以传入任意 {@code moduleKey} 与
 * {@code recordId}，读取该模块任意一条记录的数据并渲染成文件。因此必须在服务层
 * 按 {@code moduleKey} 追加一次该模块的读取权限校验，否则打印会成为绕过
 * {@code sales-orders:read} 等模块读权限的数据出口。</p>
 *
 * <p>这与 {@code ModuleExportService} 对 {@code ModuleExportDefinition#readPermission()}
 * 的二次校验保持同一语义：导出/打印类端点除了「能导出」之外，还必须「能读该模块」。</p>
 */
@Component
class PrintRecordAuthorization {

    /**
     * 可打印模块到其读取权限码的映射。
     *
     * <p>键必须与 {@code print-runtime.json} 的 {@code modules} 键保持一致；
     * {@link #requireReadable(String)} 对未登记模块直接失败，避免新增可打印模块时
     * 因忘记登记权限码而静默放行。</p>
     */
    private static final Map<String, String> READ_PERMISSION_BY_MODULE = Map.of(
            ModuleKeys.PURCHASE_ORDER, PermissionCodes.PURCHASE_ORDERS_READ,
            ModuleKeys.PURCHASE_INBOUND, PermissionCodes.PURCHASE_INBOUNDS_READ,
            ModuleKeys.SALES_ORDER, PermissionCodes.SALES_ORDERS_READ,
            ModuleKeys.SALES_OUTBOUND, PermissionCodes.SALES_OUTBOUNDS_READ,
            ModuleKeys.SALES_RETURN, PermissionCodes.SALES_RETURNS_READ,
            ModuleKeys.FREIGHT_BILL, PermissionCodes.FREIGHT_BILLS_READ,
            ModuleKeys.CUSTOMER_STATEMENT, PermissionCodes.CUSTOMER_STATEMENTS_READ,
            ModuleKeys.FREIGHT_STATEMENT, PermissionCodes.FREIGHT_STATEMENTS_READ,
            ModuleKeys.RECEIPT, PermissionCodes.RECEIPTS_READ,
            ModuleKeys.PAYMENT, PermissionCodes.PAYMENTS_READ
    );

    private final PermissionChecker permissionChecker;

    PrintRecordAuthorization(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    /**
     * 要求当前主体具备该模块的读取权限；未登记模块视为配置缺失并失败闭合。
     */
    void requireReadable(String moduleKey) {
        String readPermission = READ_PERMISSION_BY_MODULE.get(moduleKey);
        if (readPermission == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "打印模块未登记读取权限: " + moduleKey);
        }
        permissionChecker.require(readPermission);
    }
}
