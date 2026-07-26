package com.leo.erp.master.api;

import java.util.Optional;

public interface CarrierQuery {

    Optional<CarrierSnapshot> findActiveById(Long id);

    Optional<CarrierSnapshot> findActiveByCode(String carrierCode);

    record CarrierSnapshot(
            Long id,
            String code,
            String name,
            Long defaultSettlementCompanyId
    ) {
    }
}
