package com.leo.erp.master.api;

import java.util.Optional;

public interface CustomerQuery {

    Optional<CustomerSnapshot> findActiveById(Long id);

    Optional<CustomerSnapshot> findActiveByCode(String customerCode);

    Optional<CustomerSnapshot> findFirstActiveByNameAndProjectNameOrderByCode(
            String customerName,
            String projectName
    );

    record CustomerSnapshot(
            Long id,
            String code,
            String name,
            String projectName,
            Long defaultSettlementCompanyId,
            String defaultSettlementCompanyName
    ) {
    }
}
