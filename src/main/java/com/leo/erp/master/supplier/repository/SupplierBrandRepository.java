package com.leo.erp.master.supplier.repository;

import com.leo.erp.master.supplier.domain.entity.SupplierBrand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SupplierBrandRepository extends JpaRepository<SupplierBrand, Long> {

    /** 含软删行: 用于品牌协调时复用既有行(含恢复软删行)。 */
    List<SupplierBrand> findBySupplierIdOrderByBrandNameAsc(Long supplierId);

    List<SupplierBrand> findBySupplierIdAndDeletedFlagFalseOrderByBrandNameAsc(Long supplierId);

    List<SupplierBrand> findBySupplierIdInAndDeletedFlagFalseOrderBySupplierIdAscBrandNameAsc(Collection<Long> supplierIds);
}
