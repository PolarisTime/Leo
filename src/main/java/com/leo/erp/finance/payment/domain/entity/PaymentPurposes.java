package com.leo.erp.finance.payment.domain.entity;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;

import java.util.Set;

public final class PaymentPurposes {

    public static final String STATEMENT_SETTLEMENT = "STATEMENT_SETTLEMENT";
    public static final String PURCHASE_PREPAYMENT = "PURCHASE_PREPAYMENT";

    private static final Set<String> ALLOWED_VALUES = Set.of(
            STATEMENT_SETTLEMENT,
            PURCHASE_PREPAYMENT
    );

    private PaymentPurposes() {
    }

    public static String normalize(String value) {
        String normalized = BusinessDocumentValidator.trimToNull(value);
        if (normalized == null) {
            return STATEMENT_SETTLEMENT;
        }
        if (!ALLOWED_VALUES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "付款用途不合法");
        }
        return normalized;
    }

    public static boolean isPurchasePrepayment(String value) {
        return PURCHASE_PREPAYMENT.equals(normalize(value));
    }
}
