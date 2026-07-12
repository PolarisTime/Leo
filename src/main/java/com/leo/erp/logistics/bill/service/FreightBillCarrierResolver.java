package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

final class FreightBillCarrierResolver {

    private static final Logger log = LoggerFactory.getLogger(FreightBillCarrierResolver.class);

    private final CarrierRepository carrierRepository;

    FreightBillCarrierResolver(CarrierRepository carrierRepository) {
        this.carrierRepository = Objects.requireNonNull(carrierRepository, "carrierRepository");
    }

    CarrierSnapshot resolve(String requestedCode, String requestedName) {
        return resolve(null, requestedCode, requestedName);
    }

    CarrierSnapshot resolve(Long requestedId, String requestedCode, String requestedName) {
        String carrierCode = BusinessDocumentValidator.trimToNull(requestedCode);
        if (requestedId == null && carrierCode == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "物流商编码不能为空");
        }
        Carrier carrier;
        if (requestedId != null) {
            carrier = carrierRepository.findByIdAndDeletedFlagFalse(requestedId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "物流商不存在"));
        } else {
            carrier = carrierRepository.findByCarrierCodeAndDeletedFlagFalse(carrierCode)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "物流商编码不存在"));
            log.warn("identity_fallback module=freight-bill field=carrierId reason=carrier-code resolvedId={}",
                    carrier.getId());
        }
        String resolvedCode = BusinessDocumentValidator.trimToNull(carrier.getCarrierCode());
        if (carrierCode != null && !Objects.equals(carrierCode, resolvedCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流商ID与物流商编码不一致");
        }
        String carrierName = BusinessDocumentValidator.trimToNull(carrier.getCarrierName());
        if (!Objects.equals(BusinessDocumentValidator.trimToNull(requestedName), carrierName)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流商名称与物流商主数据不一致");
        }
        return new CarrierSnapshot(
                carrier.getId(),
                resolvedCode,
                carrierName,
                carrier.getDefaultSettlementCompanyId(),
                BusinessDocumentValidator.trimToNull(carrier.getDefaultSettlementCompanyName())
        );
    }

    record CarrierSnapshot(
            Long id,
            String code,
            String name,
            Long defaultSettlementCompanyId,
            String defaultSettlementCompanyName
    ) {
    }
}
