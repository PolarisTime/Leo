package com.leo.erp.contract.sales.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesContractResponse(
        Long id,
        String contractNo,
        String customerName,
        String projectName,
        LocalDate signDate,
        LocalDate effectiveDate,
        LocalDate expireDate,
        String salesName,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        boolean deletedFlag,
        String remark,
        List<SalesContractItemResponse> items
) {
    public SalesContractResponse(Long id,
                                 String contractNo,
                                 String customerName,
                                 String projectName,
                                 LocalDate signDate,
                                 LocalDate effectiveDate,
                                 LocalDate expireDate,
                                 String salesName,
                                 BigDecimal totalWeight,
                                 BigDecimal totalAmount,
                                 String status,
                                 String remark,
                                 List<SalesContractItemResponse> items) {
        this(id, contractNo, customerName, projectName, signDate, effectiveDate, expireDate, salesName,
                totalWeight, totalAmount, status, false, remark, items);
    }
}
