package com.leo.erp.statement.freight.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.api.CarrierQuery;
import org.springframework.stereotype.Service;

@Service
public class FreightStatementCarrierResolver {

    private final CarrierQuery carrierQuery;

    public FreightStatementCarrierResolver(CarrierQuery carrierQuery) {
        this.carrierQuery = carrierQuery;
    }

    String resolveCarrierCode(String requestCarrierCode, String carrierName) {
        String explicitCode = trimToNull(requestCarrierCode);
        if (carrierQuery == null) {
            return explicitCode;
        }
        if (explicitCode == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "物流商编码不能为空");
        }
        return carrierQuery.findActiveByCode(explicitCode)
                .map(CarrierQuery.CarrierSnapshot::code)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "物流商编码不存在"));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
