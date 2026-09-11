package com.leo.erp.common.api;

import org.springframework.data.domain.Pageable;

import java.util.Set;

/**
 * 源单候选查询的分页排序构建：直接复用 {@link PageQuery} 的 page/size/sortBy/direction 校验，
 * 不再维护第二套校验实现；排序字段经格式与白名单校验，非法值返回 422 而非
 * PropertyReferenceException 500，并保留业务排序字段后的同向 id 唯一兜底。
 */
public final class CandidatePageables {

    private static final String DEFAULT_SORT_FIELD = "id";

    private CandidatePageables() {
    }

    public static Pageable of(
            int page,
            int size,
            String sortBy,
            String direction,
            Set<String> allowedSortFields
    ) {
        return PageQuery.of(page, size, sortBy, direction, allowedSortFields)
                .toPageable(DEFAULT_SORT_FIELD);
    }
}
