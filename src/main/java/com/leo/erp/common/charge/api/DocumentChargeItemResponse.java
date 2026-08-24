package com.leo.erp.common.charge.api;

import java.math.BigDecimal;

/**
 * 单据附加费用行响应：id 为雪花 ID 字符串契约由 JacksonConfig 全局 Long→String 承载。
 */
public record DocumentChargeItemResponse(
        Long id,
        Integer lineNo,
        String chargeName,
        Long materialId,
        BigDecimal amount,
        String unit,
        String remark
) {
}
