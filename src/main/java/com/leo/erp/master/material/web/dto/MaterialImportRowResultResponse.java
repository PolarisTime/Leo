package com.leo.erp.master.material.web.dto;

/**
 * 商品导入单行结果明细：outcome 为 CREATED/UPDATED/SKIPPED/FAILED；
 * 失败行 reason 必填，成功行 reason 为 null，商品字段取导入后的最终值。
 */
public record MaterialImportRowResultResponse(
        int rowNumber,
        String materialCode,
        String brand,
        String material,
        String spec,
        String length,
        String outcome,
        String reason
) {
}
