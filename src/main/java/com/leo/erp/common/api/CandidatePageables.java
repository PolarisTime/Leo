package com.leo.erp.common.api;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 源单候选查询的分页排序构建：与普通列表接口的 PageQuery 保持同等校验强度，
 * 排序字段经格式与白名单校验，非法值返回 400 而非 PropertyReferenceException 500。
 */
public final class CandidatePageables {

    private static final Pattern SAFE_SORT_FIELD = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private CandidatePageables() {
    }

    public static Pageable of(
            int page,
            int size,
            String sortBy,
            String direction,
            Set<String> allowedSortFields
    ) {
        String property = sortBy == null || sortBy.isBlank() ? "id" : sortBy.trim();
        if (!SAFE_SORT_FIELD.matcher(property).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sortBy 格式不合法");
        }
        if (allowedSortFields != null && !allowedSortFields.contains(property)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sortBy 不支持当前列表");
        }
        Sort.Direction dir = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(dir, property));
    }
}
