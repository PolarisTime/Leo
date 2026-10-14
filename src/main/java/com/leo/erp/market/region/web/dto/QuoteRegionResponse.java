package com.leo.erp.market.region.web.dto;

/**
 * 取价地区响应。{@code id} 为雪花 ID，按全局约定序列化为十进制字符串。
 */
public record QuoteRegionResponse(
        Long id,
        String regionName,
        String slug,
        Integer sortOrder,
        String status,
        String remark
) {
}
