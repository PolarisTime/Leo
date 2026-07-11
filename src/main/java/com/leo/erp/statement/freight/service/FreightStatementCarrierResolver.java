package com.leo.erp.statement.freight.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import org.springframework.stereotype.Service;

@Service
public class FreightStatementCarrierResolver {

    private final CarrierRepository carrierRepository;

    public FreightStatementCarrierResolver(CarrierRepository carrierRepository) {
        this.carrierRepository = carrierRepository;
    }

    String resolveCarrierCode(String requestCarrierCode, String carrierName) {
        String explicitCode = trimToNull(requestCarrierCode);
        if (carrierRepository == null) {
            return explicitCode;
        }
        if (explicitCode == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "物流商编码不能为空");
        }
        return carrierRepository.findByCarrierCodeAndDeletedFlagFalse(explicitCode)
                .map(com.leo.erp.master.carrier.domain.entity.Carrier::getCarrierCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "物流商编码不存在"));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
