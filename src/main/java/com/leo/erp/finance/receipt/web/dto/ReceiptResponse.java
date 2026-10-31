package com.leo.erp.finance.receipt.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReceiptResponse(
        Long id,
        String receiptNo,
        String counterpartyType,
        Long counterpartyId,
        String counterpartyCode,
        String counterpartyName,
        String receiptPurpose,
        Long customerId,
        String customerCode,
        String customerName,
        Long projectId,
        String projectName,
        Long settlementCompanyId,
        String settlementCompanyName,
        Long accountId,
        // 资金账户快照：读取时按结算主体实时解析，账户不存在或已禁用时为 null，不影响既有字段。
        String accountName,
        String accountNo,
        Long sourceCustomerStatementId,
        LocalDate receiptDate,
        String payType,
        BigDecimal amount,
        String status,
        boolean deletedFlag,
        String operatorName,
        String remark,
        List<ReceiptAllocationResponse> items,
        /**
         * 资源版本（实体 {@code @Version}，乐观并发前置条件）。
         *
         * <p>列表与详情都返回：客户端保存时必须把这一版原样回送到
         * {@code X-Resource-Version}，服务端据此判定「我编辑的是哪一版」（不一致 412）。
         * 全局 {@code Long} → 字符串序列化即输出 {@code "3"} 这样的十进制字符串。</p>
         */
        Long version
) {
    public ReceiptResponse(Long id,
                           String receiptNo,
                           String counterpartyType,
                           Long counterpartyId,
                           String counterpartyCode,
                           String counterpartyName,
                           String receiptPurpose,
                           Long customerId,
                           String customerCode,
                           String customerName,
                           Long projectId,
                           String projectName,
                           Long settlementCompanyId,
                           String settlementCompanyName,
                           Long sourceCustomerStatementId,
                           LocalDate receiptDate,
                           String payType,
                           BigDecimal amount,
                           String status,
                           boolean deletedFlag,
                           String operatorName,
                           String remark,
                           List<ReceiptAllocationResponse> items) {
        this(id, receiptNo, counterpartyType, counterpartyId, counterpartyCode, counterpartyName,
                receiptPurpose, customerId, customerCode, customerName, projectId, projectName,
                settlementCompanyId, settlementCompanyName, null, null, null, sourceCustomerStatementId,
                receiptDate, payType, amount, status, deletedFlag, operatorName, remark, items, null);
    }

    public ReceiptResponse(Long id,
                           String receiptNo,
                           Long customerId,
                           String customerCode,
                           String customerName,
                           Long projectId,
                           String projectName,
                           Long settlementCompanyId,
                           String settlementCompanyName,
                           Long sourceCustomerStatementId,
                           LocalDate receiptDate,
                           String payType,
                           BigDecimal amount,
                           String status,
                           boolean deletedFlag,
                           String operatorName,
                           String remark,
                           List<ReceiptAllocationResponse> items) {
        this(id, receiptNo, "客户", customerId, customerCode, customerName,
                "CUSTOMER_STATEMENT_SETTLEMENT", customerId, customerCode, customerName,
                projectId, projectName, settlementCompanyId, settlementCompanyName,
                sourceCustomerStatementId, receiptDate, payType, amount, status, deletedFlag,
                operatorName, remark, items);
    }

    public ReceiptResponse(Long id,
                           String receiptNo,
                           String customerCode,
                           String customerName,
                           Long projectId,
                           String projectName,
                           Long settlementCompanyId,
                           String settlementCompanyName,
                           Long sourceCustomerStatementId,
                           LocalDate receiptDate,
                           String payType,
                           BigDecimal amount,
                           String status,
                           boolean deletedFlag,
                           String operatorName,
                           String remark,
                           List<ReceiptAllocationResponse> items) {
        this(id, receiptNo, null, customerCode, customerName, projectId, projectName, settlementCompanyId,
                settlementCompanyName, sourceCustomerStatementId, receiptDate, payType, amount, status,
                deletedFlag, operatorName, remark, items);
    }

    public ReceiptResponse(Long id,
                           String receiptNo,
                           String customerCode,
                           String customerName,
                           Long projectId,
                           String projectName,
                           Long settlementCompanyId,
                           String settlementCompanyName,
                           Long sourceStatementId,
                           LocalDate receiptDate,
                           String payType,
                           BigDecimal amount,
                           String status,
                           String operatorName,
                           String remark,
                           List<ReceiptAllocationResponse> items) {
        this(id, receiptNo, null, customerCode, customerName, projectId, projectName, settlementCompanyId,
                settlementCompanyName, sourceStatementId, receiptDate, payType, amount, status, false,
                operatorName, remark, items);
    }

    public ReceiptResponse(Long id,
                           String receiptNo,
                           String customerName,
                           String projectName,
                           Long sourceStatementId,
                           LocalDate receiptDate,
                           String payType,
                           BigDecimal amount,
                           String status,
                           String operatorName,
                           String remark,
                           List<ReceiptAllocationResponse> items) {
        this(id, receiptNo, null, null, customerName, null, projectName, null, null, sourceStatementId,
                receiptDate, payType, amount, status, false, operatorName, remark, items);
    }

    public ReceiptResponse(Long id,
                           String receiptNo,
                           String customerCode,
                           String customerName,
                           Long projectId,
                           String projectName,
                           Long sourceStatementId,
                           LocalDate receiptDate,
                           String payType,
                           BigDecimal amount,
                           String status,
                           String operatorName,
                           String remark,
                           List<ReceiptAllocationResponse> items) {
        this(id, receiptNo, null, customerCode, customerName, projectId, projectName, null, null,
                sourceStatementId, receiptDate, payType, amount, status, false, operatorName, remark, items);
    }

    /**
     * 复制当前响应并补齐资金账户快照（账户名称、账号）。
     * 列表装配由服务层批量解析后一次性回填，避免逐行查库（N+1）。
     */
    public ReceiptResponse withAccount(String accountName, String accountNo) {
        return new ReceiptResponse(id, receiptNo, counterpartyType, counterpartyId, counterpartyCode,
                counterpartyName, receiptPurpose, customerId, customerCode, customerName, projectId,
                projectName, settlementCompanyId, settlementCompanyName, accountId, accountName, accountNo,
                sourceCustomerStatementId, receiptDate, payType, amount, status, deletedFlag,
                operatorName, remark, items, version);
    }
}
