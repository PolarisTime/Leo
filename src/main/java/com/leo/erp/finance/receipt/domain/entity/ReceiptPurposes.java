package com.leo.erp.finance.receipt.domain.entity;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;

import java.util.Set;

public final class ReceiptPurposes {

    public static final String CUSTOMER_STATEMENT_SETTLEMENT = "CUSTOMER_STATEMENT_SETTLEMENT";
    public static final String SUPPLIER_PREPAYMENT_REFUND = "SUPPLIER_PREPAYMENT_REFUND";
    public static final String SUPPLIER_OTHER_RECEIPT = "SUPPLIER_OTHER_RECEIPT";

    private static final Set<String> ALLOWED_VALUES = Set.of(
            CUSTOMER_STATEMENT_SETTLEMENT,
            SUPPLIER_PREPAYMENT_REFUND,
            SUPPLIER_OTHER_RECEIPT
    );

    private ReceiptPurposes() {
    }

    public static String normalize(String value) {
        String normalized = BusinessDocumentValidator.trimToNull(value);
        if (normalized == null) {
            return CUSTOMER_STATEMENT_SETTLEMENT;
        }
        if (!ALLOWED_VALUES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "收款用途不合法");
        }
        return normalized;
    }

    public static boolean isSupplierReceipt(String value) {
        String normalized = normalize(value);
        return SUPPLIER_PREPAYMENT_REFUND.equals(normalized)
                || SUPPLIER_OTHER_RECEIPT.equals(normalized);
    }
}
