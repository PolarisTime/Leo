package com.leo.erp.system.printtemplate.api;

/**
 * XLSX 套打布局只读端口。
 *
 * <p>布局来自 {@code print-runtime.json}，按 moduleKey 缓存且不可变；业务模块（销售订单导出）
 * 只需要"拿到本模块的布局模型"，不应依赖 {@code system.printtemplate.service} 的内部实现
 * （{@code PrintXlsxExportLayoutProvider} 的缓存/配置失败语义属于 printtemplate 自身职责）。</p>
 */
public interface PrintLayoutQuery {

    /**
     * 读取指定业务模块的 XLSX 套打布局。
     *
     * @throws com.leo.erp.common.error.BusinessException 配置缺失时（{@code INTERNAL_ERROR}）
     */
    PrintXlsxExportLayout layout(String moduleKey);
}
