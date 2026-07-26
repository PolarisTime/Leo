package com.leo.erp.master.api;

import java.util.List;
import java.util.Optional;

public interface SupplierQuery {

    Optional<SupplierSnapshot> findActiveById(Long id);

    Optional<SupplierSnapshot> findActiveByCode(String supplierCode);

    Optional<SupplierSnapshot> findFirstActiveByNameOrderByCode(String supplierName);

    List<SupplierSnapshot> findActiveByNameOrderByCode(String supplierName);

    record SupplierSnapshot(Long id, String code, String name) {
    }
}
