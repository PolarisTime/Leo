package com.leo.erp.finance.supplierrefundreceipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.finance.supplierrefundreceipt.repository.SupplierRefundReceiptRepository;
import org.springframework.stereotype.Component;

@Component
public class SupplierRefundReceiptGuard {

    private final SupplierRefundReceiptRepository repository;

    public SupplierRefundReceiptGuard(SupplierRefundReceiptRepository repository) {
        this.repository = repository;
    }

    public void assertNoActiveReceipt(Long purchaseRefundId, String operationName) {
        if (purchaseRefundId == null) {
            return;
        }
        if (repository.existsByPurchaseRefundIdAndDeletedFlagFalse(purchaseRefundId)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购退款单已存在供应商退款到账单，不能" + operationName
            );
        }
    }
}
