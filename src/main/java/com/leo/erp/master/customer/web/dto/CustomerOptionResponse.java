package com.leo.erp.master.customer.web.dto;

public record CustomerOptionResponse(
        Long id,
        String label,
        String value,
        String customerCode,
        String customerName,
        String projectName,
        String projectNameAbbr,
        Long defaultSettlementCompanyId,
        String defaultSettlementCompanyName
) {
    public CustomerOptionResponse(Long id,
                                  String label,
                                  String value,
                                  String customerCode,
                                  String customerName,
                                  String projectName,
                                  String projectNameAbbr) {
        this(id, label, value, customerCode, customerName, projectName, projectNameAbbr, null, null);
    }
}
