package com.leo.erp.common.api;

public record ApiFieldError(
        String field,
        String code,
        String message
) {
}
