package com.leo.erp.common.moduleexport.service;

import java.util.List;

/**
 * 一个可导出业务模块的静态定义。
 *
 * @param moduleKey       模块键（与前端路由、{@code ModuleKeys} 一致）
 * @param tableName       单据主表（仅允许受信任的小写标识符）
 * @param readPermission  读取该模块所需的权限码，导出前二次校验
 * @param columns         导出列（顺序即列顺序），列名来自 {@code tableName}
 */
public record ModuleExportDefinition(
        String moduleKey,
        String tableName,
        String readPermission,
        List<ModuleExportColumn> columns
) {

    public ModuleExportDefinition {
        columns = List.copyOf(columns);
    }
}
