package com.leo.erp.common.api;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public record PageQuery(
        @Min(0) int page,
        @Min(1) @Max(200) int size,
        String sortBy,
        String direction
) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

    public Pageable toPageable(String defaultSortBy) {
        String property = (sortBy == null || sortBy.isBlank()) ? defaultSortBy : sortBy;
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(sortDirection, property));
    }

    public static PageQuery of(Integer page, Integer size, String sortBy, String direction) {
        return of(page, size, sortBy, direction, null);
    }

    public static PageQuery of(Integer page, Integer size, String sortBy, String direction, Set<String> allowedSortFields) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        if (resolvedPage < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "page 不能小于0");
        }
        if (resolvedSize < 1 || resolvedSize > MAX_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "size 必须在1到200之间");
        }
        String resolvedSortBy = normalizeSortBy(sortBy, allowedSortFields);
        String resolvedDirection = normalizeDirection(direction);
        return new PageQuery(resolvedPage, resolvedSize, resolvedSortBy, resolvedDirection);
    }

    private static String normalizeSortBy(String sortBy, Set<String> allowedSortFields) {
        if (sortBy == null || sortBy.isBlank()) {
            return null;
        }
        String normalized = sortBy.trim();
        if (!normalized.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sortBy 格式不合法");
        }
        if (allowedSortFields != null && !allowedSortFields.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sortBy 不支持当前列表");
        }
        return normalized;
    }

    private static String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return null;
        }
        String normalized = direction.trim().toLowerCase();
        if (!"asc".equals(normalized) && !"desc".equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "direction 只能为 asc 或 desc");
        }
        return normalized;
    }
}
