package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

import java.math.BigDecimal;
import java.util.Objects;
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

    public static void requireSameInteger(Integer expected, Integer actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
    }

    public static void requireSameDecimal(BigDecimal expected, BigDecimal actual, String message) {
        if (expected == null || actual == null || expected.compareTo(actual) != 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
    }

    public static void requireSameSourceText(String expected,
                                             String actual,
                                             int lineNo,
                                             String sourceName,
                                             String fieldName) {
        requireSameText(expected, actual, sourceFieldMismatchMessage(lineNo, sourceName, fieldName));
    }

    public static void requireSameSourceInteger(Integer expected,
                                                Integer actual,
                                                int lineNo,
                                                String sourceName,
                                                String fieldName) {
        requireSameInteger(expected, actual, sourceFieldMismatchMessage(lineNo, sourceName, fieldName));
    }

    public static void requireSameSourceDecimal(BigDecimal expected,
                                                BigDecimal actual,
                                                int lineNo,
                                                String sourceName,
                                                String fieldName) {
        requireSameDecimal(expected, actual, sourceFieldMismatchMessage(lineNo, sourceName, fieldName));
    }

    public static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String sourceFieldMismatchMessage(int lineNo, String sourceName, String fieldName) {
        return "第" + lineNo + "行" + sourceName + fieldName + "与请求不一致";
    }
}
