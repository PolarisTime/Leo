package com.leo.erp.system.runtimeconfig.web.dto;

public record RuntimeStatementConfig(
        boolean customerReceiptAmountZero,
        boolean supplierFullPayment
) {
}
