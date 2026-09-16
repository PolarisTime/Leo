package com.leo.erp.master.api;

import java.util.List;
import java.util.Optional;

public interface SupplierQuery {

    Optional<SupplierSnapshot> findActiveById(Long id);

    Optional<SupplierSnapshot> findActiveByCode(String supplierCode);

    Optional<SupplierSnapshot> findFirstActiveByNameOrderByCode(String supplierName);

    List<SupplierSnapshot> findActiveByNameOrderByCode(String supplierName);

    record SupplierSnapshot(Long id, String code, String name, String shortName) {

        /** 兼容无简称的构造(简称缺省为 null)。 */
        public SupplierSnapshot(Long id, String code, String name) {
            this(id, code, name, null);
        }

        /** 展示名: 优先简称, 否则全称。 */
        public String displayName() {
            return shortName == null || shortName.isBlank() ? name : shortName;
        }
    }
}
