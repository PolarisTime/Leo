package com.leo.erp.statement.freight.service;

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
        if (explicitCode != null || carrierRepository == null) {
            return explicitCode;
        }
        String normalizedCarrierName = trimToNull(carrierName);
        if (normalizedCarrierName == null) {
            return null;
        }
        return carrierRepository.findFirstByCarrierNameAndDeletedFlagFalseOrderByCarrierCodeAsc(normalizedCarrierName)
                .map(com.leo.erp.master.carrier.domain.entity.Carrier::getCarrierCode)
                .orElse(null);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
