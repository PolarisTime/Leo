package com.leo.erp.market.lengthpremium.web.dto;

/**
 * 定尺加价响应。{@code id} 为雪花 ID，按全局约定序列化为十进制字符串。
 */
public record LengthPremiumResponse(
        Long id,
        String lengthName,
        Integer premium,
        Integer sortOrder,
        String status,
        String remark
) {
}
