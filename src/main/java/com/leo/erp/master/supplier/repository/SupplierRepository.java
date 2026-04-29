package com.leo.erp.master.supplier.repository;

import com.leo.erp.master.supplier.domain.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {

    boolean existsBySupplierCodeAndDeletedFlagFalse(String supplierCode);

    Optional<Supplier> findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc(String supplierName);

    List<Supplier> findByDeletedFlagFalseOrderBySupplierCodeAsc();

    Optional<Supplier> findByIdAndDeletedFlagFalse(Long id);

    long countByDeletedFlagFalse();
}
