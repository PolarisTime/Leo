package com.leo.erp.common.api;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.leo.erp.common.json.NumericLongSerializer;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * v2 分页响应。
 *
 * <p>{@code totalElements} 是<b>非 ID 计数</b>，必须输出 JSON number；全局
 * {@code Long -> 字符串} 只服务于雪花 ID 精度保护，不能把分页计数也变成字符串
 * （否则同一个对象里 {@code totalElements} 是字符串、{@code totalPages} 是数字）。
 * 雪花 ID 字段仍由全局规则输出字符串。</p>
 */
public record PageResponse<T>(
        List<T> content,
        @JsonSerialize(using = NumericLongSerializer.class) long totalElements,
        int totalPages,
        int currentPage,
        int pageSize,
        boolean hasMore
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize(),
                page.hasNext()
        );
    }
}
