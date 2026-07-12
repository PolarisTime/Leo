package com.leo.erp.master.project.web.dto;

public record ProjectOptionResponse(
        Long id,
        String label,
        Long value,
        Long customerId,
        String customerCode,
        String projectCode,
        String projectName,
        String projectNameAbbr
) {
}
