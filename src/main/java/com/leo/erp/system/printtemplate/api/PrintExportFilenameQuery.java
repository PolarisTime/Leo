package com.leo.erp.system.printtemplate.api;

import java.time.LocalDate;

/**
 * 打印导出文件名只读端口。
 *
 * <p>文件名由业务单号、业务日期、项目简称、结算主体拼接并按 UTF-8 字节截断；这套规则属于
 * printtemplate 的导出契约，业务模块只应调用，不应依赖 {@code PrintExportFilenameService} 的内部实现。</p>
 */
public interface PrintExportFilenameQuery {

    /**
     * 构造单据类导出文件名（不含目录）。
     *
     * @param orderNo               业务单号
     * @param businessDate          业务日期；为 {@code null} 时该段退化为 {@code -}
     * @param projectId             项目 id，用于回查项目简称；可为 {@code null}
     * @param projectName           项目名称（回查失败时的兜底）
     * @param settlementCompanyName 结算主体名称
     * @param extension             扩展名（如 {@code xlsx}）
     */
    String forOrder(
            String orderNo,
            LocalDate businessDate,
            Long projectId,
            String projectName,
            String settlementCompanyName,
            String extension
    );
}
