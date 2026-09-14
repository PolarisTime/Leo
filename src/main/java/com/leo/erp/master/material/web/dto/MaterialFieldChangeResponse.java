package com.leo.erp.master.material.web.dto;

/**
 * 商品导入预览的字段差异：before 为 null 表示新增，after 为 null 表示清空。
 */
public record MaterialFieldChangeResponse(
        String field,
        String label,
        String before,
        String after
) {
}
