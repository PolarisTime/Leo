package com.leo.erp.master.customer.web.dto;

public record CustomerOptionResponse(
        Long id,
        String label,
        String value,
        String customerCode,
        String customerName,
        String projectName,
        String projectNameAbbr
) {
}
