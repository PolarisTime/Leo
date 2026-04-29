package com.leo.erp.master.customer.web.dto;

public record CustomerResponse(
        Long id,
        String customerCode,
        String customerName,
        String contactName,
        String contactPhone,
        String city,
        String settlementMode,
        String projectName,
        String projectNameAbbr,
        String projectAddress,
        String status,
        String remark
) {
}
