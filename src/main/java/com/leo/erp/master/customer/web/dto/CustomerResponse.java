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
        Long defaultSettlementCompanyId,
        String defaultSettlementCompanyName,
        String status,
        String remark
) {
    public CustomerResponse(Long id,
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
                            String remark) {
        this(
                id,
                customerCode,
                customerName,
                contactName,
                contactPhone,
                city,
                settlementMode,
                projectName,
                projectNameAbbr,
                projectAddress,
                null,
                null,
                status,
                remark
        );
    }
}
