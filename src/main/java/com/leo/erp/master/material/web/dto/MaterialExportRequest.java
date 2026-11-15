package com.leo.erp.master.material.web.dto;

import jakarta.validation.constraints.Size;

/**
 * 商品资料导出请求体。
 *
 * @param keyword 商品资料筛选关键字（编码/品牌/名称/规格等，语义与列表筛选一致），可省略
 * @param format  导出格式：{@code xlsx}（默认）或 {@code csv}；其它值返回 422
 */
public record MaterialExportRequest(
        @Size(max = 200, message = "关键字长度不能超过 200")
        String keyword,

        @Size(max = 32, message = "导出格式长度不能超过 32")
        String format
) {
}
