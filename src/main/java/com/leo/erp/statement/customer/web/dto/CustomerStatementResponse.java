package com.leo.erp.statement.customer.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CustomerStatementResponse(
        Long id,
        String statementNo,
        String customerCode,
        String customerName,
        Long projectId,
        String projectName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal salesAmount,
        BigDecimal receiptAmount,
        BigDecimal closingAmount,
        String status,
        boolean deletedFlag,
        String remark,
        List<CustomerStatementItemResponse> items,
        Long customerId,
        String direction,
        Long sourceSalesReturnId,
        String sourceSalesReturnNo,
        /** 项目简称(取自 md_project.project_name_abbr); 项目缺失/已删除/简称为空 → null, 由前端回退显示全称。 */
        String projectNameAbbr
) {
    public CustomerStatementResponse(Long id,
                                     String statementNo,
                                     String customerCode,
                                     String customerName,
                                     Long projectId,
                                     String projectName,
                                     Long settlementCompanyId,
                                     String settlementCompanyName,
                                     LocalDate startDate,
                                     LocalDate endDate,
                                     BigDecimal salesAmount,
                                     BigDecimal receiptAmount,
                                     BigDecimal closingAmount,
                                     String status,
                                     boolean deletedFlag,
                                     String remark,
                                     List<CustomerStatementItemResponse> items,
                                     Long customerId) {
        this(id, statementNo, customerCode, customerName, projectId, projectName, settlementCompanyId,
                settlementCompanyName, startDate, endDate, salesAmount, receiptAmount, closingAmount, status,
                deletedFlag, remark, items, customerId, null, null, null, null);
    }

    public CustomerStatementResponse(Long id,
                                     String statementNo,
                                     String customerCode,
                                     String customerName,
                                     Long projectId,
                                     String projectName,
                                     Long settlementCompanyId,
                                     String settlementCompanyName,
                                     LocalDate startDate,
                                     LocalDate endDate,
                                     BigDecimal salesAmount,
                                     BigDecimal receiptAmount,
                                     BigDecimal closingAmount,
                                     String status,
                                     boolean deletedFlag,
                                     String remark,
                                     List<CustomerStatementItemResponse> items) {
        this(id, statementNo, customerCode, customerName, projectId, projectName, settlementCompanyId,
                settlementCompanyName, startDate, endDate, salesAmount, receiptAmount, closingAmount, status,
                deletedFlag, remark, items, null);
    }

    public CustomerStatementResponse(Long id,
                                     String statementNo,
                                     String customerCode,
                                     String customerName,
                                     Long projectId,
                                     String projectName,
                                     Long settlementCompanyId,
                                     String settlementCompanyName,
                                     LocalDate startDate,
                                     LocalDate endDate,
                                     BigDecimal salesAmount,
                                     BigDecimal receiptAmount,
                                     BigDecimal closingAmount,
                                     String status,
                                     String remark,
                                     List<CustomerStatementItemResponse> items) {
        this(id, statementNo, customerCode, customerName, projectId, projectName, settlementCompanyId,
                settlementCompanyName, startDate, endDate, salesAmount, receiptAmount, closingAmount,
                status, false, remark, items, null);
    }

    public CustomerStatementResponse(Long id,
                                     String statementNo,
                                     String customerName,
                                     String projectName,
                                     LocalDate startDate,
                                     LocalDate endDate,
                                     BigDecimal salesAmount,
                                     BigDecimal receiptAmount,
                                     BigDecimal closingAmount,
                                     String status,
                                     String remark,
                                     List<CustomerStatementItemResponse> items) {
        this(id, statementNo, null, customerName, null, projectName, null, null, startDate, endDate,
                salesAmount, receiptAmount, closingAmount, status, false, remark, items, null);
    }

    /**
     * 补「项目简称」: 由查询侧 {@code ProjectNameAbbrResolver} 统一回填, 不参与 MapStruct 映射。
     *
     * <p>刻意声明为 static: 单参实例方法会被 MapStruct 识别为"流式 setter"并要求提供映射目标。</p>
     */
    public static CustomerStatementResponse withProjectNameAbbr(CustomerStatementResponse source,
                                                                String projectNameAbbr) {
        return new CustomerStatementResponse(
                source.id(), source.statementNo(), source.customerCode(), source.customerName(),
                source.projectId(), source.projectName(), source.settlementCompanyId(),
                source.settlementCompanyName(), source.startDate(), source.endDate(), source.salesAmount(),
                source.receiptAmount(), source.closingAmount(), source.status(), source.deletedFlag(),
                source.remark(), source.items(), source.customerId(), source.direction(),
                source.sourceSalesReturnId(), source.sourceSalesReturnNo(), projectNameAbbr);
    }
}
