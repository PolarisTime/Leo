package com.leo.erp.common.moduleexport.service;

/**
 * 单个导出列定义。
 *
 * @param field  单据表上的物理列名（仅允许受信任的小写标识符）
 * @param header 导出表头（中文展示名）
 */
public record ModuleExportColumn(String field, String header) {
}
