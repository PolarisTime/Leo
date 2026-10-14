package com.leo.erp.master.valuealias.web.dto;

import java.time.LocalDateTime;

/**
 * 值映射响应。
 *
 * <p>{@code id} 为雪花 ID，经全局 {@code ToStringSerializer} 约定序列化为十进制字符串，
 * 避免前端 {@code Number} 丢精度。</p>
 */
public record ValueAliasResponse(
        Long id,
        String dimension,
        String sourceValue,
        String targetValue,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
