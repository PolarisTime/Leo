package com.leo.erp.contract.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

public final class ContractStatusPolicy {

    private ContractStatusPolicy() {
    }

    public static String preserveForOrdinaryUpdate(String currentStatus, String requestedStatus) {
        String normalizedCurrent = normalize(currentStatus);
        String normalizedRequested = normalize(requestedStatus);
        if (!normalizedRequested.isEmpty() && !normalizedCurrent.equals(normalizedRequested)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "合同状态必须通过专用状态接口变更");
        }
        return normalizedCurrent;
    }

    private static String normalize(String status) {
        return status == null ? "" : status.trim();
    }
}
