package com.leo.erp.finance.payment.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PaymentResponse(
        Long id,
        String paymentNo,
        String counterpartyType,
        Long counterpartyId,
        String paymentPurpose,
        String counterpartyCode,
        String counterpartyName,
        Long sourceStatementId,
        Long sourcePurchaseOrderId,
        String purchaseOrderNo,
        String supplierCode,
        String supplierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        Long accountId,
        // 资金账户快照：读取时按结算主体实时解析，账户不存在或已禁用时为 null，不影响既有字段。
        String accountName,
        String accountNo,
        LocalDate paymentDate,
        String payType,
        BigDecimal amount,
        String status,
        boolean deletedFlag,
        String operatorName,
        String remark,
        List<PaymentAllocationResponse> items,
        /**
         * 资源版本（实体 {@code @Version}，乐观并发前置条件）。
         *
         * <p>列表与详情都返回：客户端保存时必须把这一版原样回送到
         * {@code X-Resource-Version}，服务端据此判定「我编辑的是哪一版」（不一致 412）。
         * 全局 {@code Long} → 字符串序列化即输出 {@code "3"} 这样的十进制字符串。</p>
         */
        Long version
) {
    public PaymentResponse(Long id,
                           String paymentNo,
                           String counterpartyType,
                           Long counterpartyId,
                           String paymentPurpose,
                           String counterpartyCode,
                           String counterpartyName,
                           Long sourceStatementId,
                           Long sourcePurchaseOrderId,
                           String purchaseOrderNo,
                           String supplierCode,
                           String supplierName,
                           Long settlementCompanyId,
                           String settlementCompanyName,
                           LocalDate paymentDate,
                           String payType,
                           BigDecimal amount,
                           String status,
                           boolean deletedFlag,
                           String operatorName,
                           String remark,
                           List<PaymentAllocationResponse> items) {
        this(id, paymentNo, counterpartyType, counterpartyId, paymentPurpose, counterpartyCode,
                counterpartyName, sourceStatementId, sourcePurchaseOrderId, purchaseOrderNo,
                supplierCode, supplierName, settlementCompanyId, settlementCompanyName, null, null, null,
                paymentDate, payType, amount, status, deletedFlag, operatorName, remark, items, null);
    }

    public PaymentResponse(Long id,
                           String paymentNo,
                           String businessType,
                           String paymentPurpose,
                           String counterpartyCode,
                           String counterpartyName,
                           Long sourceStatementId,
                           Long sourcePurchaseOrderId,
                           String purchaseOrderNo,
                           String supplierCode,
                           String supplierName,
                           Long settlementCompanyId,
                           String settlementCompanyName,
                           LocalDate paymentDate,
                           String payType,
                           BigDecimal amount,
                           String status,
                           boolean deletedFlag,
                           String operatorName,
                           String remark,
                           List<PaymentAllocationResponse> items) {
        this(id, paymentNo, businessType, null, paymentPurpose, counterpartyCode, counterpartyName,
                sourceStatementId, sourcePurchaseOrderId, purchaseOrderNo, supplierCode, supplierName,
                settlementCompanyId, settlementCompanyName, paymentDate, payType, amount, status,
                deletedFlag, operatorName, remark, items);
    }

    public PaymentResponse(Long id,
                           String paymentNo,
                           String businessType,
                           String counterpartyCode,
                           String counterpartyName,
                           Long sourceStatementId,
                           LocalDate paymentDate,
                           String payType,
                           BigDecimal amount,
                           String status,
                           boolean deletedFlag,
                           String operatorName,
                           String remark,
                           List<PaymentAllocationResponse> items) {
        this(
                id,
                paymentNo,
                businessType,
                null,
                null,
                counterpartyCode,
                counterpartyName,
                sourceStatementId,
                null,
                null,
                null,
                null,
                null,
                null,
                paymentDate,
                payType,
                amount,
                status,
                deletedFlag,
                operatorName,
                remark,
                items
        );
    }

    public PaymentResponse(Long id,
                           String paymentNo,
                           String businessType,
                           String counterpartyCode,
                           String counterpartyName,
                           Long sourceStatementId,
                           LocalDate paymentDate,
                           String payType,
                           BigDecimal amount,
                           String status,
                           String operatorName,
                           String remark,
                           List<PaymentAllocationResponse> items) {
        this(
                id,
                paymentNo,
                businessType,
                null,
                null,
                counterpartyCode,
                counterpartyName,
                sourceStatementId,
                null,
                null,
                null,
                null,
                null,
                null,
                paymentDate,
                payType,
                amount,
                status,
                false,
                operatorName,
                remark,
                items
        );
    }

    public PaymentResponse(Long id,
                           String paymentNo,
                           String businessType,
                           String counterpartyName,
                           Long sourceStatementId,
                           LocalDate paymentDate,
                           String payType,
                           BigDecimal amount,
                           String status,
                           String operatorName,
                           String remark,
                           List<PaymentAllocationResponse> items) {
        this(
                id,
                paymentNo,
                businessType,
                null,
                null,
                null,
                counterpartyName,
                sourceStatementId,
                null,
                null,
                null,
                null,
                null,
                null,
                paymentDate,
                payType,
                amount,
                status,
                false,
                operatorName,
                remark,
                items
        );
    }

    /**
     * 复制当前响应并补齐资金账户快照（账户名称、账号）。
     * 列表装配由服务层批量解析后一次性回填，避免逐行查库（N+1）。
     */
    public PaymentResponse withAccount(String accountName, String accountNo) {
        return new PaymentResponse(id, paymentNo, counterpartyType, counterpartyId, paymentPurpose,
                counterpartyCode, counterpartyName, sourceStatementId, sourcePurchaseOrderId,
                purchaseOrderNo, supplierCode, supplierName, settlementCompanyId, settlementCompanyName,
                accountId, accountName, accountNo, paymentDate, payType, amount, status, deletedFlag,
                operatorName, remark, items, version);
    }
}
