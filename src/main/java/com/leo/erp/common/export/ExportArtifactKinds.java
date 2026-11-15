package com.leo.erp.common.export;

/**
 * 导出产物种类标识。
 *
 * <p>不同资源集合的导出产物共用一个进程内存储，靠 {@code kind} 隔离：
 * 读取时必须同时校验 {@code kind} 与 id，避免把 A 集合的产物 id 拿到 B 集合的
 * content 端点上来读取（雪花 id 虽然全局唯一，但契约上仍应显式隔离）。</p>
 */
public final class ExportArtifactKinds {

    public static final String MODULE_EXPORT = "module-export";
    public static final String MATERIAL_EXPORT = "material-export";
    public static final String CASH_LEDGER_EXPORT = "cash-ledger-export";
    public static final String ATTACHMENT_MANIFEST_EXPORT = "attachment-manifest-export";
    public static final String SALES_ORDER_XLSX_EXPORT = "sales-order-xlsx-export";

    private ExportArtifactKinds() {
    }
}
