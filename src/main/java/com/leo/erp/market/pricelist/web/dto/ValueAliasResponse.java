package com.leo.erp.market.pricelist.web.dto;

import java.time.LocalDateTime;

/** 值映射响应。雪花 ID 由 {@code JacksonConfig} 统一序列化为十进制字符串。 */
public record ValueAliasResponse(
        Long id,
        String dimension,
        String sourceValue,
        String targetValue,
        String remark,
        String createdName,
        LocalDateTime createdAt,
        String updatedName,
        LocalDateTime updatedAt
) {
}
