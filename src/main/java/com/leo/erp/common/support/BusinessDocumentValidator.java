package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

import java.util.Set;

public final class BusinessDocumentValidator {

    private BusinessDocumentValidator() {
    }

    public static void requireStatusIn(String status, Set<String> allowedStatuses, String message) {
        if (!allowedStatuses.contains(normalizeText(status))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
    }

    public static void requireSameText(String expected, String actual, String message) {
        if (!normalizeText(expected).equals(normalizeText(actual))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
    }

    public static void requireSameOptionalCode(String expected, String actual, String message) {
        String normalizedExpected = trimToNull(expected);
        String normalizedActual = trimToNull(actual);
        if (normalizedExpected == null || normalizedActual == null) {
            return;
        }
        if (!normalizedExpected.equals(normalizedActual)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
    }

    public static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
