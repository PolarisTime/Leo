package com.leo.erp.master.supplier.repository;

import com.leo.erp.master.supplier.domain.entity.SupplierBrand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SupplierBrandRepository extends JpaRepository<SupplierBrand, Long> {

    List<SupplierBrand> findBySupplierIdOrderByBrandNameAsc(Long supplierId);

    List<SupplierBrand> findBySupplierIdInOrderBySupplierIdAscBrandNameAsc(Collection<Long> supplierIds);
}
