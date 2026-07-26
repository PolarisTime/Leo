package com.leo.erp.master.api;

import java.util.List;
import java.util.Optional;

public interface ProjectQuery {

    Optional<ProjectSnapshot> findActiveById(Long id);

    List<ProjectSnapshot> findActiveByCustomerCodeAndNameOrderByCode(String customerCode, String projectName);

    record ProjectSnapshot(
            Long id,
            String name,
            String abbreviatedName,
            Long customerId,
            String customerCode
    ) {
    }
}
