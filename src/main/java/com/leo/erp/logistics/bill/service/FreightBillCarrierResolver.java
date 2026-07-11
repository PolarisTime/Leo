package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.repository.CarrierRepository;

import java.util.Objects;

final class FreightBillCarrierResolver {

    private final CarrierRepository carrierRepository;

    FreightBillCarrierResolver(CarrierRepository carrierRepository) {
        this.carrierRepository = Objects.requireNonNull(carrierRepository, "carrierRepository");
    }

    CarrierSnapshot resolve(String requestedCode, String requestedName) {
        String carrierCode = BusinessDocumentValidator.trimToNull(requestedCode);
        if (carrierCode == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "物流商编码不能为空");
        }
        Carrier carrier = carrierRepository.findByCarrierCodeAndDeletedFlagFalse(carrierCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "物流商编码不存在"));
        String carrierName = BusinessDocumentValidator.trimToNull(carrier.getCarrierName());
        if (!Objects.equals(BusinessDocumentValidator.trimToNull(requestedName), carrierName)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流商名称与物流商主数据不一致");
        }
        return new CarrierSnapshot(
                carrier.getCarrierCode(),
                carrierName,
                carrier.getDefaultSettlementCompanyId(),
                BusinessDocumentValidator.trimToNull(carrier.getDefaultSettlementCompanyName())
        );
    }

    record CarrierSnapshot(
            String code,
            String name,
            Long defaultSettlementCompanyId,
            String defaultSettlementCompanyName
    ) {
    }
}
