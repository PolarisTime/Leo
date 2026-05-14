package com.leo.erp.common.web.dto;

public record MetaErrorCodeResponse(
        String name,
        int code,
        String message
) {
}
