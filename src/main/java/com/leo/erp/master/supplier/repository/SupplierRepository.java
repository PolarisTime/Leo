package com.leo.erp.master.supplier.repository;

import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier>,
        RecordExistencePort {

    @Override
    default String moduleKey() {
        return "supplier";
    }

    @Override
    default boolean existsActive(Long recordId) {
        return existsByIdAndDeletedFlagFalse(recordId);
    }

    @Override
    default boolean lockActive(Long recordId) {
        return findActiveForAttachmentBinding(recordId).isPresent();
    }

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select supplier from Supplier supplier where supplier.id = :id and supplier.deletedFlag = false")
    Optional<Supplier> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    boolean existsBySupplierCodeAndDeletedFlagFalse(String supplierCode);

    Optional<Supplier> findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc(String supplierName);

    List<Supplier> findBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc(String supplierName);

    Optional<Supplier> findBySupplierCodeAndDeletedFlagFalse(String supplierCode);

    List<Supplier> findByDeletedFlagFalseOrderBySupplierCodeAsc();

    List<Supplier> findByDeletedFlagFalseAndStatusOrderBySupplierCodeAsc(String status);

    Optional<Supplier> findByIdAndDeletedFlagFalse(Long id);

    long countByDeletedFlagFalse();
}
