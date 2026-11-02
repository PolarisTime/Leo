package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.permission.PermissionChecker;
import org.springframework.stereotype.Component;

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
 *
 * <p>权限码来自 {@code print-runtime.json} 的 {@code modules.*.readPermission}：与
 * 「可打印模块」声明共用同一份配置，新增可打印模块只需一处登记；未配置时失败闭合
 * （不会因为忘记登记而静默放行）。</p>
 */
@Component
class PrintRecordAuthorization {

    private final PermissionChecker permissionChecker;
    private final PrintRuntimeProperties runtimeProperties;

    PrintRecordAuthorization(
            PermissionChecker permissionChecker,
            PrintRuntimeProperties runtimeProperties
    ) {
        this.permissionChecker = permissionChecker;
        this.runtimeProperties = runtimeProperties;
    }

    /**
     * 要求当前主体具备该模块的读取权限；未登记模块视为配置缺失并失败闭合。
     */
    void requireReadable(String moduleKey) {
        String readPermission = runtimeProperties.readPermission(moduleKey);
        if (readPermission == null || readPermission.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "打印模块未登记读取权限: " + moduleKey);
        }
        permissionChecker.require(readPermission);
    }
}
