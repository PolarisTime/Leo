package com.leo.erp.contract.sales.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesContractResponse(
        Long id,
        String contractNo,
        Long customerId,
        String customerCode,
        String customerName,
        Long projectId,
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
                                 boolean deletedFlag,
                                 String remark,
                                 List<SalesContractItemResponse> items) {
        this(id, contractNo, null, null, customerName, null, projectName, signDate, effectiveDate, expireDate,
                salesName, totalWeight, totalAmount, status, deletedFlag, remark, items);
    }

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
        this(id, contractNo, null, null, customerName, null, projectName, signDate, effectiveDate, expireDate, salesName,
                totalWeight, totalAmount, status, false, remark, items);
    }
}
