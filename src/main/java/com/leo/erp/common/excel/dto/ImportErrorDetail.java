package com.leo.erp.common.excel.dto;

public record ImportErrorDetail(
        int row,
        String field,
        String message
) {
}
