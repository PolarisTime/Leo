package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

import java.util.Set;

public final class BusinessStatusValidator {

    private BusinessStatusValidator() {
    }

    public static String normalizeRequired(String value, String fieldLabel, Set<String> allowedStatuses) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldLabel + "不能为空");
        }
        if (!allowedStatuses.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldLabel + "不合法");
        }
        return normalized;
    }

    public static String normalizeWithDefault(String value,
                                              String defaultValue,
                                              String fieldLabel,
                                              Set<String> allowedStatuses) {
        String normalized = normalizeOptional(value);
        return normalizeRequired(normalized == null ? defaultValue : normalized, fieldLabel, allowedStatuses);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
